package dk.sdu.cloud.app.orchestrator.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.SimpleDuration
import kotlin.math.max

data class VerifiedJob(
    /**
     * A copy of the [Application] that this job is/will be running.
     */
    val application: Application,

    /**
     * A (optional) name for this job
     */
    val name: String? = null,

    /**
     * A complete list of files gathered from input_directory and input_file parameters.
     */
    val files: List<ValidatedFileForUpload>,

    /**
     * A unique ID for this job.
     */
    val id: String,

    /**
     * The real username of the user who created this job.
     *
     * If this is started by a project proxy user this will point to the real user (as indicated by the
     * extendedByChain).
     */
    val owner: String,

    /**
     * The number of nodes requested.
     */
    val nodes: Int,

    /**
     * The number of tasks per node requested.
     */
    val tasksPerNode: Int,

    /**
     * The maximum amount of time this job can run for.
     */
    val maxTime: SimpleDuration,

    /**
     * The input values for this job.
     */
    val jobInput: VerifiedJobInput,

    /**
     * The backend on which to run this job.
     */
    val backend: String,

    /**
     * The job's current state.
     */
    val currentState: JobState,

    /**
     * A status message.
     */
    val status: String,

    /**
     * The state of the job when the job entered the failed state
     */
    var failedState: JobState?,

    /**
     * The collection to put the results in. This defaults to [Application.metadata.title].
     */
    val archiveInCollection: String,

    /**
     * Timestamp for when this job was created.
     */
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Timestamp for when this job was last updated.
     */
    val modifiedAt: Long = System.currentTimeMillis(),

    /**
     * A complete list of mounts
     */
    @Suppress("ConstructorParameterNaming")
    @get:JsonProperty("mounts")
    val _mounts: List<ValidatedFileForUpload>? = null,

    /**
     * Timestamp for when this job started execution.
     */
    val startedAt: Long? = null,

    /**
     * Milliseconds left of job from the job is started, null if the job is not started
     */
    val timeLeft: Long? = if (startedAt != null) {
        max((startedAt + maxTime.toMillis()) - System.currentTimeMillis(), 0)
    } else {
        null
    },

    /**
     * The username of the user who initiated the job.
     *
     * If this is started by a project proxy user then this will point to the username of that proxy user.
     */
    val user: String = owner,

    /**
     * A list of peers that this application is requesting networking with.
     */
    @Suppress("ConstructorParameterNaming")
    @get:JsonProperty("peers")
    val _peers: List<ApplicationPeer>? = null,

    val reservation: MachineReservation = MachineReservation.BURST,

    val outputFolder: String? = null
) {
    @get:JsonIgnore
    val mounts: List<ValidatedFileForUpload>
        get() = _mounts ?: emptyList()

    @get:JsonIgnore
    val peers: List<ApplicationPeer>
        get() = _peers ?: emptyList()

    override fun toString() = "VerifiedJob(${application.metadata.name}@${application.metadata.version})"

    override fun equals(other: Any?): Boolean {
        if (other is VerifiedJob) {
            //Check time and node settings
            if (this.maxTime != other.maxTime) {
                return false
            }
            if (this.nodes != other.nodes) {
                return false
            }
            if (this.tasksPerNode != other.tasksPerNode) {
                return false
            }

            //Check reservation
            if (this.reservation != other.reservation) {
                return false
            }
            //Check files
            if (this.files.toSet() != other.files.toSet()) {
                return false
            }
            //Check Mounts
            if (this.mounts.toSet() != other.mounts.toSet()) {
                return false
            }

            //Checking Peers
            if (this.peers.toSet() != other.peers.toSet()) {
                return false
            }

            //Checking jobInput
            if (this.jobInput.backingData != other.jobInput.backingData) {
                return false
            }
            return true
        }
        return false
    }
}
