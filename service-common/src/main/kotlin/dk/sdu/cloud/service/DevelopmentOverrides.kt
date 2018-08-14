package dk.sdu.cloud.service

import dk.sdu.cloud.client.ServiceDescription

class DevelopmentOverrides : MicroFeature {
    private lateinit var ctx: Micro

    var enabled: Boolean = false
        private set

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        this.ctx = ctx

        ctx.requireFeature(ConfigurationFeature)
        enabled = cliArgs.contains("--dev")

        if (enabled) {
            val serviceDiscovery =
                ctx.configuration.requestChunkAtOrNull<Map<String, String>>("development", "serviceDiscovery")

            val serviceDiscoveryOverrides = ctx.featureOrNull(ServiceDiscoveryOverrides)
            if (serviceDiscovery != null && serviceDiscoveryOverrides != null) {
                serviceDiscovery
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
    }

    companion object Feature : MicroFeatureFactory<DevelopmentOverrides, Unit>, Loggable {
        override val key: MicroAttributeKey<DevelopmentOverrides> = MicroAttributeKey("development-overrides")
        override val log = logger()

        override fun create(config: Unit): DevelopmentOverrides = DevelopmentOverrides()
    }
}
