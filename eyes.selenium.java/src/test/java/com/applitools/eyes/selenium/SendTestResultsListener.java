package com.applitools.eyes.selenium;
import com.applitools.eyes.utils.CommUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.testng.*;

import java.io.IOException;
import java.util.*;

public class SendTestResultsListener implements ISuiteListener {

    Set<ITestResult> allTestResults = null;

    @Override
    public void onStart(ISuite iSuite) {

    }

    @Override
    public void onFinish(ISuite iSuite) {
        allTestResults = new HashSet<>();
        collectTestResultsIntoSet(iSuite);
        JsonObject resultJson = getResultJsonAsString();
        CommUtils.postJson("http://applitools-sdk-test-results.herokuapp.com/result", new Gson().fromJson(resultJson, Map.class), null);
    }

    private void collectTestResultsIntoSet(ISuite iSuite){
        Map<String, ISuiteResult> suiteResultMap = iSuite.getResults();
        Iterator<ISuiteResult> suiteResultIterator = suiteResultMap.values().iterator();
        while (suiteResultIterator.hasNext()) {
            ITestContext testContext = suiteResultIterator.next().getTestContext();
            allTestResults.addAll(testContext.getPassedTests().getAllResults());
            allTestResults.addAll(testContext.getFailedButWithinSuccessPercentageTests().getAllResults());
            allTestResults.addAll(testContext.getFailedConfigurations().getAllResults());
            allTestResults.addAll(testContext.getFailedTests().getAllResults());
            allTestResults.addAll(testContext.getSkippedConfigurations().getAllResults());
            allTestResults.addAll(testContext.getSkippedTests().getAllResults());
        }
    }

    private JsonObject getResultJsonAsString(){
        Iterator<ITestResult> resultsIterator = allTestResults.iterator();
        JsonObject finalJsonObject = new JsonObject();
        JsonObject innerResultsJsonObject = new JsonObject();
        while (resultsIterator.hasNext()){
            ITestResult testResult = resultsIterator.next();
            String methodName = testResult.getMethod().getMethodName();
            boolean isPassed = testResult.isSuccess();
            innerResultsJsonObject.addProperty(methodName, isPassed);
        }
        finalJsonObject.addProperty("sdk", "java");
        finalJsonObject.add("results", innerResultsJsonObject);
        return finalJsonObject;
    }
}