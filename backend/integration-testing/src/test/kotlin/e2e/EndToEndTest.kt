package dk.sdu.cloud.integration.e2e

import com.sun.jna.Platform
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.findPreferredOutgoingIp
import dk.sdu.cloud.integration.t
import org.junit.Rule
import org.openqa.selenium.Dimension
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxOptions
import org.testcontainers.containers.BrowserWebDriverContainer
import java.io.File

// Kotlin cannot instantiate BrowserWebDriverContainer due to the generics
class BrowserWebDriverContainerFix : BrowserWebDriverContainer<BrowserWebDriverContainerFix>() {
    val localhostIp: String by lazy {
        if (Platform.isLinux()) {
            findPreferredOutgoingIp()
        } else {
            testHostIpAddress
        }
    }
}

data class EndToEndContext(
    val address: String,
    val driver: WebDriver
)

enum class E2EDrivers {
    FIREFOX,
    CHROME;
}

abstract class EndToEndTest : IntegrationTest() {
    @JvmField @Rule
    val chrome = BrowserWebDriverContainerFix().apply {
        withCapabilities(ChromeOptions())
        withRecordingMode(
            BrowserWebDriverContainer.VncRecordingMode.RECORD_ALL,
            File("/tmp/recordings").also { it.mkdirs() }
        )
        withFileSystemBind("/tmp/recordings", "/tmp/recordings")
        if (Platform.isLinux()) withNetworkMode("host")
    }

    @JvmField @Rule
    val firefox = BrowserWebDriverContainerFix().apply {
        withCapabilities(FirefoxOptions())
        withRecordingMode(
            BrowserWebDriverContainer.VncRecordingMode.RECORD_ALL,
            File("/tmp/recordings").also { it.mkdirs() }
        )
        withFileSystemBind("/tmp/recordings", "/tmp/recordings")
        if (Platform.isLinux()) withNetworkMode("host")
    }

    fun e2e(
        drivers: Array<E2EDrivers> = E2EDrivers.values(),
        block: suspend EndToEndContext.() -> Unit
    ) {
        for (driver in drivers) {
            t {
                val d = when (driver) {
                    E2EDrivers.FIREFOX -> firefox
                    E2EDrivers.CHROME -> chrome
                }

                d.webDriver.manage().window().size = Dimension(1300, 800)
                EndToEndContext("http://${d.localhostIp}:9000", d.webDriver).block()
            }
        }
    }
}
