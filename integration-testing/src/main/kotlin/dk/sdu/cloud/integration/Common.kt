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

data class Configuration(val userA: User, val userB: User)

data class User(val username: String, val refreshToken: String) {
    init {
        require(username.isNotEmpty())
        require(refreshToken.isNotEmpty())
    }
}
