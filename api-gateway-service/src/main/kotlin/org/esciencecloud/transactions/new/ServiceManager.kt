package org.esciencecloud.transactions.new

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import org.esciencecloud.client.RESTDescriptions
import org.esciencecloud.service.KafkaDescriptions
import org.esciencecloud.transactions.util.stackTraceToString
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.jar.JarFile

data class ServiceDefinition(
        val name: String,
        val restDescriptions: List<RESTDescriptions>,
        val kafkaDescriptions: List<KafkaDescriptions>
)


class ServiceManager(vararg val targets: File) {
    companion object {
        private val log = LoggerFactory.getLogger(ServiceManager::class.java)
    }

    fun load(): List<ServiceDefinition> {
        log.info("Reloading services from ${targets.size} targets...")
        val jarFiles = targets.flatMap { it.listFiles { file: File -> file.extension == "jar" }.toList() }

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
                            name = loader.source,
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
        } catch (ex: Exception) {
            log.warn("Unable to create instance of service! Service loaded from: " +
                    source)
            log.warn(ex.stackTraceToString())
            return null
        }
    }
}