package dk.sdu.cloud.auth.api

import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.EventStreamContainer

typealias UserEventProducer = EventProducer<UserEvent>
typealias UserEventConsumer = EventStream<UserEvent>

object AuthStreams : EventStreamContainer() {
    val UserUpdateStream = stream<UserEvent>("auth.user", { it.key })
}
