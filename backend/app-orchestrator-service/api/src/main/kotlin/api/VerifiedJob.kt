package dk.sdu.cloud.app.orchestrator.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.SimpleDuration
import kotlin.math.max

data class VerifiedJob(
    /**
     * A unique ID for this job.
     */
    val id: String,

    /**
     * A (optional) name for this job
     */
    val name: String? = null,

    /**
     * The username of the user who created this job.
     */
    val owner: String,

    /**
     * A copy of the [Application] that this job is/will be running.
     */
    val application: Application,

    /**
     * The backend on which to run this job.
     */
    val backend: String,

    /**
     * The number of nodes requested.
     */
    val nodes: Int,

    /**
     * The maximum amount of time this job can run for.
     */
    val maxTime: SimpleDuration,

    /**
     * The number of tasks per node requested.
     */
    val tasksPerNode: Int,

    val reservation: MachineReservation = MachineReservation.BURST,

    /**
     * The input values for this job.
     */
    val jobInput: VerifiedJobInput,

    /**
     * A complete list of files gathered from input_directory and input_file parameters.
     */
    val files: Set<ValidatedFileForUpload>,

    /**
     * A complete list of mounts
     */
    @Suppress("ConstructorParameterNaming")
    @get:JsonProperty("mounts")
    val _mounts: Set<ValidatedFileForUpload>? = null,

    /**
     * A list of peers that this application is requesting networking with.
     */
    @Suppress("ConstructorParameterNaming")
    @get:JsonProperty("peers")
    val _peers: Set<ApplicationPeer>? = null,

    /**
     * The job's current state.
     */
    val currentState: JobState,

    /**
     * The state of the job when the job entered the failed state
     */
    var failedState: JobState?,

    /**
     * A status message.
     */
    val status: String,

    /**
     * The collection to put the results in. This defaults to [Application.metadata.title].
     */
    val archiveInCollection: String,

    val outputFolder: String? = null,

    /**
     * Timestamp for when this job was created.
     */
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Timestamp for when this job was last updated.
     */
    val modifiedAt: Long = System.currentTimeMillis(),

    /**
     * Timestamp for when this job started execution.
     */
    val startedAt: Long? = null,

    /**
     * Persistent url to application
     */
    val url: String? = null,

    val project: String? = null
) {
    @get:JsonIgnore val mounts: Set<ValidatedFileForUpload>
        get() = _mounts ?: emptySet()

    @get:JsonIgnore
    val peers: Set<ApplicationPeer>
        get() = _peers ?: emptySet()

    /**
     * Milliseconds left of job from the job is started, null if the job is not started
     */
    val timeLeft: Long? get() = if (startedAt != null) {
        max((startedAt + maxTime.toMillis()) - System.currentTimeMillis(), 0)
    } else {
        null
    }

    override fun toString() = "VerifiedJob(${application.metadata.name}@${application.metadata.version})"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VerifiedJob

        if (files != other.files) return false
        if (nodes != other.nodes) return false
        if (tasksPerNode != other.tasksPerNode) return false
        if (maxTime != other.maxTime) return false
        if (jobInput != other.jobInput) return false
        if (_mounts != other._mounts) return false
        if (_peers != other._peers) return false
        if (reservation != other.reservation) return false

        return true
    }

    override fun hashCode(): Int {
        var result = files.hashCode()
        result = 31 * result + nodes
        result = 31 * result + tasksPerNode
        result = 31 * result + maxTime.hashCode()
        result = 31 * result + jobInput.hashCode()
        result = 31 * result + (_mounts?.hashCode() ?: 0)
        result = 31 * result + (_peers?.hashCode() ?: 0)
        result = 31 * result + reservation.hashCode()
        return result
    }
}
