package com.applitools.eyes.selenium.wrappers;

import com.applitools.eyes.*;
import com.applitools.eyes.positioning.PositionProvider;
import com.applitools.eyes.selenium.SizeAndBorders;
import com.applitools.eyes.triggers.MouseAction;
import com.applitools.utils.ArgumentGuard;
import com.google.common.collect.ImmutableMap;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Coordinates;
import org.openqa.selenium.remote.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("FieldCanBeLocal")
public class EyesRemoteWebElement extends RemoteWebElement {
    private final Logger logger;
    private final EyesWebDriver eyesDriver;
    private final RemoteWebElement webElement;
    private Method executeMethod;

    private final String JS_GET_COMPUTED_STYLE_FORMATTED_STR =
            "var elem = arguments[0]; " +
                    "var styleProp = '%s'; " +
                    "if (window.getComputedStyle) { " +
                    "return window.getComputedStyle(elem, null)" +
                    ".getPropertyValue(styleProp);" +
                    "} else if (elem.currentStyle) { " +
                    "return elem.currentStyle[styleProp];" +
                    "} else { " +
                    "return null;" +
                    "}";

    private final String JS_GET_SCROLL_LEFT =
            "return arguments[0].scrollLeft;";

    private final String JS_GET_SCROLL_TOP =
            "return arguments[0].scrollTop;";

    private final String JS_GET_SCROLL_WIDTH =
            "return arguments[0].scrollWidth;";

    private final String JS_GET_SCROLL_HEIGHT =
            "return arguments[0].scrollHeight;";

    private final String JS_SCROLL_TO_FORMATTED_STR =
            "arguments[0].scrollLeft = %d;" +
                    "arguments[0].scrollTop = %d;";

    private final String JS_GET_SCROLL_POSITION =
            "return arguments[0].scrollLeft + ';' + arguments[0].scrollTop;";

    private final String JS_GET_OVERFLOW =
            "return arguments[0].style.overflow;";

    private final String JS_SET_OVERFLOW_FORMATTED_STR =
            "arguments[0].style.overflow = '%s'";

    private final String JS_GET_CLIENT_WIDTH = "return arguments[0].clientWidth;";
    private final String JS_GET_CLIENT_HEIGHT = "return arguments[0].clientHeight;";

    private final String JS_GET_CLIENT_SIZE = "return [arguments[0].clientWidth, arguments[0].clientHeight];";

    private final String JS_GET_BORDER_WIDTHS_ARR =
            "var retVal = retVal || [];" +
                    "if (window.getComputedStyle) { " +
                    "var computedStyle = window.getComputedStyle(elem, null);" +
                    "retVal.push(computedStyle.getPropertyValue('border-left-width'));" +
                    "retVal.push(computedStyle.getPropertyValue('border-top-width'));" +
                    "retVal.push(computedStyle.getPropertyValue('border-right-width')); " +
                    "retVal.push(computedStyle.getPropertyValue('border-bottom-width'));" +
                    "} else if (elem.currentStyle) { " +
                    "retVal.push(elem.currentStyle['border-left-width']);" +
                    "retVal.push(elem.currentStyle['border-top-width']);" +
                    "retVal.push(elem.currentStyle['border-right-width']);" +
                    "retVal.push(elem.currentStyle['border-bottom-width']);" +
                    "} else { " +
                    "retVal.push(0,0,0,0);" +
                    "}";

    @SuppressWarnings("unused")
    private final String JS_GET_BORDER_WIDTHS =
            JS_GET_BORDER_WIDTHS_ARR + "return retVal;";

    private final String JS_GET_SIZE_AND_BORDER_WIDTHS =
            "var elem = arguments[0]; " +
                    "var retVal = [elem.clientWidth, elem.clientHeight]; " +
                    JS_GET_BORDER_WIDTHS_ARR +
                    "return retVal;";

    private PositionProvider positionProvider;

