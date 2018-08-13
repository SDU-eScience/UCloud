package dk.sdu.cloud.service

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import dk.sdu.cloud.client.ServiceDescription
import java.io.File

interface ServerConfiguration {
    val connConfig: ConnectionConfig

    fun configure()
}

data class ConfigurationFeatureConfig<T : ServerConfiguration>(
    val configTypeReference: TypeReference<T>
)

@Suppress("FunctionName")
inline fun <reified T : ServerConfiguration> ConfigurationFeatureConfig(): ConfigurationFeatureConfig<T> {
    return ConfigurationFeatureConfig(jacksonTypeRef())
}

class ConfigurationFeature(private val featureConfig: ConfigurationFeatureConfig<*>) : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        log.info("Reading configuration...")
        val configMapper = jacksonObjectMapper().apply {
            configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            configure(JsonParser.Feature.ALLOW_MISSING_VALUES, true)
            configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
        }

        val configFilePath =
            cliArgs.getOrNull(0)?.takeIf { !it.startsWith("--") } ?: "/etc/${serviceDescription.name}/config.json"
        val configFile = File(configFilePath)
        log.debug("Using path: $configFilePath. This has resolved to: ${configFile.absolutePath}")

        if (!configFile.exists()) {
            throw IllegalStateException(
                "Unable to find configuration file. Attempted to locate it at: " +
                        configFile.absolutePath
            )
        }

        val readConfig =
            configMapper.readValue<ServerConfiguration>(configFile, featureConfig.configTypeReference).also {
                it.configure()
                log.info("Retrieved the following configuration:")
                log.info(it.toString())
            }

        ctx.configuration = readConfig
    }

    companion object Feature : MicroFeatureFactory<ConfigurationFeature, ConfigurationFeatureConfig<*>>, Loggable {
        override val key: MicroAttributeKey<ConfigurationFeature> = MicroAttributeKey("configuration-feature")
        override fun create(config: ConfigurationFeatureConfig<*>): ConfigurationFeature = ConfigurationFeature(config)

        override val log = logger()

        internal val CONFIG_KEY = MicroAttributeKey<ServerConfiguration>("server-configuration")
    }
}

var Micro.configuration: ServerConfiguration
    get() = attributes[ConfigurationFeature.CONFIG_KEY]
    internal set(value) {
        attributes[ConfigurationFeature.CONFIG_KEY] = value
    }

