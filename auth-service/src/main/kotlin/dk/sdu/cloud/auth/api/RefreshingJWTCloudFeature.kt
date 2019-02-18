package dk.sdu.cloud.auth.api

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.MicroAttributeKey
import dk.sdu.cloud.micro.MicroFeature
import dk.sdu.cloud.micro.MicroFeatureFactory
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.TokenValidationJWT

class RefreshingJWTCloudFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        val cloudContext = ctx.client
        val refreshToken = ctx.configuration.requestChunkAt<String>("refreshToken")

        val tokenValidation = ctx.tokenValidation as? TokenValidationJWT
            ?: throw IllegalStateException("Token validation needs to use JWTs!")

        val authenticatedCloud = RefreshingJWTAuthenticator(cloudContext, refreshToken, tokenValidation)
        ctx.authenticator = authenticatedCloud
    }

    companion object Feature : MicroFeatureFactory<RefreshingJWTCloudFeature, Unit> {
        override val key = MicroAttributeKey<RefreshingJWTCloudFeature>("refreshing-jwt-cloud-feature")
        override fun create(config: Unit): RefreshingJWTCloudFeature = RefreshingJWTCloudFeature()

        internal val REFRESHING_CLOUD_KEY = MicroAttributeKey<RefreshingJWTAuthenticator>("refreshing-cloud")
    }
}

var Micro.authenticator: RefreshingJWTAuthenticator
    get() {
        requireFeature(RefreshingJWTCloudFeature)
        return attributes[RefreshingJWTCloudFeature.REFRESHING_CLOUD_KEY]
    }
    set(value) {
        attributes[RefreshingJWTCloudFeature.REFRESHING_CLOUD_KEY] = value
    }
