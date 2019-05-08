package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class VerifiedJob(
    val application: Application,
    val files: List<ValidatedFileForUpload>,
    val id: String,
    val owner: String,
    val nodes: Int,
    val tasksPerNode: Int,
    val maxTime: SimpleDuration,
    val jobInput: VerifiedJobInput,
    val backend: String,
    val currentState: JobState,
    val status: String,
    val archiveInCollection: String,
    val ownerUid: Long,
    val workspace: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    @get:JsonProperty("mounts")
    val _mounts: List<ValidatedFileForUpload>? = null,
    val startedAt: Long? = null
) {
    @get:JsonIgnore
    val mounts: List<ValidatedFileForUpload> get() = _mounts ?: emptyList()

    override fun toString() = "VerifiedJob(${application.metadata.name}@${application.metadata.version})"
}
