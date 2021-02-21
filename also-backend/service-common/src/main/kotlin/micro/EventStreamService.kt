package dk.sdu.cloud.micro

import dk.sdu.cloud.events.EventStreamService

private val eventStreamServiceKey = MicroAttributeKey<EventStreamService>("event-stream-service")
var Micro.eventStreamService: EventStreamService
    get() {
        return attributes[eventStreamServiceKey]
    }
    set(value) {
        attributes[eventStreamServiceKey] = value
    }

val Micro.eventStreamServiceOrNull: EventStreamService?
    get() {
        return attributes.getOrNull(eventStreamServiceKey)
    }
