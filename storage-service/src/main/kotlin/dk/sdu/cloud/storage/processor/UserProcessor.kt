package dk.sdu.cloud.storage.processor

import dk.sdu.cloud.auth.api.UserEvent
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.Error
import org.apache.kafka.streams.kstream.KStream
import org.slf4j.LoggerFactory

class UserProcessor(
        private val stream: KStream<String, UserEvent>,
        private val adminConnection: StorageConnection // TODO This one might be problematic
) {
    private val log = LoggerFactory.getLogger(UserProcessor::class.java)

    fun init() {
        stream.foreach { _, event -> handleEvent(event) }
    }

    private fun handleEvent(event: UserEvent) {
        when (event) {
            is UserEvent.Created -> {
                log.info("Creating a matching user in iRODS: $event")
                val result = adminConnection.userAdmin!!.createUser(event.userId)
                if (result is Error) {
                    // TODO Duplicate. We need to get rid of these Error types
                    if (result.errorCode != 2) {
                        result.orThrow() // This will throw
                    }
                }
            }
        }
    }
}