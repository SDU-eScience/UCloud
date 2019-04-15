package dk.sdu.cloud.app.api

import dk.sdu.cloud.events.EventStreamContainer
import dk.sdu.cloud.file.api.StorageFile

data class JobStateChange(val systemId: String, val newState: JobState)

enum class JobState {
    /**
     * The job has been validated and is ready to be processed for scheduling
     */
    VALIDATED,

    /**
     * The job has all of its dependencies shipped to compute and is ready to be scheduled
     */
    PREPARED,

    /**
     * The job has been scheduled
     */
    SCHEDULED,

    /**
     * The job is currently running in the HPC environment
     */
    RUNNING,

    /**
     * The job has completed successfully, but is in the process of transferring files.
     */
    TRANSFER_SUCCESS,

    /**
     * The job has completed successfully
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

data class ValidatedFileForUpload(
    val id: String,
    val stat: StorageFile,
    val destinationFileName: String,
    val destinationPath: String,
    val sourcePath: String,
    val needsExtractionOfType: FileForUploadArchiveType?
)

enum class FileForUploadArchiveType {
    ZIP
}
