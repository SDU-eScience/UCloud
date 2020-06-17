package dk.sdu.cloud.file.favorite.migration

import dk.sdu.cloud.calls.call
import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

/**
 * @see [LookupDescriptions.reverseLookup]
 */
private data class ReverseLookupRequest(val fileId: String) {
    constructor(fileIds: List<String>) : this(fileIds.joinToString(","))

    @get:JsonIgnore
    val allIds: List<String>
        get() = fileId.split(",")
}

/**
 * @see [LookupDescriptions.reverseLookup]
 */
private data class ReverseLookupResponse(val canonicalPath: List<String?>)

/**
 * Provides REST calls for looking up files in the efficient file index.
 */
private object LookupDescriptions : CallDescriptionContainer("indexing") {
    const val baseContext: String = "/api/indexing/lookup"

    val reverseLookup = call<ReverseLookupRequest, ReverseLookupResponse, CommonErrorMessage>("reverseLookup") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"reverse"
            }

            params {
                +boundTo(ReverseLookupRequest::fileId)
            }
        }
    }
}

class MetadataMigration(
    private val micro: Micro,
    private val serviceClient: AuthenticatedClient
) {
    private val db = AsyncDBSessionFactory(micro.databaseConfig)

    suspend fun runDataMigration() {
        data class FileFavorite(val fileId: String, val username: String)
        val fileFavorites = ArrayList<FileFavorite>()

        db.withTransaction { session ->
            session.sendQuery("declare c no scroll cursor for select file_id, username from file_favorite.favorites")

            while (true) {
                val rows = session.sendQuery("fetch forward 100 from c").rows
                rows.forEach { row ->
                    val fileId = row.getString(0)!!
                    val username = row.getString(1)!!
                    val element = FileFavorite(fileId, username)
                    log.debug("Next row: $element")
                    fileFavorites.add(element)
                }

                if (rows.isEmpty()) break
            }
        }

        val allFileIds = fileFavorites.asSequence().map { it.fileId }.toSet()
        val fileIdToPath = HashMap<String, String>()
        allFileIds.chunked(100).forEach { chunkSet ->
            val fileIds = chunkSet.toList()
            val paths = LookupDescriptions.reverseLookup.call(
                ReverseLookupRequest(fileIds),
                serviceClient
            ).orThrow().canonicalPath
            for (i in fileIds.indices) {
                val fileId = fileIds[i]
                val path = paths[i]
                if (path != null) {
                    fileIdToPath[fileId] = path
                }
            }
        }

        fileFavorites.forEach { (fileId, username) ->
            val path = fileIdToPath[fileId] ?: return@forEach
            if (!path.contains("\n") && !username.contains("\n")) {
                println(path)
                println(username)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
