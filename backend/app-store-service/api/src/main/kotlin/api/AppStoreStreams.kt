package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.EventStreamContainer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class AppEvent {
    abstract val appName: String
    abstract val appVersion: String

    @Serializable
    @SerialName("deleted")
    data class Deleted(
        override val appName: String,
        override val appVersion: String
    ) : AppEvent()
}

val AppEvent.key: String get() = "$appName$appVersion"
typealias AppEventProducer = EventProducer<AppEvent>
typealias AppEventConsumer = EventStream<AppEvent>

object AppStoreStreams : EventStreamContainer() {
    val AppDeletedStream = stream(AppEvent.serializer(), "appStore.delete", {it.key} )
}
