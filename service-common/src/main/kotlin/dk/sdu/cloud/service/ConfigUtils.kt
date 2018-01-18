package dk.sdu.cloud.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.client.ServiceDescription
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

interface ServerConfiguration {
    val connConfig: ConnectionConfig

    fun configure()
}

inline fun <reified T : ServerConfiguration> readConfigurationBasedOnArgs(
        args: Array<String>,
        serviceDescription: ServiceDescription,
        log: Logger = LoggerFactory.getLogger(T::class.java)
): T {
    log.info("Reading configuration...")
    val configMapper = jacksonObjectMapper()
    val configFilePath = args.getOrNull(0) ?: "/etc/${serviceDescription.name}/config.json"
    val configFile = File(configFilePath)
    log.debug("Using path: $configFilePath. This has resolved to: ${configFile.absolutePath}")
    if (!configFile.exists()) {
        throw IllegalStateException("Unable to find configuration file. Attempted to locate it at: " +
                configFile.absolutePath)
    }

    return configMapper.readValue<T>(configFile).also {
        it.configure()
        log.info("Retrieved the following configuration:")
        log.info(it.toString())
    }
}