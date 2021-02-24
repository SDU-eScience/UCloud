package dk.sdu.cloud.accounting.services

import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.project.api.ExistsRequest
import dk.sdu.cloud.project.api.Projects
import io.ktor.http.HttpStatusCode

class VerificationService(
    private val serviceClient: AuthenticatedClient
) {
    suspend fun verify(id: String, type: WalletOwnerType) {
        when (type) {
            WalletOwnerType.USER -> {
                val res = UserDescriptions.lookupUsers.call(
                    LookupUsersRequest(listOf(id)),
                    serviceClient
                ).orThrow()

                if (res.results[id] == null) {
                    throw RPCException("Unknown user", HttpStatusCode.NotFound)
                }
            }

            WalletOwnerType.PROJECT -> {
                val res = Projects.exists.call(
                    ExistsRequest(id),
                    serviceClient
                ).orThrow()

                if (!res.exists) {
                    throw RPCException("Project not found", HttpStatusCode.NotFound)
                }
            }
        }
    }
}
