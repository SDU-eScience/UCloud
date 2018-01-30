package dk.sdu.cloud.storage.processor

import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.storage.api.PermissionCommand
import dk.sdu.cloud.storage.api.TemporaryRight
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.ext.irods.IRodsUser
import dk.sdu.cloud.storage.model.AccessEntry
import dk.sdu.cloud.storage.model.AccessRight
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Predicate
import org.slf4j.LoggerFactory

class ACLProcessor(
    private val commandStream: KStream<String, KafkaRequest<PermissionCommand>>,
    private val irods: StorageConnectionFactory
) {
    private val log = LoggerFactory.getLogger(UserProcessor::class.java)

    fun init() {
        val branches = commandStream.branch(
            Predicate { _, event -> TokenValidation.validateOrNull(event.header.performedFor) != null },
            Predicate { _, event -> TokenValidation.validateOrNull(event.header.performedFor) == null }
        )

        val authenticated = branches[0]
        val unauthenticated = branches[1]

        // NOTE(Dan): This is a gigantic hack. We should not treat command objects like they are re-playable events.
        // We should write this into a second event commandStream. This is only temporary, and iRODS is potentially
        // problematic here so we just take the easy route.
        authenticated.foreach { _, command ->
            val validated = TokenValidation.validateOrNull(command.header.performedFor)
            if (validated == null) {
                log.info("Unauthenticated attempt at granting permissions: $command")
            } else {
                val event = command.event
                val account =
                    irods.createForAccount(validated.subject, validated.token).capture() ?: return@foreach run {
                        log.warn("Unexpected result from iRODS")
                    }

                account.use {
                    val path = account.paths.parseAbsolute(event.onFile, true)
                    val entity = IRodsUser.fromUsernameAndZone(event.entity, account.connectedUser.zone)

                    when (event) {
                        is PermissionCommand.Grant -> {
                            log.info("Granting permissions: $event")

                            val irodsPermission = when (event.rights) {
                                TemporaryRight.READ -> AccessRight.READ
                                TemporaryRight.READ_WRITE -> AccessRight.READ_WRITE
                                TemporaryRight.OWN -> AccessRight.OWN
                            }

                            val accessEntry = AccessEntry(entity, irodsPermission)
                            account.accessControl.updateACL(path, listOf(accessEntry), false)
                            // TODO We need to communicate these results back to the user
                        }

                        is PermissionCommand.Revoke -> {
                            log.info("Removing permissions $event")
                            val accessEntry = AccessEntry(entity, AccessRight.NONE)
                            account.accessControl.updateACL(path, listOf(accessEntry), false)
                            // TODO We need to communicate these results back to the user
                        }
                    }
                }
            }
        }

        unauthenticated.foreach { _, event -> log.info("Unauthenticated attempt at granting permissions: $event") }
    }
}
