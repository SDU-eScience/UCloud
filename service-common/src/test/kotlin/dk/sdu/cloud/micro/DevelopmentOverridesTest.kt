package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.test.initializeMicro
import org.junit.Test
import java.nio.file.Files

class DevelopmentOverridesTest{

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

    private val serviceDiscoveryFileWithErrors by lazy {
        Files.createTempFile("serviceDisWithErrors", ".yml").toFile().also {
            // language=yaml
            it.writeText(
                """
                    ---
                    development:
                      serviceDiscovery:
                        storage: :8010
                        files: wrongsize
                        files.upload: :notint
            """.trimIndent()
            )
        }
    }

    private val serviceDescription = object : ServiceDescription {
        override val name: String = "test"
        override val version: String = "1.0.0"
    }

    @Test
    fun `test init - no cli args`() {
        val micro = initializeMicro()
        val devOverride = DevelopmentOverrides()
        devOverride.init(micro, serviceDescription, emptyList())
    }

    @Test
    fun `test init - have files`() {
        val file = serviceDiscoveryFile
        initializeMicro(listOf("--config", file.absolutePath))
        file.delete()
    }

    @Test
    fun `test init - have files - port null`() {
        val file = serviceDiscoveryFileWithErrors
        initializeMicro(listOf("--config", file.absolutePath))
        file.delete()
    }
}
