package dk.sdu.cloud.tus.processors

import dk.sdu.cloud.tus.api.TusUploadEvent
import dk.sdu.cloud.tus.api.internal.UploadEventStream
import dk.sdu.cloud.tus.services.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.net.URI

private val log = LoggerFactory.getLogger(UploadStateProcessor::class.java)

class UploadStateProcessor(
        private val uploadEventStream: UploadEventStream,
        private val transferStateService: TransferStateService,
        private val icat: ICAT
) {
    fun init() {
        log.info("Attaching upload state processor")

        uploadEventStream.foreach { _, event ->
            log.info("Handling upload event: $event")

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
                    transaction {
                        UploadProgress.update({ UploadProgress.id eq event.id }, limit = 1) {
                            it[numChunksVerified] = event.chunk
                        }
                    }

                    if (event.chunk == event.numChunks) {
                        val state = transferStateService.retrieveState(event.id)!!
                        val irodsUser = state.user
                        val irodsZone = state.zone

                        val uri = URI(state.targetCollection)
                        val irodsCollection = "/${uri.host}${uri.path}"
                        val irodsFileName = state.targetName

                        // Finalize upload
                        log.debug("Upload ${state.id} has been completed!")
                        icat.useConnection {
                            autoCommit = false
                            log.debug("Registration of object...")

                            val resource = findResourceByNameAndZone("child_01", "tempZone") ?: return@useConnection
                            log.debug("Using resource $resource. $irodsUser, $irodsZone, $irodsCollection")

                            val entry = findAccessRightForUserInCollection(irodsUser, irodsZone, irodsCollection)
                            log.debug("ACL Entry: $entry")

                            // TODO This is really primitive and even worse, potentially wrong
                            if (entry != null && (entry.accessType == 1200L || entry.accessType == 1120L)) {
                                val objectId = registerDataObject(
                                        entry.objectId,
                                        state.id,
                                        state.length,
                                        irodsFileName,
                                        irodsUser,
                                        irodsZone,
                                        resource
                                ) ?: return@useConnection run {
                                    log.warn("Was unable to register data object! Event was: $event")
                                    rollback()
                                }

                                log.debug("Auto-generated ID is $objectId")
                                val now = System.currentTimeMillis()

                                registerAccessEntry(ICATAccessEntry(objectId, entry.userId, 1200, now, now))
                                commit()

                                log.info("Object has been registered: $event")
                            }
                        }
                    }
                }
            }
        }
    }
}