    public EyesRemoteWebElement(Logger logger, EyesWebDriver eyesDriver, WebElement webElement) {
        super();

        ArgumentGuard.notNull(logger, "logger");
        ArgumentGuard.notNull(eyesDriver, "eyesDriver");
        ArgumentGuard.notNull(webElement, "webElement");

        this.logger = logger;
        this.eyesDriver = eyesDriver;

        if (webElement instanceof RemoteWebElement) {
            this.webElement = (RemoteWebElement) webElement;
        } else {
            throw new EyesException("The input web element is not a RemoteWebElement.");
        }

        setParent(eyesDriver.getRemoteWebDriver());
        setId(this.webElement.getId());

        try {
            // We can't call the execute method directly because it is
            // protected, and we must override this function since we don't
            // have the "parent" and "id" of the aggregated object.
            executeMethod = RemoteWebElement.class.getDeclaredMethod("execute",
                    String.class, Map.class);
            executeMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new EyesException("Failed to find 'execute' method!");
        }
    }

    public Region getBounds() {
        Point weLocation = webElement.getLocation();
        int left = weLocation.getX();
        int top = weLocation.getY();
        int width = 0;
        int height = 0;

        try {
            Dimension weSize = webElement.getSize();
            width = weSize.getWidth();
            height = weSize.getHeight();
        } catch (Exception ex) {
            // Not supported on all platforms.
        }

        if (left < 0) {
            width = Math.max(0, width + left);
            left = 0;
        }

        if (top < 0) {
            height = Math.max(0, height + top);
            top = 0;
        }

        return new Region(left, top, width, height, CoordinatesType.CONTEXT_RELATIVE);
    }

    /**
     * Returns the computed value of the style property for the current
     * element.
     * @param propStyle The style property which value we would like to
     *                  extract.
     * @return The value of the style property of the element, or {@code null}.
     */
    public String getComputedStyle(String propStyle) {
        String scriptToExec = String.format
                (JS_GET_COMPUTED_STYLE_FORMATTED_STR, propStyle);
        return (String) eyesDriver.executeScript(scriptToExec, this);
    }

    /**
     * @return The integer value of a computed style.
     */
    public int getComputedStyleInteger(String propStyle) {
        return Math.round(Float.valueOf(getComputedStyle(propStyle).trim().
                replace("px", "")));
    }

    /**
     * @return The value of the scrollLeft property of the element.
     */
    public int getScrollLeft() {
        return (int) Math.ceil(Double.parseDouble(eyesDriver.executeScript(JS_GET_SCROLL_LEFT,
                this).toString()));
    }

    /**
     * @return The value of the scrollTop property of the element.
     */
    public int getScrollTop() {
        return (int) Math.ceil(Double.parseDouble(eyesDriver.executeScript(JS_GET_SCROLL_TOP,
                this).toString()));
    }

    /**
     * @return The value of the scrollWidth property of the element.
     */
    public int getScrollWidth() {
        return (int) Math.ceil(Double.parseDouble(eyesDriver.executeScript(JS_GET_SCROLL_WIDTH,
                this).toString()));
    }

    /**
     * @return The value of the scrollHeight property of the element.
     */
    public int getScrollHeight() {
        return (int) Math.ceil(Double.parseDouble(eyesDriver.executeScript(JS_GET_SCROLL_HEIGHT,
                this).toString()));
    }

    public int getClientWidth() {
        return (int) Math.ceil(Double.parseDouble(eyesDriver.executeScript(JS_GET_CLIENT_WIDTH, this).toString()));
    }

    public int getClientHeight() {
        return (int) Math.ceil(Double.parseDouble(eyesDriver.executeScript(JS_GET_CLIENT_HEIGHT, this).toString()));
    }

    /**
     * @return The width of the left border.
     */
    public int getBorderLeftWidth() {
        return getComputedStyleInteger("border-left-width");
    }

