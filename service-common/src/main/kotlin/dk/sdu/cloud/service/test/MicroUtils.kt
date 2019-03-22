package dk.sdu.cloud.service.test

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.ClientFeature
import dk.sdu.cloud.micro.ConfigurationFeature
import dk.sdu.cloud.micro.DevelopmentOverrides
import dk.sdu.cloud.micro.KafkaFeature
import dk.sdu.cloud.micro.KafkaFeatureConfiguration
import dk.sdu.cloud.micro.KtorServerProviderFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ScriptFeature
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.ServiceDiscoveryOverrides
import dk.sdu.cloud.micro.ServiceInstanceFeature
import dk.sdu.cloud.micro.TokenValidationFeature
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.install
import io.ktor.server.testing.TestApplicationEngine
import java.nio.file.Files

private val tokenValidationConfig by lazy {
    Files.createTempFile("config", ".yml").toFile().also {
        // language=yaml
        it.writeText(
            """
                ---
                tokenValidation:
                  jwt:
                    sharedSecret: ${TokenValidationMock.sharedSecret}
            """.trimIndent()
        )
    }
}

private val databaseConfig by lazy {
    Files.createTempFile("config", ".yml").toFile().also {
        // language=yaml
        it.writeText(
            """
          ---
          hibernate:
            database:
              profile: TEST_H2
              logSql: true
        """.trimIndent()
        )
    }
}

/**
 * Initializes Micro with all default features. Certain services are mocked.
 *
 * @see KafkaMock
 * @see TokenValidationMock
 */
fun initializeMicro(additionalArgs: List<String> = emptyList()): Micro {
    val serviceDescription = object : ServiceDescription {
        override val name: String = "test"
        override val version: String = "1.0.0"
    }

    // This function is required to initialize all default features
    return Micro().apply {
        val configFiles = listOf(tokenValidationConfig, databaseConfig)
        val configArgs = configFiles.flatMap { listOf("--config", it.absolutePath) }

        init(serviceDescription, (listOf("--dev") + configArgs + additionalArgs).toTypedArray())

        install(ScriptFeature)
        install(ConfigurationFeature)
        install(ServiceDiscoveryOverrides)
        install(DevelopmentOverrides) // Always activated
//        install(KtorServerProviderFeature)

        attributes[KtorServerProviderFeature.serverProviderKey] = { module ->
            val engine = TestApplicationEngine()
            engine.start()
            engine.application.module()
            engine
        }

        install(
            KafkaFeature,
            KafkaFeatureConfiguration(kafkaServicesOverride = KafkaMock.initialize())
        )
        EventServiceMock.reset()
        eventStreamService = EventServiceMock
        install(ClientFeature)
        install(TokenValidationFeature)
        install(ServiceInstanceFeature)
        install(ServerFeature)
    }
}
