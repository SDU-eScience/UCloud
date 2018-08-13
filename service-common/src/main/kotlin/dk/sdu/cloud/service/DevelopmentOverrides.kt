package dk.sdu.cloud.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.client.ServiceDescription
import java.io.File

internal data class DevelopmentOverride(
    val serviceDiscovery: Map<String, String>?
)

class DevelopmentOverrides: MicroFeature {
    private lateinit var ctx: Micro

    var enabled: Boolean = false
        private set

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        this.ctx = ctx
        var isDevMode = false
        val files = ArrayList<String>()

        val argIterator = cliArgs.iterator()
        while (argIterator.hasNext()) {
            val arg = argIterator.next()

            if (arg == "--dev-file") {
                val fileArgument = if (argIterator.hasNext()) null else argIterator.next()
                if (fileArgument == null) {
                    log.warn("Dangling --dev-file. Usage is: --dev-file <file>")
                } else {
                    files.add(fileArgument)
                }
            } else if (arg == "--dev") {
                isDevMode = true
            }
        }

        enabled = isDevMode

        if (isDevMode) {
            loadFromFile(DEFAULT_DEV_FILE)

            files.forEach {
                loadFromFile(File(it))
            }
        }
    }

    private fun loadFromFile(file: File) {
        if (!file.exists()) {
            log.info("Attempting to load dev configuration from file ${file.absolutePath}, but file does not exist")
            return
        }

        val overrides = try {
            mapper.readValue<DevelopmentOverride>(file)
        } catch (ex: Exception) {
            log.info("Could not parse dev configuration file (${file.absolutePath}). Exception follows:")
            log.info(ex.stackTraceToString())
            return
        }

        val serviceDiscoveryOverrides = ctx.featureOrNull(ServiceDiscoveryOverrides)
        if (overrides.serviceDiscovery != null && serviceDiscoveryOverrides != null) {
            overrides
                .serviceDiscovery
                .forEach { (serviceName, destination) ->
                    val splitValue = destination.split(":")
                    val hostname = splitValue[0].takeIf { it.isNotBlank() } ?: "localhost"
                    val port = if (splitValue.size <= 1) 8080 else splitValue[1].toIntOrNull()
                    if (port == null) {
                        log.info(
                            "Unable to parse destination for $serviceName. " +
                                    "Port was not a valid integer, got: '${splitValue[1]}'"
                        )
                    }

                    serviceDiscoveryOverrides.createOverride(serviceName, port ?: 8080, hostname)
                }
        }
    }

    companion object Feature : MicroFeatureFactory<DevelopmentOverrides, Unit>, Loggable {
        override val key: MicroAttributeKey<DevelopmentOverrides> = MicroAttributeKey("development-overrides")
        override val log = logger()

        override fun create(config: Unit): DevelopmentOverrides = DevelopmentOverrides()

        private val mapper = jacksonObjectMapper()

        private val DEFAULT_DEV_FILE =
            File(
                listOf(
                    System.getProperty("user.home"),
                    ".sdu-cloud",
                    "dev.json"
                ).joinToString(File.pathSeparator)
            )

    }
}
