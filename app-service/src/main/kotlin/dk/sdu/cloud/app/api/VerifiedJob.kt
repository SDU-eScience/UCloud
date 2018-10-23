package dk.sdu.cloud.app.api

data class VerifiedJob(
    val application: Application,
    val files: List<ValidatedFileForUpload>,
    val id: String,
    val owner: String,
    val nodes: Int,
    val tasksPerNode: Int,
    val maxTime: SimpleDuration,
    val jobInput: VerifiedJobInput,
    val backend: String
)
