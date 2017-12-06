package org.esciencecloud.abc.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.esciencecloud.client.KafkaRequest
import org.esciencecloud.storage.Error

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = KafkaRequest.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = HPCAppEvent.Started::class, name = "started"),
        JsonSubTypes.Type(value = HPCAppEvent.SuccessfullyCompleted::class, name = "success"),
        JsonSubTypes.Type(value = HPCAppEvent.Pending::class, name = "pending"),
        JsonSubTypes.Type(value = HPCAppEvent.UnsuccessfullyCompleted::class, name = "error"))
sealed class HPCAppEvent {
    /**
     * The request has been submitted to Slurm, but we have not yet received notification that it has started
     */
    data class Pending(
            val jobId: Long,
            val jobDirectory: String,
            val workingDirectory: String,
            val originalRequest: KafkaRequest<HPCAppRequest.Start>
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

    fun toJobStatus(): JobStatus = when (this) {
        is HPCAppEvent.Pending -> JobStatus.PENDING
        is HPCAppEvent.SuccessfullyCompleted -> JobStatus.COMPLETE
        is HPCAppEvent.UnsuccessfullyCompleted -> JobStatus.FAILURE
        is HPCAppEvent.Started -> JobStatus.RUNNING

        is HPCAppEvent.Ended -> throw IllegalStateException() // Is abstract, all other cases should be caught
    }
}

