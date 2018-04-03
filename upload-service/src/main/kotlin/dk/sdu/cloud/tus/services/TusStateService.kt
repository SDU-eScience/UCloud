package dk.sdu.cloud.tus.services

import dk.sdu.cloud.tus.api.UploadState
import dk.sdu.cloud.tus.api.UploadSummary
import dk.sdu.cloud.tus.services.FileUpload.Companion.BLOCK_SIZE
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

class TusStateService {
    fun retrieveSummary(
        id: String,
        authenticatedPrincipal: String? = null,
        allowRetries: Boolean = true
    ): UploadSummary? {
        return withRetries(allowRetries) {
            transaction {
                (UploadDescriptions innerJoin UploadProgress)
                    .slice(
                        UploadDescriptions.id, UploadDescriptions.owner, UploadDescriptions.sizeInBytes,
                        UploadProgress.numChunksVerified, UploadDescriptions.savedAs
                    )
                    .select {
                        var q = (UploadDescriptions.id eq id)
                        if (authenticatedPrincipal != null) {
                            q = q and (UploadDescriptions.owner eq authenticatedPrincipal)
                        }
                        return@select q
                    }
                    .toList()
            }.singleOrNull()?.let {
                val sizeInBytes = it[UploadDescriptions.sizeInBytes]
                val numChunks = Math.ceil(sizeInBytes / BLOCK_SIZE.toDouble()).toLong()
                val chunksVerified = it[UploadProgress.numChunksVerified]
                val savedAs = it[UploadDescriptions.savedAs]
                val offset = if (numChunks == chunksVerified) sizeInBytes else chunksVerified * BLOCK_SIZE

                UploadSummary(it[UploadDescriptions.id], sizeInBytes, offset, savedAs)
            }
        }
    }

    fun retrieveState(
        id: String,
        authenticatedPrincipal: String? = null,
        allowRetries: Boolean = true
    ): UploadState? {
        return withRetries(allowRetries) {
            transaction {
                (UploadDescriptions innerJoin UploadProgress)
                    .select {
                        var q = (UploadDescriptions.id eq id)
                        if (authenticatedPrincipal != null) {
                            q = q and (UploadDescriptions.owner eq authenticatedPrincipal)
                        }

                        return@select q
                    }
                    .toList()
            }.singleOrNull()?.let {
                val sizeInBytes = it[UploadDescriptions.sizeInBytes]
                val numChunks = Math.ceil(sizeInBytes / BLOCK_SIZE.toDouble()).toLong()
                val chunksVerified = it[UploadProgress.numChunksVerified]
                val offset = if (numChunks == chunksVerified) sizeInBytes else chunksVerified * BLOCK_SIZE

                UploadState(
                    id = it[UploadDescriptions.id],
                    length = sizeInBytes,
                    offset = offset,
                    user = it[UploadDescriptions.owner],
                    zone = it[UploadDescriptions.zone],
                    targetCollection = it[UploadDescriptions.targetCollection],
                    targetName = it[UploadDescriptions.targetName]
                )
            }
        }
    }

    private inline fun <T> withRetries(allowRetries: Boolean, maxTries: Int = 3, body: () -> T): T? {
        var retriesLeft = if (allowRetries) maxTries else 1
        while (retriesLeft > 0) {
            val result = body()

            if (result != null) {
                return result
            } else {
                log.debug("Retrying: $retriesLeft")
                Thread.sleep(150 * (maxTries - retriesLeft).toLong())
            }

            retriesLeft--
        }
        log.debug("Returning null")
        return null
    }

    companion object {
        private val log = LoggerFactory.getLogger(TusStateService::class.java)
    }
}

object UploadDescriptions : Table() {
    val id = varchar("id", 36).primaryKey()
    val sizeInBytes = long("size_in_bytes")
    val owner = varchar("owner", 256)
    val zone = varchar("zone", 256)
    val targetCollection = varchar("target_collection", 2048)
    val targetName = varchar("target_name", 1024)
    val savedAs = text("saved_as").nullable()
    val doChecksum = bool("do_checksum")
    val sensitive = bool("sensitive")
}

object UploadProgress : Table() {
    val id = reference("id", UploadDescriptions.id)
    val numChunksVerified = long("num_chunks_verified")
}