    /**
     * @return The width of the right border.
     */
    public int getBorderRightWidth() {
        return getComputedStyleInteger("border-right-width");
    }

    /**
     * @return The width of the top border.
     */
    public int getBorderTopWidth() {
        return getComputedStyleInteger("border-top-width");
    }

    /**
     * @return The width of the bottom border.
     */
    public int getBorderBottomWidth() {
        return getComputedStyleInteger("border-bottom-width");
    }

    /**
     * Scrolls to the specified location inside the element.
     * @param location The location to scroll to.
     */
    public Location scrollTo(Location location) {
        Object position = eyesDriver.executeScript(String.format(JS_SCROLL_TO_FORMATTED_STR,
                location.getX(), location.getY()) + JS_GET_SCROLL_POSITION, this);
        String[] xy = position.toString().split(";");
        if (xy.length != 2) {
            throw new EyesException("Could not get scroll position!");
        }
        float x = Float.parseFloat(xy[0]);
        float y = Float.parseFloat(xy[1]);
        return new Location((int) Math.ceil(x), (int) Math.ceil(y));
    }

    public Location getScrollLocation() {
        Object position = eyesDriver.executeScript(JS_GET_SCROLL_POSITION, this);
        String[] xy = position.toString().split(";");
        if (xy.length != 2) {
            throw new EyesException("Could not get scroll position!");
        }
        float x = Float.parseFloat(xy[0]);
        float y = Float.parseFloat(xy[1]);
        return new Location((int) Math.ceil(x), (int) Math.ceil(y));
    }

    /**
     * @return The overflow of the element.
     */
    public String getOverflow() {
        return eyesDriver.executeScript(JS_GET_OVERFLOW, this).toString();
    }

    /**
     * Sets the overflow of the element.
     * @param overflow The overflow to set.
     */
    public void setOverflow(String overflow) {
        eyesDriver.executeScript(String.format(JS_SET_OVERFLOW_FORMATTED_STR, overflow), this);
    }

    @Override
    public void click() {

        // Letting the driver know about the current action.
        Region currentControl = getBounds();
        eyesDriver.getEyes().addMouseTrigger(MouseAction.Click, this);
        logger.verbose(String.format("click(%s)", currentControl));

        webElement.click();
    }

    @Override
    public WebDriver getWrappedDriver() {
        return eyesDriver;
    }
//
//    @Override
//    public String getId() {
//        return webElement.getId();
//    }
//
//    @Override
//    public void setParent(RemoteWebDriver parent) {
//        webElement.setParent(parent);
//    }
/*
    @Override
    protected Response execute(String command, Map<String, ?> parameters) {
        // "execute" is a protected method, which is why we use reflection.
        try {
            return (Response) executeMethod.invoke(webElement, command,
                    parameters);
        } catch (Exception e) {
            throw new EyesException("Failed to invoke 'execute' method!", e);
        }

    }*/
//
//    @Override
//    public void setId(String id) {
//        webElement.setId(id);
//    }

    @Override
    public void setFileDetector(FileDetector detector) {
        webElement.setFileDetector(detector);
    }

    @Override
    public void submit() {
        webElement.submit();
    }

    @Override
    public void sendKeys(CharSequence... keysToSend) {
        for (CharSequence keys : keysToSend) {
            String text = String.valueOf(keys);
            eyesDriver.getEyes().addTextTrigger(this, text);
        }

        webElement.sendKeys(keysToSend);
    }

    @Override
    public void clear() {
        webElement.clear();
    }

    @Override
    public String getTagName() {
        return webElement.getTagName();
    }

    @Override
    public String getAttribute(String name) {
        return webElement.getAttribute(name);
    }

    @Override
    public boolean isSelected() {
        return webElement.isSelected();
    }

    @Override
    public boolean isEnabled() {
        return webElement.isEnabled();
    }

    @Override
    public String getText() {
        return webElement.getText();
    }

