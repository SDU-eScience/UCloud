package dk.sdu.cloud.tus.services

import dk.sdu.cloud.tus.api.TransferState
import dk.sdu.cloud.tus.api.TransferSummary
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class TransferStateService {
    fun retrieveSummary(authenticatedPrincipal: String, id: String): TransferSummary? {
        // TODO Should admins be allowed to do this regardless of who they are?
        return transaction {
            (UploadDescriptions innerJoin UploadProgress)
                    .slice(UploadDescriptions.id, UploadDescriptions.owner, UploadDescriptions.sizeInBytes,
                            UploadProgress.numChunksVerified)
                    .select {
                        (UploadDescriptions.id eq id) and
                                (UploadDescriptions.owner eq authenticatedPrincipal)
                    }
                    .toList()
        }.singleOrNull()?.let {
            TransferSummary(
                    it[UploadDescriptions.id],
                    it[UploadDescriptions.sizeInBytes],
                    it[UploadProgress.numChunksVerified] * RadosStorage.BLOCK_SIZE
            )
        }
    }

    fun retrieveState(authenticatedPrincipal: String, id: String): TransferState? {
        return transaction {
            (UploadDescriptions innerJoin UploadProgress)
                    .select { (UploadDescriptions.id eq id) and (UploadDescriptions.owner eq authenticatedPrincipal) }
                    .toList()
        }.singleOrNull()?.let {
            TransferState(
                    id = it[UploadDescriptions.id],
                    length = it[UploadDescriptions.sizeInBytes],
                    offset = it[UploadProgress.numChunksVerified] * RadosStorage.BLOCK_SIZE,
                    user = it[UploadDescriptions.owner],
                    zone = it[UploadDescriptions.zone],
                    targetCollection = it[UploadDescriptions.targetCollection],
                    targetName = it[UploadDescriptions.targetName]
            )
        }
    }
}

object UploadDescriptions : Table() {
    val id = varchar("id", 36).primaryKey()
    val sizeInBytes = long("size_in_bytes")
    val owner = varchar("owner", 256)
    val zone = varchar("zone", 256)
    val targetCollection = varchar("target_collection", 2048)
    val targetName = varchar("target_name", 1024)
    val doChecksum = bool("do_checksum")
}

object UploadProgress : Table() {
    val id = varchar("id", 36) references UploadDescriptions.id
    val numChunksVerified = long("num_chunks_verified")
}
