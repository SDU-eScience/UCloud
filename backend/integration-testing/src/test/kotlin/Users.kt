package dk.sdu.cloud.integration

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.CreateSingleUserRequest
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.TokenValidationJWT
import java.util.*
import kotlin.random.Random

suspend fun createUser(
    username: String = "user-${Random.nextLong()}",
    role: Role = Role.USER
): Pair<AuthenticatedClient, String> {
    val refreshToken = UserDescriptions.createNewUser.call(
        listOf(
            CreateSingleUserRequest(username, UUID.randomUUID().toString(), "$username@mail", role)
        ),
        serviceClient
    ).orThrow().single().refreshToken

    return Pair(
        RefreshingJWTAuthenticator(
            serviceClient.client,
            refreshToken,
            UCloudLauncher.micro.tokenValidation as TokenValidationJWT
        ).authenticateClient(OutgoingHttpCall),
        username
    )
}