package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.EventStreamContainer

typealias AppEventProducer = EventProducer<AppEvent>
typealias AppEventConsumer = EventStream<AppEvent>

object AppStoreStreams : EventStreamContainer() {
    val AppDeletedStream = stream<AppEvent>("appStore.delete", {it.key} )
}
