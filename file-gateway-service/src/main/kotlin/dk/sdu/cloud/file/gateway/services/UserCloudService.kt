package dk.sdu.cloud.file.gateway.services

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.bearerAuth
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.bearer
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode

class UserCloudService(private val cloudContext: CloudContext) {
    fun createUserCloud(call: ApplicationCall): AuthenticatedCloud {
        val bearer = call.request.bearer ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
        return cloudContext.bearerAuth(bearer)
    }
}
