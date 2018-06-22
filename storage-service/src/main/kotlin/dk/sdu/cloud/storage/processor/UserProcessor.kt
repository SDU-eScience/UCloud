package dk.sdu.cloud.storage.processor

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.auth.api.UserEvent
import dk.sdu.cloud.storage.services.cephfs.CephFSUserDao
import org.apache.kafka.streams.kstream.KStream
import org.slf4j.LoggerFactory

class UserProcessor(
    private val stream: KStream<String, UserEvent>,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val isDevelopment: Boolean,
    private val userDao: CephFSUserDao
) {
    private val log = LoggerFactory.getLogger(UserProcessor::class.java)

    fun init() {
        stream.foreach { _, event -> handleEvent(event) }
    }

    private fun handleEvent(event: UserEvent) {
        when (event) {
            is UserEvent.Created -> {
                log.info("Creating a matching user: $event")
                val prefix = if (isDevelopment) emptyList() else listOf("sudo")
                val command = listOf("sdu_cloud_add_user", userDao.findUnixUser(event.userId), event.userId)

                val process = ProcessBuilder().apply { command(prefix + command) }.start()
                if (process.waitFor() != 0) {
                    throw IllegalStateException("Unable to create new user: $event")
                }
            }

            else -> {
                log.warn("Discarding event: $event")
            }
        }
    }
}