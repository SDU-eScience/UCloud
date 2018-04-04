package dk.sdu.cloud.storage.processor

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.auth.api.UserEvent
import dk.sdu.cloud.storage.services.ICATService
import dk.sdu.cloud.storage.services.ext.DuplicateException
import dk.sdu.cloud.storage.services.ext.StorageConnectionFactory
import org.apache.kafka.streams.kstream.KStream
import org.slf4j.LoggerFactory

class UserProcessor(
    private val stream: KStream<String, UserEvent>,
    private val irods: StorageConnectionFactory,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val icatService: ICATService
) {
    private val log = LoggerFactory.getLogger(UserProcessor::class.java)

    fun init() {
        stream.foreach { _, event -> handleEvent(event) }
    }

    private fun handleEvent(event: UserEvent) {
        when (event) {
            is UserEvent.Created -> {
                log.info("Creating a matching user in iRODS: $event")
                val username = event.userId
                irods.createForAccount("_storage", cloud.tokenRefresher.retrieveTokenRefreshIfNeeded()).use {
                    try {
                        it.userAdmin!!.createUser(username)
                    } catch (ex: DuplicateException) {
                        // Ignored
                    }
                }

                // Create default directories
                try {
                    icatService.createDirectDirectory("/home/$username/Jobs", username)
                } catch (_: DuplicateException) {}

                try {
                    icatService.createDirectDirectory("/home/$username/Uploads", username)
                } catch (_: DuplicateException) {}
            }

            else -> {
                log.warn("Discarding event: $event")
            }
        }
    }
}