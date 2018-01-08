package dk.sdu.cloud.tus

import dk.sdu.cloud.tus.api.TusUploadEvent
import dk.sdu.cloud.tus.api.UploadEventStream
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(UploadStateProcessor::class.java)

class UploadStateProcessor(
        private val uploadEventStream: UploadEventStream
) {
    fun init() {
        log.info("Attaching upload state processor")

        uploadEventStream.foreach { _, event ->
            when (event) {
                is TusUploadEvent.Created -> {
                    transaction {
                        UploadDescriptions.insert {
                            it[id] = event.id
                            it[sizeInBytes] = event.sizeInBytes
                            it[owner] = event.owner
                            it[zone] = event.zone
                            it[targetCollection] = event.targetCollection
                            it[targetName] = event.targetName
                            it[doChecksum] = event.doChecksum
                        }

                        UploadProgress.insert {
                            it[id] = event.id
                            it[numChunksVerified] = 0
                        }
                    }
                }

                is TusUploadEvent.ChunkVerified -> {
                    UploadProgress.update({ UploadProgress.id eq event.id }, limit = 1) {
                        it[numChunksVerified] = event.chunk
                    }
                }
            }
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