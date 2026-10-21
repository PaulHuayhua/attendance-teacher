package prs02.via.academy.selenium;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

/**
 * Clase base para todas las pruebas Selenium.
 * Configura RemoteWebDriver apuntando al Selenium Grid Hub.
 */
public abstract class BaseSeleniumTest {

    protected WebDriver driver;

    /** URL del microservicio bajo prueba (inyectada por Maven/Jenkins). */
    protected static final String TARGET_URL =
            System.getProperty("target.url", "http://localhost:8089");

    /** URL del Selenium Hub (inyectada por Maven/Jenkins). */
    private static final String SELENIUM_HUB_URL =
            System.getProperty("selenium.hub.url", "http://selenium-hub:4444/wd/hub");

    @BeforeEach
    void setUp() throws MalformedURLException {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080"
        );
        driver = new RemoteWebDriver(new URL(SELENIUM_HUB_URL), options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
