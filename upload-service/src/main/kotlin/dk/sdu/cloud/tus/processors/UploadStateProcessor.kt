package dk.sdu.cloud.tus.processors

import dk.sdu.cloud.tus.api.TusUploadEvent
import dk.sdu.cloud.tus.api.internal.UploadEventStream
import dk.sdu.cloud.tus.services.UploadDescriptions
import dk.sdu.cloud.tus.services.UploadProgress
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
