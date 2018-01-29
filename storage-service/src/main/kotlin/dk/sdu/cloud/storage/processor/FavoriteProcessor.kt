package dk.sdu.cloud.storage.processor

import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.api.FavoriteCommand
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.model.MetadataEntry
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Predicate
import org.slf4j.LoggerFactory

class FavoriteProcessor(
    private val commandStream: KStream<String, KafkaRequest<FavoriteCommand>>,
    private val irods: StorageConnectionFactory
) {
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
                val path = account.paths.parseAbsolute(event.path, true)
                log.info("Updating favorites: $command")

                try {
                    when (event) {
                        is FavoriteCommand.Grant -> {
                            account.metadata.updateMetadata(
                                path,
                                listOf(MetadataEntry(FAVORITE_KEY, "true")),
                                emptyList()
                            )
                        }

                        is FavoriteCommand.Revoke -> {
                            // The iRODS API is truly strange.
                            account.metadata.updateMetadata(
                                path,
                                emptyList(),
                                listOf(
                                    MetadataEntry(FAVORITE_KEY, "true"),
                                    MetadataEntry(FAVORITE_KEY, "false")
                                )
                            )
                        }
                    }
                } catch (ex: Exception) {
                    log.warn("Caught JargonException while updating favorites. Ignoring this exception...")
                    log.warn(ex.stackTraceToString())
                }
            }
        }

        unauthenticated.foreach { _, event -> log.info("Unauthenticated attempt at granting permissions: $event") }
    }

    companion object {
        private const val FAVORITE_KEY = "favorited"
        private val log = LoggerFactory.getLogger(FavoriteProcessor::class.java)
    }
}