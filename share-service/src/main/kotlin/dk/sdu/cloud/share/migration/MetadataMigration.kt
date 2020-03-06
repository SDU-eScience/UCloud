package dk.sdu.cloud.share.migration

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.delay
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.io.File

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
            roles = Roles.PRIVILEDGED
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
    private val db: AsyncDBSessionFactory,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun runDataMigration() {
        data class ShareRow(
            val owner: String,
            val sharedWith: String,
            val state: Int,
            val rights: Int,
            val ownerToken: String,
            val recipientToken: String?,
            val createdAt: Long,
            val modifiedAt: Long,
            val path: String?,
            val fileId: String
        )

        val shares = ArrayList<ShareRow>()

        db.withTransaction { session ->
            session.sendQuery(
                """
                    declare c no scroll cursor for 
                        select owner, shared_with, state, rights, owner_token, recipient_token, created_at, 
                               modified_at, path, file_id
                        from share.shares
                """
            )

            while (true) {
                val rows = session.sendQuery("fetch forward 100 from c").rows
                rows.forEach { row ->
                    shares.add(
                        ShareRow(
                            row.getString(0)!!,
                            row.getString(1)!!,
                            row.getInt(2)!!,
                            row.getInt(3)!!,
                            row.getString(4)!!,
                            row.getString(5),
                            (row[6]!! as LocalDateTime).toTimestamp(),
                            (row[7]!! as LocalDateTime).toTimestamp(),
                            row.getString(8),
                            row.getString(9)!!
                        )
                    )
                }

                if (rows.isEmpty()) break
            }
        }

        val allFileIds = shares.asSequence().map { it.fileId }.toSet()
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

        val file = File("/tmp/output.json")
        file.writeText(
            defaultMapper.writeValueAsString(shares.map { share ->
                val path = fileIdToPath[share.fileId] ?: share.path
                share.copy(path = path)
            })
        )

        log.info("Job complete. File can be found at /tmp/output.json. This program won't terminate while it exists.")
        while (file.exists()) {
            delay(1000L)
        }
    }

    fun LocalDateTime.toTimestamp(): Long {
        return toDateTime(DateTimeZone.UTC).millis
    }

    companion object : Loggable {
        override val log = logger()
    }
}
