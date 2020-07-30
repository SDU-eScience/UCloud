package dk.sdu.cloud.integration.e2e

import com.sun.jna.Platform
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.findPreferredOutgoingIp
import dk.sdu.cloud.integration.t
import dk.sdu.cloud.micro.configuration
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.openqa.selenium.Dimension
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.testcontainers.containers.BrowserWebDriverContainer
import java.io.File

// Kotlin cannot instantiate BrowserWebDriverContainer due to the generics
class BrowserWebDriverContainerFix : BrowserWebDriverContainer<BrowserWebDriverContainerFix>()

data class EndToEndContext(
    val address: String,
    val driver: WebDriver
)

enum class E2EDrivers {
    FIREFOX,
    CHROME;
}

data class E2EConfig(val useLocalDriver: Boolean = false)

abstract class EndToEndTest : IntegrationTest() {
    val config = UCloudLauncher.micro.configuration.requestChunkAtOrNull("e2e") ?: E2EConfig()

    private val localDocker: String by lazy {
        when {
            Platform.isLinux() -> {
                findPreferredOutgoingIp()
            }
            Platform.isMac() -> {
                "host.docker.internal"
            }
            else -> {
                throw IllegalStateException()
            }
        }
    }

    @JvmField
    @Rule
    val chrome = if (config.useLocalDriver) null else BrowserWebDriverContainerFix().apply {
        withCapabilities(ChromeOptions())
        withRecordingMode(
            BrowserWebDriverContainer.VncRecordingMode.RECORD_ALL,
            File("/tmp/recordings").also { it.mkdirs() }
        )
        withFileSystemBind("/tmp/recordings", "/tmp/recordings")
        if (Platform.isLinux()) withNetworkMode("host")
    }

    @JvmField
    @Rule
    val firefox = if (config.useLocalDriver) null else BrowserWebDriverContainerFix().apply {
        withCapabilities(FirefoxOptions())
        withRecordingMode(
            BrowserWebDriverContainer.VncRecordingMode.RECORD_ALL,
            File("/tmp/recordings").also { it.mkdirs() }
        )
        withFileSystemBind("/tmp/recordings", "/tmp/recordings")
        if (Platform.isLinux()) withNetworkMode("host")
    }

    val localFirefox = if (!config.useLocalDriver) null else FirefoxDriver()
    val localChrome = if (!config.useLocalDriver) null else ChromeDriver()

    @JvmField
    @Rule
    val firefoxRule = object : ExternalResource() {
        override fun after() {
            localFirefox?.close()
            localChrome?.close()
        }
    }

    fun e2e(
        drivers: Array<E2EDrivers> = E2EDrivers.values(),
        block: suspend EndToEndContext.() -> Unit
    ) {

        for (driver in drivers) {
            t {
                val d = when (driver) {
                    E2EDrivers.FIREFOX -> if (config.useLocalDriver) localFirefox!! else firefox!!.webDriver
                    E2EDrivers.CHROME -> if (config.useLocalDriver) localChrome!! else chrome!!.webDriver
                }

                d.manage().window().size = Dimension(1300, 800)
                val localAddress = if (config.useLocalDriver) "localhost" else localDocker
                EndToEndContext("http://${localAddress}:9000", d).block()
            }
        }
    }
}
