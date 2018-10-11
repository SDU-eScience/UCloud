package dk.sdu.cloud.service

import dk.sdu.cloud.client.ServiceDescription

private const val DEFAULT_PORT = 8080

class ServiceInstanceFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        val port = ctx.featureOrNull(ServiceDiscoveryOverrides)?.get(serviceDescription.name)?.port ?: DEFAULT_PORT

        ctx.serviceInstance = ServiceInstance(
            serviceDescription.definition(),
            queryHostname(),
            port
        )
    }

    private fun queryHostname(): String {
        if (System.getProperty("os.name").startsWith("Windows")) {
            log.debug("Received hostname through COMPUTERNAME env variable")
            return System.getenv("COMPUTERNAME")
        } else {
            val env = System.getenv("HOSTNAME")
            if (env != null) {
                log.debug("Received hostname through HOSTNAME env variable")
                return env
            }

            log.debug("Attempting to retrieve hostname through hostname executable")
            return exec { command("hostname") }.lines().firstOrNull()
                    ?: throw IllegalStateException("Unable to retrieve hostname")
        }
    }

    private fun exec(builder: ProcessBuilder.() -> Unit): String {
        val process = ProcessBuilder().also(builder).start()
        process.waitFor()
        return process.inputStream.reader().readText()
    }

    companion object Feature : MicroFeatureFactory<ServiceInstanceFeature, Unit>, Loggable {
        override val key = MicroAttributeKey<ServiceInstanceFeature>("service-instance-feature")
        override fun create(config: Unit): ServiceInstanceFeature = ServiceInstanceFeature()
        override val log = logger()

        internal val INSTANCE_KEY = MicroAttributeKey<ServiceInstance>("service-instance")
    }
}

var Micro.serviceInstance: ServiceInstance
    get() {
        requireFeature(ServiceInstanceFeature)
        return attributes[ServiceInstanceFeature.INSTANCE_KEY]
    }
    internal set(value) {
        attributes[ServiceInstanceFeature.INSTANCE_KEY] = value
    }
