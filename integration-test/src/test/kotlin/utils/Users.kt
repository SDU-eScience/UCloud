package dk.sdu.cloud.integration.utils

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.CreateSingleUserRequest
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.integration.adminClient
import dk.sdu.cloud.integration.rpcClient
import dk.sdu.cloud.integration.serviceClient
import java.util.*
import kotlin.random.Random

data class CreatedUser(
    val client: AuthenticatedClient,
    val username: String,
    val password: String,
    val email: String,
    val organization: String?
)

suspend fun createUser(
    username: String = "user-${Random.nextLong()}",
    password: String = UUID.randomUUID().toString(),
    role: Role = Role.USER,
    email: String = "$username@mail",
    organization: String? = null,
): CreatedUser {
    val refreshToken = UserDescriptions.createNewUser.call(
        listOf(
            CreateSingleUserRequest(
                username,
                password,
                email,
                role,
                orgId = organization,
                firstnames = "user",
                lastname = "${Random.nextLong()}"
            )
        ),
        adminClient
    ).orThrow().single().refreshToken

    val client = RefreshingJWTAuthenticator(
        rpcClient,
        JwtRefresher.Normal(refreshToken, OutgoingHttpCall)
    ).authenticateClient(OutgoingHttpCall)

    return CreatedUser(
        client,
        username,
        password,
        email,
        organization
    )
}
