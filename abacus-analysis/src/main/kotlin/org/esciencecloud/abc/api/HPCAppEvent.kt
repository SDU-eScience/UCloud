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
    /**
     * The request has been submitted to Slurm, but we have not yet received notification that it has started
     */
    data class Pending(
            val jobId: Long,
            val jobDirectory: String,
            val workingDirectory: String,
            val originalRequest: Request<HPCAppRequest.Start>
    ) : HPCAppEvent()

    /**
     * The request has started and is being processed by Slurm
     */
    data class Started(val jobId: Long) : HPCAppEvent()

    /**
     * The request has been handled by Slurm. See sub-classes for outcome.
     */
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