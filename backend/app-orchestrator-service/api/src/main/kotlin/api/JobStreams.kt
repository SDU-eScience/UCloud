package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.file.api.StorageFile

data class JobStateChange(val systemId: String, val newState: JobState)

enum class JobState {
    /**
     * Any job which has been submitted and not yet in a final state where the number of tasks running is less than
     * the number of tasks requested
     */
    IN_QUEUE,

    /**
     * A job where all the tasks are running
     */
    RUNNING,

    /**
     * A job which has been cancelled, either by user request or system request
     */
    CANCELING,

    /**
     * A job which has terminated. The job terminated with no _scheduler_ error.
     *
     * Note: A job will complete successfully even if the user application exits with an unsuccessful status code.
     */
    SUCCESS,

    /**
     * A job which has terminated with a failure.
     *
     * Note: A job will fail _only_ if it is the scheduler's fault
     */
    FAILURE;

    fun isFinal(): Boolean =
        when (this) {
            SUCCESS, FAILURE -> true
            else -> false
        }
}

data class ValidatedFileForUpload(
    val id: String,
    val stat: StorageFile,
    val sourcePath: String,
    val readOnly: Boolean = false
)
