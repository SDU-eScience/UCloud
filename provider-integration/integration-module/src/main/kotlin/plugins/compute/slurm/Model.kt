package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.app.orchestrator.api.JobState
import kotlinx.serialization.Serializable

@Serializable
data class SlurmJob(
    val ucloudId: String,
    val slurmId: String,
    val partition: String = "normal",
    val lastKnown: String = "init",
    val status: Boolean = true,
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

object SlurmStateToUCloudState {
    private fun entry(slurmState: String, state: JobState, message: String): Pair<String, UcloudStateInfo> {
        return slurmState to UcloudStateInfo(state, slurmState, message, state.isFinal())
    }

    val slurmToUCloud: Map<String, UcloudStateInfo> = mapOf(
        entry("PENDING", JobState.IN_QUEUE, "Your job is currently in the queue"),
        entry("CONFIGURING", JobState.IN_QUEUE, "Your job is currently in the queue (CONFIGURING)"),
        entry("RESV_DEL_HOLD", JobState.IN_QUEUE, "Your job is currently in the queue (RESV_DEL_HOLD)"),
        entry("REQUEUE_FED", JobState.IN_QUEUE, "Your job is currently in the queue (REQUEUE_FED)"),
        entry("SUSPENDED", JobState.IN_QUEUE, "Your job is currently in the queue (SUSPENDED)"),

        entry("REQUEUE_HOLD", JobState.IN_QUEUE, "Your job is currently held for requeue"),
        entry("REQUEUED", JobState.IN_QUEUE, "Your job is currently in the queue (REQUEUED)"),
        entry("RESIZING", JobState.IN_QUEUE, "Your job is currently in the queue (RESIZING)"),

        entry("RUNNING", JobState.RUNNING, "Your job is now running"),
        entry("COMPLETING", JobState.RUNNING, "Your job is now running and about to complete"),
        entry("SIGNALING", JobState.RUNNING, "Your job is now running and about to complete"),
        entry("SPECIAL_EXIT", JobState.RUNNING, "Your job is now running and about to complete"),
        entry("STAGE_OUT", JobState.RUNNING, "Your job is now running and about to complete"),

        entry("STOPPED", JobState.RUNNING, "Your job is now running and about to complete"),

        entry("COMPLETED", JobState.SUCCESS, "Your job has successfully completed"),
        entry("CANCELLED", JobState.SUCCESS, "Your job has successfully completed, due to a cancel"),
        entry("FAILED", JobState.SUCCESS, "Your job has completed with a Slurm status of FAILED"),

        entry("OUT_OF_MEMORY", JobState.SUCCESS, "Your job was terminated with an out of memory error"),

        entry("BOOT_FAIL", JobState.FAILURE, "Your job has failed (BOOT_FAIL)"),
        entry("NODE_FAIL", JobState.FAILURE, "Your job has failed (NODE_FAIL)"),

        entry("REVOKED", JobState.FAILURE, "Your job has failed (REVOKED)"),
        entry("PREEMPTED", JobState.FAILURE, "Your job was preempted by Slurm"),

        entry("DEADLINE", JobState.EXPIRED, "Your job has expired (DEADLINE)"),
        entry("TIMEOUT", JobState.EXPIRED, "Your job has expired (TIMEOUT)")
    )
}

@Serializable
data class InteractiveSession(val token: String, val rank: Int , val ucloudId: String)
