package org.esciencecloud.abc.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.esciencecloud.abc.Request
import org.esciencecloud.storage.Error

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = Request.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = HPCAppEvent.Started::class, name = "started"),
        JsonSubTypes.Type(value = HPCAppEvent.SuccessfullyCompleted::class, name = "success"),
        JsonSubTypes.Type(value = HPCAppEvent.UnsuccessfullyCompleted::class, name = "error"))
sealed class HPCAppEvent {
    data class Started(val jobId: Long) : HPCAppEvent()

    abstract class Ended : HPCAppEvent() {
        abstract val success: Boolean
    }

    data class SuccessfullyCompleted(val jobId: Long) : Ended() {
        override val success: Boolean = true
    }

    data class UnsuccessfullyCompleted(val reason: Error<Any>) : Ended() {
        override val success: Boolean = false
    }
}