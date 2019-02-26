package dk.sdu.cloud.integration

import dk.sdu.cloud.auth.api.CreateSingleUserResponse
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.integration.testing.api.IntegrationTestingServiceDescription
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.TokenValidationJWT

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

val tokenValidation = micro.tokenValidation as TokenValidationJWT

val config = micro.configuration.requestChunkAt<IntegrationTestConfiguration>("test", "integration")
val adminClient = RefreshingJWTAuthenticator(
    micro.client,
    config.adminRefreshToken,
    tokenValidation
).authenticateClient(OutgoingHttpCall)

fun CreateSingleUserResponse.client(): AuthenticatedClient =
    RefreshingJWTAuthenticator(micro.client, refreshToken, tokenValidation).authenticateClient(OutgoingHttpCall)

