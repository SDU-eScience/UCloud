package dk.sdu.cloud.app.store.api

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