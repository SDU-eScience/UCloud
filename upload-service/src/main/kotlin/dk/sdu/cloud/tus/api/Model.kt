package dk.sdu.cloud.tus.api

data class UploadSummary(
    val id: String,
    val length: Long,
    val offset: Long,
    val savedAs: String?
)

data class UploadState(
    val id: String,

    val length: Long,
    val offset: Long,

    val user: String,
    val zone: String,

    val targetCollection: String,
    val targetName: String
)

data class UploadCreationCommand(
    val fileName: String?,
    val sensitive: Boolean,
    val owner: String?,
    val location: String?,
    val length: Long
)