    @Override
    public String getCssValue(String propertyName) {
        return webElement.getCssValue(propertyName);
    }

    /**
     * For RemoteWebElement object, the function returns an
     * EyesRemoteWebElement object. For all other types of WebElement,
     * the function returns the original object.
     */
    private WebElement wrapElement(WebElement elementToWrap) {
        WebElement resultElement = elementToWrap;
        if (elementToWrap instanceof RemoteWebElement) {
            resultElement = new EyesRemoteWebElement(logger, eyesDriver, elementToWrap);
        }
        return resultElement;
    }

    /**
     * For RemoteWebElement object, the function returns an
     * EyesRemoteWebElement object. For all other types of WebElement,
     * the function returns the original object.
     */
    private List<WebElement> wrapElements(List<WebElement>
                                                  elementsToWrap) {
        // This list will contain the found elements wrapped with our class.
        List<WebElement> wrappedElementsList =
                new ArrayList<>(elementsToWrap.size());

        for (WebElement currentElement : elementsToWrap) {
            if (currentElement instanceof RemoteWebElement) {
                wrappedElementsList.add(new EyesRemoteWebElement(logger,
                        eyesDriver, currentElement));
            } else {
                wrappedElementsList.add(currentElement);
            }
        }

        return wrappedElementsList;
    }

    @Override
    public List<WebElement> findElements(By by) {
        return wrapElements(webElement.findElements(by));
    }

    @Override
    public WebElement findElement(By by) {
        return wrapElement(webElement.findElement(by));
    }

    @Override
    public WebElement findElementById(String using) {
        return wrapElement(webElement.findElementById(using));
    }

    @Override
    public List<WebElement> findElementsById(String using) {
        return wrapElements(webElement.findElementsById(using));
    }

    @Override
    public WebElement findElementByLinkText(String using) {
        return wrapElement(webElement.findElementByLinkText(using));
    }

    @Override
    public List<WebElement> findElementsByLinkText(String using) {
        return wrapElements(webElement.findElementsByLinkText(using));
    }

    @Override
    public WebElement findElementByName(String using) {
        return wrapElement(webElement.findElementByName(using));
    }

    @Override
    public List<WebElement> findElementsByName(String using) {
        return wrapElements(webElement.findElementsByName(using));
    }

    @Override
    public WebElement findElementByClassName(String using) {
        return wrapElement(webElement.findElementByClassName(using));
    }

    @Override
    public List<WebElement> findElementsByClassName(String using) {
        return wrapElements(webElement.findElementsByClassName(using));
    }

    @Override
    public WebElement findElementByCssSelector(String using) {
        return wrapElement(webElement.findElementByCssSelector(using));
    }

    @Override
    public List<WebElement> findElementsByCssSelector(String using) {
        return wrapElements(webElement.findElementsByCssSelector(using));
    }

    @Override
    public WebElement findElementByXPath(String using) {
        return wrapElement(webElement.findElementByXPath(using));
    }

    @Override
    public List<WebElement> findElementsByXPath(String using) {
        return wrapElements(webElement.findElementsByXPath(using));
    }

    @Override
    public WebElement findElementByPartialLinkText(String using) {
        return wrapElement(webElement.findElementByPartialLinkText(using));
    }

    @Override
    public List<WebElement> findElementsByPartialLinkText(String using) {
        return wrapElements(webElement.findElementsByPartialLinkText(using));
    }

    @Override
    public WebElement findElementByTagName(String using) {
        return wrapElement(webElement.findElementByTagName(using));
    }

