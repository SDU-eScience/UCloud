package dk.sdu.cloud.transactions

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.service.KafkaDescriptions
import org.slf4j.LoggerFactory
import dk.sdu.cloud.service.stackTraceToString
import java.io.File
import java.util.*
import java.util.jar.JarFile

data class ServiceDefinition(
        val manifest: ServiceManifest,
        val restDescriptions: List<RESTDescriptions>,
        val kafkaDescriptions: List<KafkaDescriptions>
)

interface ServiceManager {
    fun load(): List<ServiceDefinition>
}

class DevelopmentServiceManager(
        private val additionalDefinitions: List<ServiceDefinition>,
        private val delegate: ServiceManager
) : ServiceManager {
    override fun load(): List<ServiceDefinition> {
        return additionalDefinitions + delegate.load()
    }
}

class DefaultServiceManager(vararg val targets: File) : ServiceManager {
    companion object {
        private val log = LoggerFactory.getLogger(ServiceManager::class.java)
    }

    override fun load(): List<ServiceDefinition> {
        log.info("Reloading services from ${targets.size} targets...")
        val jarFiles = targets.flatMap {
            it.listFiles { file: File -> file.extension == "jar" }?.toList() ?: emptyList()
        }

        log.info("Found ${jarFiles.size} services!")
        if (log.isDebugEnabled) {
            log.debug("The following services were loaded:")
            jarFiles.forEach { log.debug("  - ${it.absolutePath} [${Date(it.lastModified())}]") }
        }

        val loaders = jarFiles.map { ServiceClassLoader(JarFile(it, true)) }
        return loaders
                .map { loader ->
                    val allClasses = loader.knownClasses
                            .mapNotNull {
                                try {
                                    loader.loadClass(it)
                                } catch (ex: Exception) {
                                    log.warn("Caught exception while trying to load class: $it")
                                    log.warn(ex.stackTraceToString())
                                    null
                                }
                            }

                    val restDescriptions = allClasses
                            .filter { it.superclass == RESTDescriptions::class.java }
                            .mapNotNull { createInstanceOf(it, loader.source) }
                            .filterIsInstance<RESTDescriptions>()

                    val kafkaDescriptions = allClasses
                            .filter { it.superclass == KafkaDescriptions::class.java }
                            .mapNotNull { createInstanceOf(it, loader.source) }
                            .filterIsInstance<KafkaDescriptions>()

                    ServiceDefinition(
                            manifest = loader.manifest,
                            restDescriptions = restDescriptions,
                            kafkaDescriptions = kafkaDescriptions
                    )
                }
    }

    private fun createInstanceOf(it: Class<*>, source: String): Any? {
        try {
            val noParamConstructor = it.constructors.find { it.parameters.isEmpty() }
            if (noParamConstructor != null) {
                return noParamConstructor.newInstance()
            } else {
                if (it.isKotlinClass()) {
                    val instance = it.kotlin.objectInstance
                    if (instance != null) {
                        return instance
                    }
                }
            }

            log.warn("Found no suitable constructor for service loaded from $source")
            return null
        } catch (ex: NoClassDefFoundError) {
            log.warn("Could not find class while trying to load $it from $source")
            log.warn("Make sure that the API jar is _ONLY_ exporting stuff available from within the API")
            log.warn("The class we could not find was: ${ex.message}")
            log.warn(ex.stackTraceToString())
            return null
        } catch (ex: Exception) {
            log.warn("Unable to create instance of service! Service loaded from: " +
                    source)
            log.warn(ex.stackTraceToString())
            return null
        }
    }
}