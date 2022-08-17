package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.app.orchestrator.api.JobState
import kotlinx.serialization.Serializable

@Serializable
data class SlurmJob(
    val ucloudId: String,
    val slurmId: String,
    val partition: String = "normal",
    val lastKnown: String = "init",
    val status: Int = 1,
    val elapsed: Long = 0L,
)

@Serializable
data class SlurmAllocation(
    val jobId: String,
    val state: String,
    val exitCode: String,
    val start: String,
    val end: String
)

@Serializable
data class UcloudStateInfo(
    val state: JobState,
    val providerState: String,
    val message: String,
    val isFinal: Boolean
)

@Deprecated("Renamed to SlurmStateToUCloudState")
val Mapping = SlurmStateToUCloudState

object SlurmStateToUCloudState {
    private fun entry(slurmState: String, state: JobState, message: String): Pair<String, UcloudStateInfo> {
        return slurmState to UcloudStateInfo(state, slurmState, message, state.isFinal())
    }

    val slurmToUCloud: Map<String, UcloudStateInfo> = mapOf(
        entry("PENDING", JobState.IN_QUEUE, "Job is queued"),
        entry("CONFIGURING", JobState.IN_QUEUE, "Job is queued"),
        entry("RESV_DEL_HOLD", JobState.IN_QUEUE, "Job is queued"),
        entry("REQUEUE_FED", JobState.IN_QUEUE, "Job is queued"),
        entry("SUSPENDED", JobState.IN_QUEUE, "Job is queued"),

        entry("REQUEUE_HOLD", JobState.IN_QUEUE, "Job is held for requeue"),
        entry("REQUEUED", JobState.IN_QUEUE, "Job is requeued"),
        entry("RESIZING", JobState.IN_QUEUE, "Job is resizing"),

        entry("RUNNING", JobState.RUNNING, "Job is running"),
        entry("COMPLETING", JobState.RUNNING, "Job is running"),
        entry("SIGNALING", JobState.RUNNING, "Job is running"),
        entry("SPECIAL_EXIT", JobState.RUNNING, "Job is running"),
        entry("STAGE_OUT", JobState.RUNNING, "Job is running"),

        entry("STOPPED", JobState.RUNNING, "Job is stopped"),

        entry("COMPLETED", JobState.SUCCESS, "Job is success"),
        entry("CANCELLED", JobState.SUCCESS, "Job is success"),
        entry("FAILED", JobState.SUCCESS, "Job is success"),

        entry("OUT_OF_MEMORY", JobState.SUCCESS, "Out of memory"),

        entry("BOOT_FAIL", JobState.FAILURE, "Job is failed"),
        entry("NODE_FAIL", JobState.FAILURE, "Job is failed"),

        entry("REVOKED", JobState.FAILURE, "Job is revoked"),
        entry("PREEMPTED", JobState.FAILURE, "Preempted"),
        entry("DEADLINE", JobState.EXPIRED, "Job is expired"),
        entry("TIMEOUT", JobState.EXPIRED, "Job is expired")
    )

    @Deprecated("Renamed to slurmToUCloud")
    val uCloudStates = slurmToUCloud
}

@Serializable
data class InteractiveSession(val token: String, val rank: Int , val ucloudId: String)