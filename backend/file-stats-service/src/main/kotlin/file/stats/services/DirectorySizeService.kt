package dk.sdu.cloud.file.stats.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.KnowledgeMode
import dk.sdu.cloud.file.api.VerifyFileKnowledgeRequest
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.indexing.api.*
import java.io.File

class DirectorySizeService(
    private val serviceClient: AuthenticatedClient
) {
    suspend fun fetchDirectorySizes(
        paths: List<String>,
        username: String
    ): Long {
        val allowedPaths = HashSet<String>()
        val pathsToCheck = ArrayList<String>()
        for (path in paths) {
            val normalized = path.normalize()
            if (normalized.startsWith("/home/$username/")) {
                allowedPaths.add(normalized)
            } else {
                pathsToCheck.add(normalized)
            }
        }

        val hasReadPermission = FileDescriptions.verifyFileKnowledge.call(
            VerifyFileKnowledgeRequest(
                username,
                pathsToCheck,
                KnowledgeMode.Permission(requireWrite = false)
            ),
            serviceClient
        ).orThrow().responses

        allowedPaths.addAll(pathsToCheck.filterIndexed { index, _ -> hasReadPermission.getOrNull(index) == true })

        return QueryDescriptions.size.call(
            SizeRequest(allowedPaths),
            serviceClient
        ).orThrow().size
    }
}
