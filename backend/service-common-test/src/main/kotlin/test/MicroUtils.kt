package dk.sdu.cloud.service.test

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.*
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

private val dbConfig by lazy {
    Files.createTempFile("config", ".yml").toFile().also {
        // language=yaml
        it.writeText(
            """
          ---
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
 * @see ClientMock
 * @see EventServiceMock
 * @see TokenValidationMock
 */
fun initializeMicro(additionalArgs: List<String> = emptyList()): Micro {
    val serviceDescription = object : ServiceDescription {
        override val name: String = "test"
        override val version: String = "1.0.0"
    }

    // This function is required to initialize all default features
    return Micro().apply {
        val configFiles = listOf(tokenValidationConfig, dbConfig)
        val configArgs = configFiles.flatMap { listOf("--config", it.absolutePath) }

        init(serviceDescription, (listOf("--dev") + configArgs + additionalArgs).toTypedArray())

        install(DeinitFeature)
        install(ScriptFeature)
        install(ConfigurationFeature)
        install(ServiceDiscoveryOverrides)
        install(DevelopmentOverrides) // Always activated
        install(LogFeature)
        install(DatabaseConfigurationFeature)

        attributes[KtorServerProviderFeature.serverProviderKey] = { module ->
            val engine = TestApplicationEngine()
            engine.start()
            engine.application.module()
            engine
        }

        EventServiceMock.reset()
        eventStreamService = EventServiceMock
        install(ClientFeature)
        install(TokenValidationFeature)
        install(ServiceInstanceFeature)
        install(ServerFeature)
    }
}
