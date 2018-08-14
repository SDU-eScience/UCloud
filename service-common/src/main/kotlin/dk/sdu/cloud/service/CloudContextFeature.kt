package dk.sdu.cloud.service

import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.SDUCloud
import dk.sdu.cloud.client.ServiceDescription

class CloudContextFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        val context = if (ctx.developmentModeEnabled && ctx.featureOrNull(ServiceDiscoveryOverrides) != null) {
            log.info("Using development client")
            DevelopmentServiceClient(
                SDUCloud("https://cloud.sdu.dk"),
                ctx.feature(ServiceDiscoveryOverrides)
            )
        } else {
            if (ctx.developmentModeEnabled) {
                log.warn("Dev mode is enabled but ServiceDiscoverOverrides is not. " +
                        "Cannot use development mode CloudContext")
            }

            log.info("Using direct service client")
            DirectServiceClient()
        }

        ctx.cloudContext = context
    }

    companion object Feature : MicroFeatureFactory<CloudContextFeature, Unit>, Loggable {
        override val key: MicroAttributeKey<CloudContextFeature> = MicroAttributeKey("cloud-context-feature")
        override fun create(config: Unit): CloudContextFeature = CloudContextFeature()

        override val log = logger()

        internal val CTX_KEY = MicroAttributeKey<CloudContext>("cloud-context")
    }
}

var Micro.cloudContext: CloudContext
    get() {
        requireFeature(CloudContextFeature)
        return attributes[CloudContextFeature.CTX_KEY]
    }
    internal set(value) {
        attributes[CloudContextFeature.CTX_KEY] = value
    }