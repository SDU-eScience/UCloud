package dk.sdu.cloud.tus.processors

import dk.sdu.cloud.tus.api.TusUploadEvent
import dk.sdu.cloud.tus.api.internal.UploadEventStream
import dk.sdu.cloud.tus.services.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import org.slf4j.LoggerFactory

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
                    try {
                        transaction {
                            UploadDescriptions.insert {
                                it[id] = event.id
                                it[sizeInBytes] = event.sizeInBytes
                                it[owner] = event.owner
                                it[zone] = event.zone
                                it[targetCollection] = event.targetCollection
                                it[targetName] = event.targetName
                                it[doChecksum] = event.doChecksum
                                it[sensitive] = event.sensitive
                            }

                            UploadProgress.insert {
                                it[id] = event.id
                                it[numChunksVerified] = 0
                            }
                        }
                    } catch (ex: PSQLException) {
                        if (ex.errorCode == 23505) {
                            log.warn("Caught a duplicate exception. Ignoring...")
                        }
                    }
                }

                is TusUploadEvent.ChunkVerified -> {
                    transaction {
                        UploadProgress.update({ UploadProgress.id eq event.id }) {
                            it[numChunksVerified] = event.chunk
                        }
                    }

                    if (event.chunk == event.numChunks) {
                        val state = transferStateService.retrieveState(event.id)!!
                        val irodsUser = state.user
                        val irodsZone = state.zone

                        val irodsCollection = state.targetCollection
                        val irodsFileName = state.targetName

                        // Finalize upload
                        log.debug("Upload ${state.id} has been completed!")
                        icat.useConnection {
                            autoCommit = false
                            log.debug("Registration of object...")

                            val resource = findResourceByNameAndZone("child_01", "tempZone") ?: return@useConnection
                            log.debug("Using resource $resource. $irodsUser, $irodsZone, $irodsCollection")

                            val (_, entry) = userHasWriteAccess(irodsUser, irodsZone, irodsCollection)
                            if (entry != null) {
                                // TODO Need to return the name such that we can retrieve it later at the frontend
                                // Likely just write it back into the database and use the OPTIONS endpoint of TUS
                                val availableName = findAvailableIRodsFileName(entry.objectId, irodsFileName)
                                val objectId = registerDataObject(
                                    entry.objectId,
                                    state.id,
                                    state.length,
                                    availableName,
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
                            } else {
                                log.info("User does not have permission to upload file to target resource!")
                            }
                        }
                    }
                }
            }
        }
    }
}
