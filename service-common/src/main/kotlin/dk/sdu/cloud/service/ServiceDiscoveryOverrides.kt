package dk.sdu.cloud.service

import dk.sdu.cloud.client.ServiceDescription

data class ServiceDiscoveryOverride internal constructor(
    val serviceName: String,
    val port: Int,
    val hostname: String = "localhost"
)

class ServiceDiscoveryOverrides : MicroFeature {
    private val overrides = HashMap<String, ServiceDiscoveryOverride>()

    fun createOverride(serviceName: String, port: Int, hostname: String = "localhost") {
        overrides[serviceName] = ServiceDiscoveryOverride(serviceName, port, hostname)
    }

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {}

    operator fun get(serviceName: String): ServiceDiscoveryOverride? = overrides[serviceName]

    companion object Feature : MicroFeatureFactory<ServiceDiscoveryOverrides, Unit>, Loggable {
        override val key = MicroAttributeKey<ServiceDiscoveryOverrides>("service-discovery-overrides")
        override val log = logger()
        override fun create(config: Unit): ServiceDiscoveryOverrides = ServiceDiscoveryOverrides()
    }
}