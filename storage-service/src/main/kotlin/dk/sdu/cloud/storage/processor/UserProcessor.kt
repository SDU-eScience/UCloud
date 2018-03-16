package dk.sdu.cloud.storage.processor

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.UserEvent
import dk.sdu.cloud.storage.Error
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.services.ICATService
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
                irods.createForAccount("_storage", cloud.tokenRefresher.retrieveTokenRefreshIfNeeded()).orThrow().use {
                    val result = it.userAdmin!!.createUser(username)
                    if (result is Error) {
                        // TODO Duplicate. We need to get rid of these Error types
                        if (result.errorCode != 2) {
                            result.orThrow() // This will throw
                        }
                    }
                }

                // Create default directories
                icatService.createDirectDirectory("/home/$username/Jobs", username)
                icatService.createDirectDirectory("/home/$username/Uploads", username)
            }

            else -> {
                log.warn("Discarding event: $event")
            }
        }
    }
}