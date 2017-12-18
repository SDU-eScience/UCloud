package org.esciencecloud.auth.processors

import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Predicate
import org.esciencecloud.auth.ServiceDAO
import org.esciencecloud.auth.UserDAO
import org.esciencecloud.auth.api.UserEvent

class UserProcessor(
        private val stream: KStream<String, UserEvent>
) {
    fun init() {
        // We can probably create something better for this
        val branches = stream.branch(
                Predicate { _, value -> value is UserEvent.Created },
                Predicate { _, value -> value is UserEvent.Updated }
        )

        val createdStream = branches[0].mapValues { it as UserEvent.Created }
        val updatedStream = branches[1].mapValues { it as UserEvent.Updated }

        createdStream.foreach { _, value ->
            UserDAO.insert(value.userCreated)
        }

        updatedStream.foreach { _, value ->
            UserDAO.update(value.updatedUser)
        }
    }
}