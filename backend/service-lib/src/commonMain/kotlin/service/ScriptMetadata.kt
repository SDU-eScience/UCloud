package dk.sdu.cloud.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class WhenToStart {
    @Serializable
    @SerialName("periodically")
    data class Periodically(val timeBetweenInvocationInMillis: Long) : WhenToStart() {
        init {
            if (timeBetweenInvocationInMillis < 60_000) {
                throw IllegalArgumentException("timeBetweenInvocationInMillis must be at least 60_000")
            }
        }
    }

    @Serializable
    @SerialName("daily")
    data class Daily(val hour: Int, val minute: Int = 0) : WhenToStart() {
        init {
            require(hour in 0..23)
            require(minute in 0..59)
        }
    }

    @Serializable
    @SerialName("weekly")
    data class Weekly(val dayOfWeek: Int, val hour: Int = 0, val minute: Int = 0) : WhenToStart() {
        init {
            require(dayOfWeek in 0..6)
            require(hour in 0..23)
            require(minute in 0..59)
        }
    }

    @Serializable
    @SerialName("never")
    object Never: WhenToStart()
}

@Serializable
data class ScriptMetadata(
    val id: String,
    val title: String,
    val whenToStart: WhenToStart,
)
