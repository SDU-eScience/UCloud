package dk.sdu.cloud.file.migration

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.services.acl.MetadataDao
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.delay
import java.io.File
import dk.sdu.cloud.file.services.acl.Metadata as UMetadata

private data class InternalShare(
    val sharedBy: String,
    val sharedWith: String,
    val state: ShareState,
    val rights: Set<AccessRight>,
    val ownerToken: String,
    val recipientToken: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)

private enum class ShareState {
    REQUEST_SENT,
    UPDATING,
    ACCEPTED
}

class ImportShares(
    private val db: AsyncDBSessionFactory,
    private val metadata: MetadataDao
) {
    suspend fun runMigration() {
        val inputFile = File("/tmp/input.json")
        log.info("Waiting for input file at ${inputFile.absolutePath}")
        while (!inputFile.exists()) {
            delay(100)
        }

        log.info("Found file!")
        val parsedShares = defaultMapper.readValue<List<Pair<String, InternalShare>>>(inputFile)

        db.withTransaction { session ->
            parsedShares.forEach { (path, share) ->
                metadata.createMetadata(
                    session,
                    UMetadata(path, "share", share.sharedWith, defaultMapper.valueToTree(share))
                )
            }
        }

        log.info("Shares successfully migrated")
    }

    companion object : Loggable {
        override val log = logger()
    }
}
