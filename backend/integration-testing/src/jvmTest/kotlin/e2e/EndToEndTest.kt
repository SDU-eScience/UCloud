package dk.sdu.cloud.integration.e2e

import com.sun.jna.Platform
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.findPreferredOutgoingIp
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.test.UCloudTestSuiteBuilder
import kotlinx.coroutines.runBlocking
import org.openqa.selenium.Dimension
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.html5.WebStorage
import org.testcontainers.containers.BrowserWebDriverContainer
import java.io.File

data class EndToEndContext(
    val address: String,
    val driver: WebDriver
)

enum class E2EDrivers {
    FIREFOX,
    CHROME;
}

data class E2EConfig(
    val useLocalDriver: Boolean = true
)

abstract class EndToEndTest : IntegrationTest() {
    fun <R> e2e(
        driver: E2EDrivers,
        block: suspend EndToEndContext.() -> R
    ): R {
        return runBlocking {
            val d = when (driver) {
                E2EDrivers.FIREFOX -> localFirefox
                E2EDrivers.CHROME -> localChrome
            }

            d.manage().window().size = Dimension(1600, 900)
            val localAddress = "localhost"
            EndToEndContext("http://${localAddress}:9000", d).block()
        }
    }


    fun <In, Out> UCloudTestSuiteBuilder<In, Out>.executeE2E(
        driver: E2EDrivers,
        block: suspend E2EIntegrationContext<In>.() -> Out
    ) {
        execute {
            e2e(driver) {
                runCatching {
                    this.driver.manage().deleteAllCookies()
                    if (this.driver is WebStorage) {
                        this.driver.localStorage.clear()
                    }
                }

                E2EIntegrationContext(address, this.driver, input, testId).block()
            }
        }
    }

    companion object {
        val localFirefox by lazy { FirefoxDriver() }
        val localChrome by lazy { ChromeDriver() }
    }
}

data class E2EIntegrationContext<In>(
    val address: String,
    val driver: WebDriver,
    val input: In,
    val testId: Int
)
