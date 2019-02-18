package dk.sdu.cloud.file.gateway.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.bearer
import io.ktor.application.call
import io.ktor.http.HttpStatusCode

class UserCloudService(private val cloud: AuthenticatedClient) {
    fun createUserCloud(ctx: HttpCall): AuthenticatedClient {
        val bearer = ctx.call.request.bearer ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
        return cloud.withoutAuthentication().bearerAuth(bearer)
    }
}
