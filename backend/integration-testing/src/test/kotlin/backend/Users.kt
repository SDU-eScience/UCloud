package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.CreateSingleUserRequest
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.file.api.homeDirectory
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.TokenValidationJWT
import java.util.*
import kotlin.random.Random

data class CreatedUser(
    val client: AuthenticatedClient,
    val username: String,
    val password: String,
    val email: String
)

suspend fun createUser(
    username: String = "user-${Random.nextLong()}",
    password: String = UUID.randomUUID().toString(),
    role: Role = Role.USER,
    email: String = "$username@mail",
    waitForStorage: Boolean = true
): CreatedUser {
    val refreshToken = UserDescriptions.createNewUser.call(
        listOf(
            CreateSingleUserRequest(username, password, email, role)
        ),
        serviceClient
    ).orThrow().single().refreshToken

    val client = RefreshingJWTAuthenticator(
        serviceClient.client,
        refreshToken,
        UCloudLauncher.micro.tokenValidation as TokenValidationJWT
    ).authenticateClient(OutgoingHttpCall)

    if (waitForStorage) {
        waitForFile(homeDirectory(username), client)
    }

    return CreatedUser(
        client,
        username,
        password,
        email
    )
}
