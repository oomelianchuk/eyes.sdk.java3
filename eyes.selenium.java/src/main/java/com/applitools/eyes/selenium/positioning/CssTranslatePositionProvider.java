package com.applitools.eyes.selenium.positioning;

import com.applitools.eyes.*;
import com.applitools.eyes.positioning.PositionMemento;
import com.applitools.eyes.positioning.PositionProvider;
import com.applitools.eyes.selenium.EyesSeleniumUtils;
import com.applitools.utils.ArgumentGuard;
import org.openqa.selenium.WebElement;

/**
 * A {@link PositionProvider} which is based on CSS translates. This is
 * useful when we want to stitch a page which contains fixed position elements.
 */
public class CssTranslatePositionProvider implements PositionProvider {

    protected final Logger logger;
    protected final IEyesJsExecutor executor;
    private final WebElement scrollRootElement;

    private final String JSSetTransform =
            "var originalTransform = arguments[0].style.transform;" +
                    "arguments[0].style.transform = '%s';" +
                    "return originalTransform;";

    private Location lastSetPosition; // cache.

    public CssTranslatePositionProvider(Logger logger, IEyesJsExecutor executor, WebElement scrollRootElement) {
        ArgumentGuard.notNull(logger, "logger");
        ArgumentGuard.notNull(executor, "executor");
        ArgumentGuard.notNull(scrollRootElement, "scrollRootElement");

        this.logger = logger;
        this.executor = executor;
        this.scrollRootElement = scrollRootElement;

        logger.verbose("creating CssTranslatePositionProvider");
    }

    public Location getCurrentPosition() {
        logger.verbose("position to return: " + lastSetPosition);
        return lastSetPosition;
    }

    public Location setPosition(Location location) {
        ArgumentGuard.notNull(location, "location");
        logger.verbose("setting position to " + location + " (element: " + scrollRootElement + ")");
        Location negatedPos = new Location(-location.getX(), -location.getY());
        executor.executeScript(String.format(JSSetTransform, "translate(" + negatedPos.getX() + "px, " + negatedPos.getY() + "px)"), scrollRootElement);
        lastSetPosition = location;
        return lastSetPosition;
    }

    public RectangleSize getEntireSize() {
        RectangleSize entireSize =
                EyesSeleniumUtils.getEntireElementSize(logger, executor, scrollRootElement);
        logger.verbose("CssTranslatePositionProvider - Entire size: " + entireSize);
        return entireSize;
    }

    public PositionMemento getState() {
        String transform = (String) executor.executeScript("return arguments[0].style.transform;", this.scrollRootElement);
        return new CssTranslatePositionMemento(transform, lastSetPosition);
    }

    public void restoreState(PositionMemento state) {
        String transform = ((CssTranslatePositionMemento) state).getTransform();
        String formatted = String.format(JSSetTransform, transform);
        executor.executeScript(formatted, this.scrollRootElement);
        lastSetPosition = ((CssTranslatePositionMemento) state).getPosition();
    }
}
