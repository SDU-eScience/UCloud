package dk.sdu.cloud.file.stats.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.indexing.api.*
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import org.slf4j.Logger
import kotlin.math.roundToLong

class UsageService(private val serviceCloud: AuthenticatedClient) {
    suspend fun calculateUsage(directory: String, owner: String): Long {
        val normalizedDirectory = directory.normalize()
        if (normalizedDirectory != "/home/$owner" && !normalizedDirectory.startsWith("/home/$owner/")) {
            throw RPCException("Not found", HttpStatusCode.NotFound)
        }

        return QueryDescriptions.size.call(SizeRequest(setOf(normalizedDirectory)), serviceCloud).orThrow().size
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
