package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.Loggable

data class ServiceDiscoveryOverride internal constructor(
    val serviceName: String,
    val port: Int,
    val hostname: String = "localhost"
)

class ServiceDiscoveryOverrides : MicroFeature {
    private var port = 8080
    private var devMode = false

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        devMode = ctx.developmentModeEnabled
        val servicePort = ctx.configuration.requestChunkAtOrNull<Int>("servicePort")
        if (servicePort != null) {
            this.port = servicePort
        }
    }

    operator fun get(serviceName: String): ServiceDiscoveryOverride? {
        if (!devMode) return null
        return ServiceDiscoveryOverride(serviceName, port)
    }

    companion object Feature : MicroFeatureFactory<ServiceDiscoveryOverrides, Unit>,
        Loggable {
        override val key =
            MicroAttributeKey<ServiceDiscoveryOverrides>("service-discovery-overrides")
        override val log = logger()
        override fun create(config: Unit): ServiceDiscoveryOverrides =
            ServiceDiscoveryOverrides()
    }
}
