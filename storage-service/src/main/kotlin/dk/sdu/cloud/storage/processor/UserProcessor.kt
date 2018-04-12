package dk.sdu.cloud.storage.processor

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.auth.api.UserEvent
import org.apache.kafka.streams.kstream.KStream
import org.slf4j.LoggerFactory

class UserProcessor(
    private val stream: KStream<String, UserEvent>,
    private val cloud: RefreshingJWTAuthenticatedCloud
) {
    private val log = LoggerFactory.getLogger(UserProcessor::class.java)

    fun init() {
        stream.foreach { _, event -> handleEvent(event) }
    }

    private fun handleEvent(event: UserEvent) {
        when (event) {
            is UserEvent.Created -> {
                log.info("Creating a matching user in iRODS: $event")
                TODO()
            }

            else -> {
                log.warn("Discarding event: $event")
            }
        }
    }
}