    @Override
    public List<WebElement> findElementsByTagName(String using) {
        return wrapElements(webElement.findElementsByTagName(using));
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof RemoteWebElement) && webElement.getId().equals(((RemoteWebElement) obj).getId());
    }

    @Override
    public int hashCode() {
        return webElement.hashCode();
    }

    @Override
    public boolean isDisplayed() {
        return webElement.isDisplayed();
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Point getLocation() {
        // This is workaround: Selenium currently just removes the value
        // after the decimal dot (instead of rounding), which causes
        // incorrect locations to be returned when using FF.
        // So, we copied the code from the Selenium
        // client and instead of using "rawPoint.get(...).intValue()" we
        // return the double value and use "round".
        String elementId = getId();
        Response response = execute(DriverCommand.GET_ELEMENT_LOCATION,
                ImmutableMap.of("id", elementId));
        Map<String, Object> rawPoint =
                (Map<String, Object>) response.getValue();
        int x = (int) Math.round(((Number) rawPoint.get("x")).doubleValue());
        int y = (int) Math.round(((Number) rawPoint.get("y")).doubleValue());
        return new Point(x, y);

        // TODO: Use the command delegation instead. (once the bug is fixed).
//        return webElement.getLocation();
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Dimension getSize() {
        // This is workaround: Selenium currently just removes the value
        // after the decimal dot (instead of rounding up), which might cause
        // incorrect size to be returned . So, we copied the code from the
        // Selenium client and instead of using "rawPoint.get(...).intValue()"
        // we return the double value, and use "ceil".
        String elementId = getId();
        Response response = execute(DriverCommand.GET_ELEMENT_SIZE,
                ImmutableMap.of("id", elementId));
        Map<String, Object> rawSize = (Map<String, Object>) response.getValue();
        int width = (int) Math.ceil(
                ((Number) rawSize.get("width")).doubleValue());
        int height = (int) Math.ceil(
                ((Number) rawSize.get("height")).doubleValue());
        return new Dimension(width, height);

        // TODO: Use the command delegation instead. (once the bug is fixed).
//        return webElement.getOuterSize();
    }

    public RectangleSize getClientSize() {
        Object retVal = eyesDriver.executeScript(JS_GET_CLIENT_SIZE, this);
        @SuppressWarnings("unchecked") List<Float> esAsList = (List<Float>) retVal;
        return new RectangleSize(
                (int) Math.round(esAsList.get(0).doubleValue()),
                (int) Math.round(esAsList.get(1).doubleValue()));
    }

    @Override
    public Coordinates getCoordinates() {
        return webElement.getCoordinates();
    }

    @Override
    public String toString() {
        return "EyesRemoteWebElement:" + webElement.toString();
    }

    public PositionProvider getPositionProvider() {
        return positionProvider;
    }

    public void setPositionProvider(PositionProvider positionProvider) {
        this.positionProvider = positionProvider;
    }

    public SizeAndBorders getSizeAndBorders() {
        Object retVal = eyesDriver.executeScript(JS_GET_SIZE_AND_BORDER_WIDTHS, this);
        @SuppressWarnings("unchecked") List<Object> esAsList = (List<Object>) retVal;
        return new SizeAndBorders(
                ((Long) esAsList.get(0)).intValue(),
                ((Long) esAsList.get(1)).intValue(),
                Integer.parseInt(((String) esAsList.get(2)).replace("px", "")),
                Integer.parseInt(((String) esAsList.get(3)).replace("px", "")),
                Integer.parseInt(((String) esAsList.get(4)).replace("px", "")),
                Integer.parseInt(((String) esAsList.get(5)).replace("px", "")));
    }

    public Rectangle getBoundingClientRect() {
        String retVal = (String) eyesDriver.executeScript("var r = arguments[0].getBoundingClientRect();" +
                "return r.x+';'+r.y+';'+r.width+';'+r.height;", this);
        String[] parts = retVal.split(";");
        Rectangle rect = new Rectangle(
                Math.round(Float.parseFloat(parts[0])),
                Math.round(Float.parseFloat(parts[1])),
                Math.round(Float.parseFloat(parts[2])),
                Math.round(Float.parseFloat(parts[3])));
        return rect;
    }
}
