package dk.sdu.cloud.integration

import dk.sdu.cloud.auth.api.CreateSingleUserResponse
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.integration.testing.api.IntegrationTestingServiceDescription
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.cloudContext
import dk.sdu.cloud.service.configuration
import dk.sdu.cloud.service.initWithDefaultFeatures
import dk.sdu.cloud.service.tokenValidation

data class IntegrationTestConfiguration(
    val adminRefreshToken: String
)

val micro = Micro().apply {
    initWithDefaultFeatures(
        IntegrationTestingServiceDescription, arrayOf(
            "--dev",
            "--config-dir",
            "${System.getProperty("user.home")}/sducloud"
        )
    )
}

val cloudContext = micro.cloudContext
val tokenValidation = micro.tokenValidation as TokenValidationJWT

val config = micro.configuration.requestChunkAt<IntegrationTestConfiguration>("test", "integration")
val adminCloud = RefreshingJWTAuthenticatedCloud(
    cloudContext,
    config.adminRefreshToken,
    tokenValidation
)

fun CreateSingleUserResponse.cloud(): AuthenticatedCloud =
    RefreshingJWTAuthenticatedCloud(cloudContext, refreshToken, tokenValidation)
