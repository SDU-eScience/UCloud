package dk.sdu.cloud.auth.api

import dk.sdu.cloud.client.ServiceDescription
import dk.sdu.cloud.service.CloudFeature
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.MicroAttributeKey
import dk.sdu.cloud.service.MicroFeature
import dk.sdu.cloud.service.MicroFeatureFactory
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.cloudContext
import dk.sdu.cloud.service.configuration
import dk.sdu.cloud.service.tokenValidation

class RefreshingJWTCloudFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        val cloudFeature = ctx.feature(CloudFeature)
        val cloudContext = ctx.cloudContext
        val refreshToken = ctx.configuration.requestChunkAt<String>("refreshToken")

        val tokenValidation = ctx.tokenValidation as? TokenValidationJWT ?:
            throw IllegalStateException("Token validation needs to use JWTs!")

        val authenticatedCloud = RefreshingJWTAuthenticatedCloud(cloudContext, refreshToken, tokenValidation)
        cloudFeature.addAuthenticatedCloud(100, authenticatedCloud)
        ctx.refreshingJwtCloud = authenticatedCloud
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
