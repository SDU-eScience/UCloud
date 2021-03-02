package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.Loggable

private const val DEFAULT_PORT = 8080

class DevelopmentOverrides : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(ConfigurationFeature)
        ctx.developmentModeEnabled = cliArgs.contains("--dev")

        if (!ctx.developmentModeEnabled) return

        val serviceDiscovery =
            ctx.configuration.requestChunkAtOrNull<Map<String, String>>("development", "serviceDiscovery")

        val serviceDiscoveryOverrides = ctx.featureOrNull(ServiceDiscoveryOverrides)
        if (serviceDiscovery != null && serviceDiscoveryOverrides != null) {
            serviceDiscovery
                .forEach { (serviceName, destination) ->
                    val splitValue = destination.split(":")
                    val hostname = splitValue[0].takeIf { it.isNotBlank() } ?: "localhost"
                    val port = if (splitValue.size <= 1) DEFAULT_PORT else splitValue[1].toIntOrNull()
                    if (port == null) {
                        log.info(
                            "Unable to parse destination for $serviceName. " +
                                    "Port was not a valid integer, got: '${splitValue[1]}'"
                        )
                    }

                    serviceDiscoveryOverrides.createOverride(serviceName, port ?: DEFAULT_PORT, hostname)
                }
        }
    }

    companion object Feature : MicroFeatureFactory<DevelopmentOverrides, Unit>,
        Loggable {
        override val key: MicroAttributeKey<DevelopmentOverrides> =
            MicroAttributeKey("development-overrides")
        override val log = logger()

        override fun create(config: Unit): DevelopmentOverrides =
            DevelopmentOverrides()

        internal val ENABLED_KEY = MicroAttributeKey<Boolean>("development-mode")
    }
}

var Micro.developmentModeEnabled: Boolean
    get() {
        return attributes.getOrNull(DevelopmentOverrides.ENABLED_KEY) ?: false
    }
    internal set(value) {
        attributes[DevelopmentOverrides.ENABLED_KEY] = value
    }
