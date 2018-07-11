package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.app.services.ValidatedFileForUpload
import dk.sdu.cloud.service.KafkaDescriptions
import dk.sdu.cloud.service.KafkaRequest

object HPCStreams : KafkaDescriptions() {
    val appEvents = stream<String, AppEvent>("appEvents") { it.systemId }
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = KafkaRequest.TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = AppEvent.Validated::class, name = "validated"),
    JsonSubTypes.Type(value = AppEvent.Prepared::class, name = "prepared"),
    JsonSubTypes.Type(value = AppEvent.ScheduledAtSlurm::class, name = "scheduled"),
    JsonSubTypes.Type(value = AppEvent.CompletedInSlurm::class, name = "completed_slurm"),
    JsonSubTypes.Type(value = AppEvent.ExecutionCompleted::class, name = "completed_execution"),
    JsonSubTypes.Type(value = AppEvent.Completed::class, name = "completed")
)
sealed class AppEvent {
    abstract val systemId: String
    abstract val timestamp: Long
    abstract val owner: String
    abstract val appWithDependencies: Application

    data class Validated(
        override val systemId: String,
        override val timestamp: Long,
        val jwt: String,
        override val owner: String,
        override val appWithDependencies: Application,

        val jobDirectory: String,
        val workingDirectory: String,
        val files: List<ValidatedFileForUpload>,
        val inlineSBatchJob: String
    ) : AppEvent()

    // This guy will most likely cause problems if clean up becomes more complicated.
    // But it might also not cause problems, let's try and see if this works out.
    interface NeedsRemoteCleaning {
        val sshUser: String
        val jobDirectory: String
        val workingDirectory: String
    }

    data class Prepared(
        override val systemId: String,
        override val timestamp: Long,
        override val owner: String,
        override val appWithDependencies: Application,

        override val sshUser: String,
        override val jobDirectory: String,
        override val workingDirectory: String,

        val jobScriptLocation: String
    ) : AppEvent(), NeedsRemoteCleaning

    data class ScheduledAtSlurm(
        override val systemId: String,
        override val timestamp: Long,
        override val owner: String,
        override val appWithDependencies: Application,

        override val sshUser: String,
        override val jobDirectory: String,
        override val workingDirectory: String,
        val slurmId: Long
    ) : AppEvent(), NeedsRemoteCleaning

    data class CompletedInSlurm(
        override val systemId: String,
        override val timestamp: Long,
        override val owner: String,
        override val appWithDependencies: Application,

        override val sshUser: String,
        override val jobDirectory: String,
        override val workingDirectory: String,

        val success: Boolean,
        val slurmId: Long
    ) : AppEvent(), NeedsRemoteCleaning

    data class ExecutionCompleted(
        override val systemId: String,
        override val timestamp: Long,
        override val owner: String,
        override val appWithDependencies: Application,

        override val sshUser: String,
        override val jobDirectory: String,
        override val workingDirectory: String,

        val successful: Boolean,
        val message: String
    ) : AppEvent(), NeedsRemoteCleaning

    data class Completed(
        override val systemId: String,
        override val timestamp: Long,
        override val owner: String,
        override val appWithDependencies: Application,

        val successful: Boolean,
        val message: String
    ) : AppEvent()

}

enum class AppState {
    /**
     * The job has been validated and is ready to be processed for scheduling
     */
    VALIDATED,
    /**
     * The job has all of its dependencies shipped to HPC and is ready to be scheduled
     */
    PREPARED,
    /**
     * The job has been scheduled in Slurm
     */
    SCHEDULED,
    /**
     * The job is currently running in the HPC environment
     */
    RUNNING,
    /**
     * The job has completed succesfully
     */
    SUCCESS,
    /**
     * The job has completed unsuccessfully
     */
    FAILURE;

    fun isFinal(): Boolean =
        when (this) {
            SUCCESS, FAILURE -> true
            else -> false
        }
}