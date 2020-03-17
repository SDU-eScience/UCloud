package dk.sdu.cloud.file.stats.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.indexing.api.*
import java.io.File

class DirectorySizeService(
    private val serviceCloud: AuthenticatedClient
) {
    suspend fun fetchDirectorySizes(
        paths: List<String>,
        username: String
    ): Long {
        return QueryDescriptions.size.call(
            SizeRequest(paths.map { it.normalize() }.filter { it.startsWith("/home/$username/") }.toSet()),
            serviceCloud
        ).orThrow().size
    }
}
