package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.nio.file.Files

class KtorServerProviderTest{

    private val serviceDescription = object : ServiceDescription {
        override val name: String = "test"
        override val version: String = "1.0.0"
    }

    private val serviceDiscoveryFile by lazy {
        Files.createTempFile("serviceDis", ".yml").toFile().also {
            // language=yaml
            it.writeText(
                """
                    ---
                    development:
                      serviceDiscovery:
                        storage: :8010
                        files: :8010
                        files.upload: :8010
            """.trimIndent()
            )
        }
    }

    @Test
    fun `test init - no cli args`() {
        val micro = Micro()
        val ktor = KtorServerProviderFeature()
        ktor.init(micro, serviceDescription, emptyList())
    }

    @Test
    fun `test init`() {
        val serviceDes = mockk<ServiceDescription>()

        every { serviceDes.name } returns "files"
        every { serviceDes.version } returns "2.2"

        val micro = Micro().apply {
            initWithDefaultFeatures(serviceDes, arrayOf("--config-dir", serviceDiscoveryFile.parent))
        }
        val ktor = KtorServerProviderFeature()
        ktor.init(micro, serviceDes, emptyList())
    }
}
