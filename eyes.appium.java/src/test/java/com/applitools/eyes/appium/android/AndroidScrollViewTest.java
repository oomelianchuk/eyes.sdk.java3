package com.applitools.eyes.appium.android;

import com.applitools.eyes.appium.Target;
import io.appium.java_client.PerformsTouchActions;
import io.appium.java_client.TouchAction;
import io.appium.java_client.touch.WaitOptions;
import io.appium.java_client.touch.offset.PointOption;
import org.openqa.selenium.By;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.touch.TouchActions;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class AndroidScrollViewTest extends AndroidTestSetup {

    @Test
    public void testAndroidScrollView() throws InterruptedException {
        driver.manage().timeouts().implicitlyWait(10_000, TimeUnit.MILLISECONDS);

        eyes.setMatchTimeout(1000);

        eyes.open(driver, getApplicationName(), "Check ScrollView");

        // Scroll down
        TouchActions touchActions = new TouchActions(driver);
        touchActions.down(5, 1700);
        touchActions.move(5, 100);
        touchActions.perform();
//        TouchAction scrollAction = new TouchAction((PerformsTouchActions) driver);
//        scrollAction.press(new PointOption().withCoordinates(5, 1700)).waitAction(new WaitOptions().withDuration(Duration.ofMillis(1500)));
//        scrollAction.moveTo(new PointOption().withCoordinates(5, 100));
//        scrollAction.cancel();
//        ((PerformsTouchActions) driver).performTouchAction(scrollAction);

        driver.findElement(By.id("btn_scroll_view_footer_header")).click();

        eyes.check(Target.window().fully().withName("Fullpage"));

        eyes.close();
    }
}
