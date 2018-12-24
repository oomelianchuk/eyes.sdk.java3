package com.applitools.eyes.visualGridClient.model;

import com.applitools.ICheckRGSettings;
import com.applitools.ICheckRGSettingsInternal;
import com.applitools.eyes.Logger;
import com.applitools.eyes.visualGridClient.services.IEyesConnector;
import com.applitools.eyes.visualGridClient.services.IResourceFuture;
import com.applitools.eyes.visualGridClient.services.RenderingGridManager;
import com.applitools.eyes.visualGridClient.services.Task;
import com.applitools.utils.GeneralUtils;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.css.ECSSVersion;
import com.helger.css.decl.*;
import com.helger.css.reader.CSSReader;
import org.apache.commons.codec.binary.Base64;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RenderingTask implements Callable<RenderStatusResults>, CompletableTask {

    private static final int MAX_FETCH_FAILS = 62;
    private static final int MAX_ITERATIONS = 30;

    private final List<RenderTaskListener> listeners = new ArrayList<>();
    private IEyesConnector eyesConnector;
    private String scriptResult;
    private ICheckRGSettings renderingConfiguration;
    private List<Task> taskList;
    private List<Task> openTaskList;
    private RenderingInfo renderingInfo;
    private final Map<String, IResourceFuture> fetchedCacheMap;
    private final Map<String, PutFuture> putResourceCache;
    private Logger logger;
    private AtomicBoolean isTaskComplete = new AtomicBoolean(false);
    private AtomicBoolean isForcePutNeeded;
    private IDebugResourceWriter debugResourceWriter;
    private HashMap<String, Object> result = null;
    private AtomicInteger framesLevel = new AtomicInteger();

    public interface RenderTaskListener {
        void onRenderSuccess();

        void onRenderFailed(Exception e);
    }

    public RenderingTask(IEyesConnector eyesConnector, String scriptResult, ICheckRGSettings renderingConfiguration,
                         List<Task> taskList, List<Task> openTasks, RenderingGridManager renderingGridManager,
                         IDebugResourceWriter debugResourceWriter, RenderTaskListener listener) {

        this.eyesConnector = eyesConnector;
        this.scriptResult = scriptResult;
        this.renderingConfiguration = renderingConfiguration;
        this.taskList = taskList;
        this.openTaskList = openTasks;
        this.renderingInfo = renderingGridManager.getRenderingInfo();
        this.fetchedCacheMap = renderingGridManager.getCachedResources();
        this.putResourceCache = renderingGridManager.getPutResourceCache();
        this.logger = renderingGridManager.getLogger();
        this.debugResourceWriter = debugResourceWriter;
        this.listeners.add(listener);

        String renderingGridForcePut = System.getenv("APPLITOOLS_RENDERING_GRID_FORCE_PUT");
        this.isForcePutNeeded = new AtomicBoolean(renderingGridForcePut != null && renderingGridForcePut.equalsIgnoreCase("true"));
    }

    @Override
    public RenderStatusResults call() throws Exception {

        logger.verbose("enter");

        boolean isSecondRequestAlreadyHappened = false;

        logger.verbose("step 1");
        //Parse to Map
        result = GeneralUtils.parseJsonToObject(scriptResult);
        logger.verbose("step 2");
        //Build RenderRequests
        RenderRequest[] requests = prepareDataForRG(result);

        logger.verbose("step 3");
        boolean stillRunning = true;
        int fetchFails = 0;
        List<RunningRender> runningRenders = null;
        do {

            try {

                runningRenders = this.eyesConnector.render(requests);

            } catch (Exception e) {

                Thread.sleep(1500);
                logger.verbose("/render throws exception... sleeping for 1.5s");
                GeneralUtils.logExceptionStackTrace(logger, e);
                if (e.getMessage().contains("Second request, yet still some resources were not PUT in renderId")) {
                    if (isSecondRequestAlreadyHappened) {
                        logger.verbose("Second request already happened");
                    }
                    isSecondRequestAlreadyHappened = true;
                }
                logger.verbose("ERROR " + e.getMessage());
                fetchFails++;
            }
            logger.verbose("step 4.1");
            if (runningRenders == null) {
                logger.verbose("ERROR - runningRenders is null.");
                continue;
            }

            for (int i = 0; i < requests.length; i++) {
                RenderRequest request = requests[i];
                request.setRenderId(runningRenders.get(i).getRenderId());
            }
            logger.verbose("step 4.2");

            RunningRender runningRender = runningRenders.get(0);
            RenderStatus worstStatus = runningRender.getRenderStatus();

            worstStatus = calcWorstStatus(runningRenders, worstStatus);

            boolean isNeedMoreDom = runningRender.isNeedMoreDom();

            if (isForcePutNeeded.get()) {
                forcePutAllResources(requests[0].getResources(), runningRender);
            }

            logger.verbose("step 4.3");
            stillRunning = worstStatus == RenderStatus.NEED_MORE_RESOURCE || isNeedMoreDom || fetchFails > MAX_FETCH_FAILS;
            if (stillRunning) {
                sendMissingResources(runningRenders, requests[0].getDom(), isNeedMoreDom);
            }

            logger.verbose("step 4.4");

        } while (stillRunning);

        Map<RunningRender, RenderRequest> mapping = mapRequestToRunningRender(runningRenders, requests);

        logger.verbose("step 5");
        pollRenderingStatus(mapping);

        logger.verbose("exit");

        return null;
    }

    private void forcePutAllResources(Map<String, RGridResource> resources, RunningRender runningRender) {
        for (String url : resources.keySet()) {
            if (putResourceCache.containsKey(url)) continue;

            try {
                logger.verbose("trying to get url from map - " + url);
                IResourceFuture resourceFuture = fetchedCacheMap.get(url);
                if (resourceFuture == null) {
                    logger.verbose("fetchedCacheMap.get(url) == null - " + url);
                } else {
                    RGridResource resource = resourceFuture.get();
                    PutFuture future = this.eyesConnector.renderPutResource(runningRender, resource);
                    if (!putResourceCache.containsKey(url)) {
                        putResourceCache.put(url, future);
                    }
                }
            } catch (Exception e) {
                GeneralUtils.logExceptionStackTrace(logger, e);
            }
        }
    }

    private void notifyFailureAllListeners(Exception e) {
        for (RenderTaskListener listener : listeners) {
            listener.onRenderFailed(e);
        }
    }

    private void notifySuccessAllListeners() {
        for (RenderTaskListener listener : listeners) {
            listener.onRenderSuccess();
        }
    }

    private Map<RunningRender, RenderRequest> mapRequestToRunningRender(List<RunningRender> runningRenders, RenderRequest[] requests) {
        Map<RunningRender, RenderRequest> mapping = new HashMap<>();
        for (int i = 0; i < requests.length; i++) {
            RenderRequest request = requests[i];
            RunningRender runningRender = runningRenders.get(i);
            mapping.put(runningRender, request);
        }
        return mapping;
    }

    private RenderStatus calcWorstStatus(List<RunningRender> runningRenders, RenderStatus worstStatus) {
        LOOP:
        for (RunningRender runningRender : runningRenders) {
            switch (runningRender.getRenderStatus()) {
                case NEED_MORE_RESOURCE:
                    if (worstStatus == RenderStatus.RENDERED || worstStatus == RenderStatus.RENDERING) {
                        worstStatus = RenderStatus.NEED_MORE_RESOURCE;
                    }
                    break;
                case ERROR:
                    worstStatus = RenderStatus.ERROR;
                    break LOOP;
            }
        }
        return worstStatus;
    }

    private List<String> getRenderIds(Collection<RunningRender> runningRenders) {
        List<String> ids = new ArrayList<>();
        for (RunningRender runningRender : runningRenders) {
            ids.add(runningRender.getRenderId());
        }
        return ids;
    }

    private void sendMissingResources(List<RunningRender> runningRenders, RGridDom dom, boolean isNeedMoreDom) throws Exception {
        logger.verbose("enter");
        List<PutFuture> allPuts = new ArrayList<>();
        if (isNeedMoreDom) {
            RunningRender runningRender = runningRenders.get(0);
            PutFuture future = this.eyesConnector.renderPutResource(runningRender, dom.asResource());
            logger.verbose("locking putResourceCache");
            synchronized (putResourceCache) {
                putResourceCache.put(dom.getUrl(), future);
                allPuts.add(future);
            }
            logger.verbose("releasing putResourceCache");
        }

        logger.verbose("creating PutFutures for " + runningRenders.size() + " runningRenders");

        for (RunningRender runningRender : runningRenders) {
            createPutFutures(allPuts, runningRender);
        }

        logger.verbose("calling future.get on " + allPuts.size() + " PutFutures");
        for (PutFuture future : allPuts) {
            logger.verbose("calling future.get on " + future.toString());
            future.get();
        }
        logger.verbose("exit");
    }

    private void createPutFutures(List<PutFuture> allPuts, RunningRender runningRender) throws Exception {
        List<String> needMoreResources = runningRender.getNeedMoreResources();
        for (String url : needMoreResources) {
            if (putResourceCache.containsKey(url) && !isForcePutNeeded.get()) {
                PutFuture putFuture = putResourceCache.get(url);
                if (!allPuts.contains(putFuture)) {
                    allPuts.add(putFuture);
                }
                continue;
            }

            try {
//                    logger.verbose("trying to get url from map - " + url);
                IResourceFuture resourceFuture = fetchedCacheMap.get(url);
                if (resourceFuture == null) {
                    logger.verbose("fetchedCacheMap.get(url) == null - " + url);
                    throw new Exception("Resource put requested but never downloaded");
                } else {
                    RGridResource resource = resourceFuture.get();
//                        logger.verbose("resource(" + resource.getUrl() + ") hash : " + resource.getSha256());
                    PutFuture future = this.eyesConnector.renderPutResource(runningRender, resource);
                    if (!putResourceCache.containsKey(url) || isForcePutNeeded.get()) {
                        synchronized (putResourceCache) {
                            putResourceCache.put(url, future);
                            allPuts.add(future);
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                GeneralUtils.logExceptionStackTrace(logger, e);
            }
        }
    }

    private RenderRequest[] prepareDataForRG(HashMap<String, Object> result) throws ExecutionException, InterruptedException, MalformedURLException {
        logger.verbose("enter");

        final List<RGridResource> allBlobs = Collections.synchronizedList(new ArrayList<RGridResource>());
        List<URL> resourceUrls = new ArrayList<>();

        parseScriptResult(result, allBlobs, resourceUrls);

        logger.verbose("fetching " + resourceUrls.size() + " resources...");
        //Fetch all resources
        while (!resourceUrls.isEmpty()) {
            fetchAllResources(allBlobs, resourceUrls);
        }
        logger.verbose("done fetching resources.");
        int written = addBlobsToCache(allBlobs);

        logger.verbose("written " + written + " blobs to cache.");
        //Create RenderingRequest
        List<RenderRequest> allRequestsForRG = buildRenderRequests(result, allBlobs);

        @SuppressWarnings("UnnecessaryLocalVariable")
        RenderRequest[] asArray = allRequestsForRG.toArray(new RenderRequest[0]);

        if (debugResourceWriter != null && !(debugResourceWriter instanceof NullDebugResourceWriter)) {
            for (RenderRequest renderRequest : asArray) {
                for (RGridResource value : renderRequest.getResources().values()) {
                    this.debugResourceWriter.write(value);
                }
            }
        }

        logger.verbose("exit - returning renderRequest array of length: " + asArray.length);
        return asArray;
    }

    @SuppressWarnings("unchecked")
    private void parseScriptResult(Map<String, Object> result, List<RGridResource> allBlobs, List<URL> resourceUrls) throws ExecutionException, InterruptedException {
        logger.verbose("enter");
        org.apache.commons.codec.binary.Base64 codec = new Base64();
        URL baseUrl = null;
        try {
            Object url = result.get("url");
            baseUrl = new URL((String) url);
        } catch (MalformedURLException e) {
            GeneralUtils.logExceptionStackTrace(logger, e);
        }
        logger.verbose("baseUrl: " + baseUrl);
        for (String key : result.keySet()) {
            Object value = result.get(key);
            switch (key) {
                case "blobs":
                    //TODO check if empty
                    List listOfBlobs = (List) value;
                    for (Object blob : listOfBlobs) {
                        RGridResource resource = parseBlobToGridResource(codec, baseUrl, (Map) blob);
                        if (!allBlobs.contains(resource)) {
                            allBlobs.add(resource);
                        }
                    }
                    break;

                case "resourceUrls":
                    List<String> list = (List<String>) value;
                    for (String url : list) {
                        try {
                            if (this.fetchedCacheMap.containsKey(url)) {
                                logger.verbose("Cache hit for " + url);
                                continue;
                            }
                            resourceUrls.add(new URL(baseUrl, url));
                        } catch (MalformedURLException e) {
                            GeneralUtils.logExceptionStackTrace(logger, e);
                        }
                    }
                    break;

                case "frames":
                    logger.verbose("handling 'frames' key (level: " + framesLevel.incrementAndGet() + ")");
                    List<Map<String, Object>> allObjects = (List) value;
                    for (Map<String, Object> frameObj : allObjects) {
                        handleFrame(allBlobs, resourceUrls, codec, baseUrl, frameObj);
                    }
                    logger.verbose("done handling 'frames' key (level: " + framesLevel.getAndDecrement() + ")");
                    break;
            }
        }
        int written = addBlobsToCache(allBlobs);
        logger.verbose("written " + written + " blobs to cache.");

        //parseAndCollectCSSResources(allBlobs, baseUrl, resourceUrls);
        logger.verbose("exit");
    }

    private void handleFrame(List<RGridResource> allBlobs, List<URL> resourceUrls, Base64 codec, URL baseUrl, Map<String, Object> frameObj) throws ExecutionException, InterruptedException {
        logger.verbose("enter - baseUrl: " + baseUrl);
        RGridDom frame = new RGridDom(logger);
        try {
            String url = (String) frameObj.get("url");
            frame.setUrl(url);
            frame.setDomNodes((List) frameObj.get("cdt"));
            List blobs = (List<String>) frameObj.get("blobs");
            for (Object blob : blobs) {
                RGridResource resource = parseBlobToGridResource(codec, baseUrl, (Map) blob);
                frame.addResource(resource);
            }
            List<String> frameResourceUrlsAsStrings = (List<String>) frameObj.get("resourceUrls");
            List<URL> frameResourceUrls = new ArrayList<>();

            URL frameBaseUrl = new URL(url);
            for (String frameResourceUrl : frameResourceUrlsAsStrings) {
                frameResourceUrls.add(new URL(frameBaseUrl, frameResourceUrl));
            }
            ArrayList<RGridResource> resourceArrayList = new ArrayList<>();
            fetchAllResources(resourceArrayList, frameResourceUrls);
            allBlobs.addAll(resourceArrayList);
            frame.addResources(resourceArrayList);
            allBlobs.add(frame.asResource());
        } catch (MalformedURLException e) {
            GeneralUtils.logExceptionStackTrace(logger, e);
        }

        parseScriptResult(frameObj, allBlobs, resourceUrls);
        logger.verbose("exit - baseUrl: " + baseUrl);
    }

    private List<RenderRequest> buildRenderRequests(HashMap<String, Object> result, List<RGridResource> allBlobs) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        Object cdt;
        Map<String, RGridResource> resourceMapping = new HashMap<>();
        cdt = result.get("cdt");
        RGridDom dom = new RGridDom(logger);
        dom.setDomNodes((List) cdt);
        for (RGridResource blob : allBlobs) {
            resourceMapping.put(blob.getUrl(), blob);
        }
        dom.setResources(resourceMapping);

        //Create RG requests
        List<RenderRequest> allRequestsForRG = new ArrayList<>();
        ICheckRGSettingsInternal rcInternal = (ICheckRGSettingsInternal) renderingConfiguration;

        for (Task task : this.taskList) {

            RenderBrowserInfo browserInfo = task.getBrowserInfo();
            RenderInfo renderInfo = new RenderInfo(browserInfo.getWidth(), browserInfo.getHeight(), browserInfo.getSizeMode(), rcInternal.getRegion(), browserInfo.getEmulationInfo());

            RenderRequest request = new RenderRequest(this.renderingInfo.getResultsUrl(), (String) result.get("url"), dom,
                    resourceMapping, renderInfo, browserInfo.getPlatform(), browserInfo.getBrowserType(), rcInternal.getScriptHooks(), null, rcInternal.isSendDom(), task);

            allRequestsForRG.add(request);
        }
        return allRequestsForRG;
    }

    private RGridResource parseBlobToGridResource(Base64 codec, URL baseUrl, Map blobAsMap) {
        // TODO - handle non-string values (probably empty json objects)
        String contentAsString = (String) blobAsMap.get("value");
        byte[] content = codec.decode(contentAsString);
        String urlAsString = (String) blobAsMap.get("url");
        try {

            URL url = new URL(baseUrl, urlAsString);
            urlAsString = url.toString();
        } catch (MalformedURLException e) {
            GeneralUtils.logExceptionStackTrace(logger, e);

        }

        @SuppressWarnings("UnnecessaryLocalVariable")
        RGridResource resource = new RGridResource(urlAsString, (String) blobAsMap.get("type"), content, logger);
        return resource;
    }

    private void parseAndCollectCSSResources(List<RGridResource> allBlobs, URL baseUrl, List<URL> resourceUrls) {
        for (RGridResource blob : allBlobs) {
            String contentTypeStr = blob.getContentType();
            String css = getCss(blob.getContent(), contentTypeStr);
            if (css == null) continue;
            parseCSS(css, baseUrl, resourceUrls);
        }
    }

    private String getCss(byte[] contentBytes, String contentTypeStr) {
        String[] parts = contentTypeStr.split(";");
        String charset = "UTF-8";
        for (String part : parts) {
            part = part.trim();
            if (part.equalsIgnoreCase("text/css")) {
                charset = null;
            } else {
                String[] keyVal = part.split("=");
                if (keyVal.length == 2) {
                    String key = keyVal[0].trim();
                    String val = keyVal[1].trim();
                    if (key.equalsIgnoreCase("charset")) {
                        charset = val.toUpperCase();
                    }
                }
            }
        }

        String css = null;
        if (charset != null) {
            try {
                css = new String(contentBytes, charset);
            } catch (UnsupportedEncodingException e) {
                GeneralUtils.logExceptionStackTrace(logger, e);
            }
        }
        return css;
    }

    private void parseCSS(String css, URL baseUrl, List<URL> resourceUrls) {
        logger.verbose("enter");
        final CascadingStyleSheet cascadingStyleSheet = CSSReader.readFromString(css, ECSSVersion.CSS30);
        if (cascadingStyleSheet == null) {
            logger.verbose("exit - failed to read CSS string");
            return;
        }
        collectAllImportUris(cascadingStyleSheet, resourceUrls, baseUrl);
        collectAllFontFaceUris(cascadingStyleSheet, resourceUrls, baseUrl);
        collectAllBackgroundImageUris(cascadingStyleSheet, resourceUrls, baseUrl);
        logger.verbose("exit");
    }

    private void collectAllFontFaceUris(CascadingStyleSheet cascadingStyleSheet, List<URL> allResourceUris, URL baseUrl) {
        logger.verbose("enter");
        ICommonsList<CSSFontFaceRule> allFontFaceRules = cascadingStyleSheet.getAllFontFaceRules();
        for (CSSFontFaceRule fontFaceRule : allFontFaceRules) {
            getAllResourcesUrisFromDeclarations(allResourceUris, fontFaceRule, "src", baseUrl);
        }
        logger.verbose("exit");
    }

    private void collectAllBackgroundImageUris(CascadingStyleSheet cascadingStyleSheet, List<URL> allResourceUris, URL baseUrl) {
        logger.verbose("enter");
        ICommonsList<CSSStyleRule> allStyleRules = cascadingStyleSheet.getAllStyleRules();
        for (CSSStyleRule styleRule : allStyleRules) {
            getAllResourcesUrisFromDeclarations(allResourceUris, styleRule, "background", baseUrl);
            getAllResourcesUrisFromDeclarations(allResourceUris, styleRule, "background-image", baseUrl);
        }
        logger.verbose("exit");
    }

    private void collectAllImportUris(CascadingStyleSheet cascadingStyleSheet, List<URL> allResourceUris, URL baseUrl) {
        logger.verbose("enter");
        ICommonsList<CSSImportRule> allImportRules = cascadingStyleSheet.getAllImportRules();
        for (CSSImportRule importRule : allImportRules) {
            String uri = importRule.getLocation().getURI();
            try {
                URL url = new URL(baseUrl, uri);
                allResourceUris.add(url);
            } catch (MalformedURLException e) {
                GeneralUtils.logExceptionStackTrace(logger, e);
            }
        }
        logger.verbose("exit");
    }

    private <T extends IHasCSSDeclarations<T>> void getAllResourcesUrisFromDeclarations(List<URL> allResourceUris, IHasCSSDeclarations<T> rule, String propertyName, URL baseUrl) {
        ICommonsList<CSSDeclaration> sourcesList = rule.getAllDeclarationsOfPropertyName(propertyName);
        for (CSSDeclaration cssDeclaration : sourcesList) {
            CSSExpression cssDeclarationExpression = cssDeclaration.getExpression();
            ICommonsList<ICSSExpressionMember> allExpressionMembers = cssDeclarationExpression.getAllMembers();
            ICommonsList<CSSExpressionMemberTermURI> allUriExpressions = allExpressionMembers.getAllInstanceOf(CSSExpressionMemberTermURI.class);
            for (CSSExpressionMemberTermURI uriExpression : allUriExpressions) {
                try {
                    String uri = uriExpression.getURIString();
                    if (uri.toLowerCase().startsWith("data:")) continue;
                    URL url = new URL(baseUrl, uri);
                    allResourceUris.add(url);
                } catch (MalformedURLException e) {
                    GeneralUtils.logExceptionStackTrace(logger, e);
                }
            }
        }
    }

    private int addBlobsToCache(List<RGridResource> allBlobs) {
        int written = 0;
        for (RGridResource blob : allBlobs) {
            String url = blob.getUrl();
            if (!this.fetchedCacheMap.containsKey(url)) {
                IResourceFuture resourceFuture = this.eyesConnector.createResourceFuture(blob);
                logger.verbose("Cache write for url - " + url + " hash:(" + resourceFuture + ")");
                this.fetchedCacheMap.put(url, resourceFuture);
                written++;
            }
        }
        return written;
    }

    @SuppressWarnings("WhileLoopReplaceableByForEach")
    private void fetchAllResources(final List<RGridResource> allBlobs, List<URL> resourceUrls) throws MalformedURLException, ExecutionException, InterruptedException {
        logger.verbose("enter");
        List<IResourceFuture> allFetches = new ArrayList<>();

        final Iterator<URL> iterator = resourceUrls.iterator();
        while (iterator.hasNext()) {
            URL link = iterator.next();
            String url = link.toString();
            IResourceFuture fetch = fetchedCacheMap.get(url);
            if (fetch != null) {
                logger.verbose("cache hit for url " + url);
                iterator.remove();
                allFetches.add(fetch);
                continue;
            }

            IEyesConnector eyesConnector = this.taskList.get(0).getEyesConnector();
            IResourceFuture future = eyesConnector.getResource(link);
            allFetches.add(future);
            synchronized (fetchedCacheMap) {
                if (!this.fetchedCacheMap.containsKey(link.toString())) {
                    this.fetchedCacheMap.put(link.toString(), future);
                } else {
                    logger.verbose("this.fetchedCacheMap.containsKey(" + link.toString() + ")");
                }
            }
        }

        logger.verbose("parsing " + allFetches.size() + " fetched resources");
        for (IResourceFuture future : allFetches) {
            logger.verbose("finishing future.get() for resource " + future.getUrl() + " ...");
            RGridResource resource = future.get();
            logger.verbose("done getting resource " + future.getUrl());
            this.debugResourceWriter.write(resource);

            String urlAsString = resource.getUrl();

            removeUrlFromList(urlAsString, resourceUrls);
            allBlobs.add(resource);
            String contentType = resource.getContentType();
            String css = getCss(resource.getContent(), contentType);
            logger.verbose("handling " + contentType + " resource from URL: " + urlAsString);
            if (css == null || css.isEmpty() || !contentType.contains("text/css")) continue;

            parseCSS(css, new URL(urlAsString), resourceUrls);
        }
        logger.verbose("exit");
    }

    private void removeUrlFromList(String url, List<URL> resourceUrls) {
        Iterator<URL> iterator = resourceUrls.iterator();
        while (iterator.hasNext()) {
            URL resourceUrl = iterator.next();
            if (resourceUrl.toString().equalsIgnoreCase(url)) {
                iterator.remove();
            }
        }
    }

    private void pollRenderingStatus(Map<RunningRender, RenderRequest> runningRenders) {
        logger.verbose("enter");
        List<String> ids = getRenderIds(runningRenders.keySet());
        int numOfIterations = 0;

        do {

            List<RenderStatusResults> renderStatusResultsList = this.eyesConnector.renderStatusById(ids.toArray(new String[0]));
            if (renderStatusResultsList == null || renderStatusResultsList.isEmpty() || renderStatusResultsList.get(0) == null) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    GeneralUtils.logExceptionStackTrace(logger, e);
                }
                continue;
            }

            sampleRenderingStatus(runningRenders, ids, renderStatusResultsList);

            if (ids.size() > 0) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    GeneralUtils.logExceptionStackTrace(logger, e);
                }
            }

            numOfIterations++;

        } while (!ids.isEmpty() && numOfIterations < MAX_ITERATIONS);

        for (String id : ids) {
            for (RunningRender renderedRender : runningRenders.keySet()) {
                if (renderedRender.getRenderId().equalsIgnoreCase(id)) {
                    Task task = runningRenders.get(renderedRender).getTask();
                    task.setRenderError(id);
                    logger.verbose("removing failed render id: " + id);
                    break;
                }
            }
        }

        this.isTaskComplete.set(true);
        this.notifySuccessAllListeners();
        logger.verbose("exit");
    }

    private void sampleRenderingStatus(Map<RunningRender, RenderRequest> runningRenders, List<String> ids, List<RenderStatusResults> renderStatusResultsList) {
        logger.verbose("enter - renderStatusResultsList.size: " + renderStatusResultsList.size());

        for (int i = 0, j = 0; i < renderStatusResultsList.size(); i++) {
            RenderStatusResults renderStatusResults = renderStatusResultsList.get(i);
            if (renderStatusResults == null) {
                continue;
            }

            RenderStatus renderStatus = renderStatusResults.getStatus();
            boolean isRenderedStatus = renderStatus == RenderStatus.RENDERED;
            boolean isErrorStatus = renderStatus == RenderStatus.ERROR;
            logger.verbose("renderStatusResults - " + renderStatusResults);
            if (isRenderedStatus || isErrorStatus) {

                String removedId = ids.remove(j);

                for (RunningRender renderedRender : runningRenders.keySet()) {
                    if (renderedRender.getRenderId().equalsIgnoreCase(removedId)) {
                        Task task = runningRenders.get(renderedRender).getTask();
                        Iterator<Task> iterator = openTaskList.iterator();
                        while (iterator.hasNext()) {
                            Task openTask = iterator.next();
                            if (openTask.getRunningTest() == task.getRunningTest()) {
                                if (isRenderedStatus) {
                                    logger.verbose("setting openTask " + openTask + " render result: " + renderStatusResults + " to url " + this.result.get("url"));
                                    openTask.setRenderResult(renderStatusResults);
                                } else {
                                    logger.verbose("setting openTask " + openTask + " render error: " + removedId + " to url " + this.result.get("url"));
                                    openTask.setRenderError(removedId);
                                }
                                iterator.remove();
                            }
                        }
                        logger.verbose("setting task " + task + " render result: " + renderStatusResults + " to url " + this.result.get("url"));
                        task.setRenderResult(renderStatusResults);
                        break;
                    }
                }
            } else {
                j++;
            }
        }
        logger.verbose("exit");
    }


    public boolean getIsTaskComplete() {
        return isTaskComplete.get();
    }

    public void addListener(RenderTaskListener listener) {
        this.listeners.add(listener);
    }


}

