package dk.sdu.cloud.auth.api

import dk.sdu.cloud.client.ServiceDescription
import dk.sdu.cloud.service.*

class RefreshingJWTCloudFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        val cloudContext = ctx.cloudContext
        val refreshToken = ctx.configuration.requestChunkAt<String>("refreshToken")

        ctx.refreshingJwtCloud = RefreshingJWTAuthenticatedCloud(cloudContext, refreshToken)
    }

    companion object Feature : MicroFeatureFactory<RefreshingJWTCloudFeature, Unit> {
        override val key = MicroAttributeKey<RefreshingJWTCloudFeature>("refreshing-jwt-cloud-feature")
        override fun create(config: Unit): RefreshingJWTCloudFeature = RefreshingJWTCloudFeature()

        internal val REFRESHING_CLOUD_KEY = MicroAttributeKey<RefreshingJWTAuthenticatedCloud>("refreshing-cloud")
    }
}

var Micro.refreshingJwtCloud: RefreshingJWTAuthenticatedCloud
    get() {
        requireFeature(RefreshingJWTCloudFeature)
        return attributes[RefreshingJWTCloudFeature.REFRESHING_CLOUD_KEY]
    }

    set(value) {
        attributes[RefreshingJWTCloudFeature.REFRESHING_CLOUD_KEY] = value
    }