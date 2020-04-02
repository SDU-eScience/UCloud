package dk.sdu.cloud.accounting.storage.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.KnowledgeMode
import dk.sdu.cloud.file.api.VerifyFileKnowledgeRequest
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.SizeRequest
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import org.slf4j.Logger

class StorageAccountingService(
    private val serviceClient: AuthenticatedClient
) {
    suspend fun calculateUsage(user: String, directory: String): Long {
        val hasPermission = FileDescriptions.verifyFileKnowledge.call(
            VerifyFileKnowledgeRequest(
                user,
                listOf(directory),
                KnowledgeMode.Permission(requireWrite = false)
            ),
            serviceClient
        ).orThrow().responses.single()

        if (!hasPermission) throw RPCException("Permission denied", HttpStatusCode.Forbidden)

        return QueryDescriptions.size.call(
            SizeRequest(setOf(directory)),
            serviceClient
        ).orThrow().size
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
