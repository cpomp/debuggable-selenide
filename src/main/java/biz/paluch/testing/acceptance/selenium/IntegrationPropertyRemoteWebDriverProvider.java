package biz.paluch.testing.acceptance.selenium;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import org.jbehave.web.selenium.DelegatingWebDriverProvider;
import org.openqa.selenium.*;
import org.openqa.selenium.remote.*;

import biz.paluch.testing.acceptance.AcceptanceProperties;

/**
 * <p>
 * Provides a {@link org.openqa.selenium.remote.RemoteWebDriver} that connects to a URL specified by system property
 * "REMOTE_WEBDRIVER_URL" and allows to take screenshots.
 * </p>
 * <p>
 * The default {@link org.openqa.selenium.remote.DesiredCapabilities}, specified by {@link #makeDesiredCapabilities()}, are for
 * Windows Firefox 3.6 allowing screenshots.
 * </p>
 */
public class IntegrationPropertyRemoteWebDriverProvider extends DelegatingWebDriverProvider {

    private final DesiredCapabilities desiredCapabilities;
    private boolean verbose = false;
    private Properties properties;

    /**
     * With default capabilities
     *
     * @see IntegrationPropertyRemoteWebDriverProvider#makeDesiredCapabilities()
     * @param properties
     */
    public IntegrationPropertyRemoteWebDriverProvider(Properties properties) {
        this(null, properties);
    }

    /**
     * Default Desired Capabilities: Any-Platform, Any Firefox Version, unless something is specified via a system-property
     * "browser.version" and 'Takes Screen-Shot'
     * 
     * @return a DesiredCapabilities matching the above.
     */
    protected DesiredCapabilities makeDesiredCapabilities() {
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities(getBrowser(), getBrowserVersion(), getPlatform());
        desiredCapabilities.setCapability(CapabilityType.TAKES_SCREENSHOT, true);
        desiredCapabilities.setCapability(CapabilityType.SUPPORTS_JAVASCRIPT, true);

        return desiredCapabilities;
    }

    /**
     * Get the default browser version for use on the Remote system.
     * 
     * @return "firefox" or whatever you have specified on system property 'browser.browser'
     */
    protected String getBrowser() {
        String bv = properties.getProperty(AcceptanceProperties.SELENIUM_BROWSER);
        return bv == null ? "firefox" : bv;
    }

    /**
     * Get the default browser platform for use on the Remote system.
     * 
     * @return "3.6" or whatever you have specified on system property 'browser.version'
     */
    protected String getBrowserVersion() {
        String bv = properties.getProperty("browser.version");
        return bv == null ? "3.6." : bv;
    }

    /**
     * Get the default browser platform for use on the Remote system.
     * 
     * @return ANY or whatever you have specified on system property 'browser.platform'
     */
    protected Platform getPlatform() {
        String bv = properties.getProperty("browser.platform");
        return bv == null ? Platform.ANY : Platform.valueOf(bv);
    }

    public IntegrationPropertyRemoteWebDriverProvider(DesiredCapabilities desiredCapabilities, Properties properties) {
        this.properties = properties;
        if (desiredCapabilities == null) {
            this.desiredCapabilities = makeDesiredCapabilities();
        } else {
            this.desiredCapabilities = desiredCapabilities;
        }

    }

    public void initialize() {
        URL url = null;
        WebDriver remoteWebDriver;
        try {
            url = createRemoteURL();
            remoteWebDriver = new ScreenshootingRemoteWebDriver(wrapCommandExecutor(new HttpCommandExecutor(url)),
                    desiredCapabilities);
        } catch (Throwable e) {
            if (verbose) {
                System.err.println("*********** Remote WebDriver Initialization Failure ************");
                e.printStackTrace(System.err);
            }
            throw new UnsupportedOperationException("Connecting to remote URL '" + url + "' failed: " + e.getMessage(), e);
        }
        // Augmenter does not work. Resulting WebDriver is good for exclusive
        // screenshooting, but not normal operation as 'session is null'
        // remoteWebDriver = new Augmenter().augment(remoteWebDriver);
        // should allow instanceof TakesScreenshot.
        // To take out when this is fixed in Selenium 2.0b4 (beta 4)
        delegate.set(remoteWebDriver);
    }

    /**
     * Override this to instrument CommandExecutor
     * 
     * @return a CommandExecutor instance.
     * @param commandExecutor a CommandExecutor that communicates over the wire.
     */
    protected CommandExecutor wrapCommandExecutor(CommandExecutor commandExecutor) {
        return commandExecutor;
    }

    public URL createRemoteURL() throws MalformedURLException {
        String url = properties.getProperty("REMOTE_WEBDRIVER_URL");
        if (url == null) {
            throw new UnsupportedOperationException("REMOTE_WEBDRIVER_URL property not specified");
        }
        return new URL(url);
    }

    static class ScreenshootingRemoteWebDriver extends RemoteWebDriver implements TakesScreenshot {

        private boolean sauceJobEnded = false;

        public ScreenshootingRemoteWebDriver(CommandExecutor commandExecutor, DesiredCapabilities desiredCapabilities) {
            super(commandExecutor, desiredCapabilities);
        }

        public <X> X getScreenshotAs(OutputType<X> target) throws WebDriverException {
            // Paul: Copied from FirefoxDriver.......
            // Get the screenshot as base64.
            String base64 = execute(DriverCommand.SCREENSHOT).getValue().toString();
            // ... and convert it.
            return target.convertFromBase64Png(base64);
        }

        @Override
        protected Response execute(String driverCommand, Map<String, ?> parameters) {
            if (sauceJobEnded) {
                throw new SauceLabsJobHasEnded();
            }
            try {
                return super.execute(driverCommand, parameters);
            } catch (WebDriverException e) {
                if (e.getMessage().indexOf("Job on Sauce is already complete") > -1) {
                    sauceJobEnded = true;
                    throw new SauceLabsJobHasEnded();
                }
                throw e;
            } catch (RuntimeException e) {
                throw e;
            }
        }
    }

    public static class SauceLabsJobHasEnded extends WebDriverException {
        public SauceLabsJobHasEnded() {
            super("SauceLabs job has ended.  It may have timed-out previously.  Not even screen-shots, "
                    + "after-scenario or after-story steps are possible after this for this WebDriver instance");
        }
    }

    public void useVerbosity(boolean verbose) {
        this.verbose = verbose;
    }
}
