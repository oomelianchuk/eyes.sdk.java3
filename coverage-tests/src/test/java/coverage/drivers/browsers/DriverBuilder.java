package coverage.drivers.browsers;

import org.openqa.selenium.WebDriver;

import java.net.MalformedURLException;
import java.util.HashMap;

public class DriverBuilder {

    public DriverBuilder() {}

    static HashMap<String, Builder> browserBuilders = new HashMap<String, Builder>()
    {{
        put("chrome", new ChromeBuilder());
        put("firefox", new FirefoxBuilder());
        put("safari-11", new Safari11Builder());
        put("safari-12", new Safari12Builder());
        put("ie-11", new InternetExplorer11Builder());
        put("edge-18", new Edge18Builder());
    }};
    protected boolean headless = true;
    protected boolean legacy = false;
    protected boolean localDriver = false;
    protected String browser = "chrome";

    public DriverBuilder headless(boolean headless) {
        this.headless = headless;
        return this;
    }

    public DriverBuilder browser(String browser) {
        this.browser = browser;
        return this;
    }

    public DriverBuilder legacy(boolean legacy) {
        this.legacy = legacy;
        return this;
    }

    public DriverBuilder localDriver(boolean localDriver) {
        this.localDriver = localDriver;
        return this;
    }

    public WebDriver build() throws MalformedURLException {
        Builder builder = browserBuilders.get(browser);
        return builder.build(headless, legacy, localDriver);
    }
}
