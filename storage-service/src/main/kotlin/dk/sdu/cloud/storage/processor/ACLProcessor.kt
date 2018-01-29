package dk.sdu.cloud.storage.processor

import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.storage.api.GrantPermissions
import dk.sdu.cloud.storage.api.TemporaryRight
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.ext.irods.IRodsUser
import dk.sdu.cloud.storage.model.AccessEntry
import dk.sdu.cloud.storage.model.AccessRight
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Predicate
import org.slf4j.LoggerFactory

class ACLProcessor(
    private val commandStream: KStream<String, KafkaRequest<GrantPermissions>>,
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
        // We should write this into a second event stream. This is only temporary, and iRODS is potentially
        // problematic here so we just take the easy route.
        authenticated.foreach { _, command ->
            val validated = TokenValidation.validateOrNull(command.header.performedFor)
            if (validated == null) {
                log.info("Unauthenticated attempt at granting permissions: $command")
            } else {
                log.info("Granting permissions: ${command.event}")
                val account =
                    irods.createForAccount(validated.subject, validated.token).capture() ?: return@foreach run {
                        log.warn("Unexpected result from iRODS")
                    }

                val path = account.paths.parseAbsolute(command.event.onFile, true)
                val toEntity = IRodsUser.fromUsernameAndZone(command.event.toUser, account.connectedUser.zone)
                val irodsPermission = when (command.event.rights) {
                    TemporaryRight.READ -> AccessRight.READ
                    TemporaryRight.READ_WRITE -> AccessRight.READ_WRITE
                    TemporaryRight.OWN -> AccessRight.OWN
                }

                val accessEntry = AccessEntry(toEntity, irodsPermission)

                account.accessControl.updateACL(path, )
            }
        }

        unauthenticated.foreach { _, event -> log.info("Unauthenticated attempt at granting permissions: $event") }
    }

}