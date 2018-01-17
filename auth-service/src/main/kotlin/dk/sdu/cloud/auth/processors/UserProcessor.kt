package dk.sdu.cloud.auth.processors

import dk.sdu.cloud.auth.api.UserEvent
import dk.sdu.cloud.auth.services.UserDAO
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Predicate
import org.h2.jdbc.JdbcSQLException
import org.slf4j.LoggerFactory

class UserProcessor(
        private val stream: KStream<String, UserEvent>
) {
    private val log = LoggerFactory.getLogger(UserProcessor::class.java)

    fun init() {
        // We can probably create something better for this
        val branches = stream.branch(
                Predicate { _, value -> value is UserEvent.Created },
                Predicate { _, value -> value is UserEvent.Updated }
        )

        val createdStream = branches[0].mapValues { it as UserEvent.Created }
        val updatedStream = branches[1].mapValues { it as UserEvent.Updated }

        createdStream.foreach { _, value ->
            try {
                log.info("Creating user: $value")
                UserDAO.insert(value.userCreated)
            } catch (ex: JdbcSQLException) {
                if (ex.errorCode == 23505) {
                    // Duplicates might occur if two requests arrive before either gets serialized into the database
                    log.warn("Caught duplicate while inserting new user")
                    log.warn("Ignoring...")
                } else {
                    throw ex
                }
            }
        }

        updatedStream.foreach { _, value ->
            log.info("Updating user: $value")
            UserDAO.update(value.updatedUser)
        }
    }
}