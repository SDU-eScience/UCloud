package dk.sdu.cloud.pong.api

import dk.sdu.cloud.events.EventStreamContainer

object Streams : EventStreamContainer() {
    val immediate = stream<Message>("immediate-stream", { "" })
    val batched = stream<Message>("batched-stream", { "" })
}
