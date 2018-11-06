package dk.sdu.cloud.service.test

import dk.sdu.cloud.client.ServiceDescription
import dk.sdu.cloud.service.CloudContextFeature
import dk.sdu.cloud.service.CloudFeature
import dk.sdu.cloud.service.ConfigurationFeature
import dk.sdu.cloud.service.DevelopmentOverrides
import dk.sdu.cloud.service.KafkaFeature
import dk.sdu.cloud.service.KafkaFeatureConfiguration
import dk.sdu.cloud.service.KafkaTopicFeature
import dk.sdu.cloud.service.KafkaTopicFeatureConfiguration
import dk.sdu.cloud.service.KtorServerProviderFeature
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.ScriptFeature
import dk.sdu.cloud.service.ServiceDiscoveryOverrides
import dk.sdu.cloud.service.ServiceInstanceFeature
import dk.sdu.cloud.service.TokenValidationFeature
import dk.sdu.cloud.service.install
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
        it.writeText("""
          ---
          hibernate:
            database:
              profile: TEST_H2
              logSql: true
        """.trimIndent())
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
        install(KtorServerProviderFeature)
        install(KafkaFeature, KafkaFeatureConfiguration(kafkaServicesOverride = KafkaMock.initialize()))
        install(CloudContextFeature)
        install(CloudFeature)
        install(TokenValidationFeature)
        install(ServiceInstanceFeature)

        feature(CloudFeature).addAuthenticatedCloud(Int.MAX_VALUE, CloudMock.also { it.initialize() })
    }
}
