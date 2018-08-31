package dk.sdu.cloud.storage.services

import dk.sdu.cloud.files.api.SensitivityLevel

sealed class TusException(why: String) : RuntimeException(why) {
    class NotFound : TusException("Not found")
}

data class TusUpload(
    val id: Long,
    val sizeInBytes: Long,
    val owner: String,
    val uploadPath: String,
    val sensitivity: SensitivityLevel,
    val progress: Long = 0,
    val createdAt: Long? = null,
    val modifiedAt: Long? = null
)

data class TusUploadCreationCommand(
    val sizeInBytes: Long,
    val uploadPath: String,
    val sensitivity: SensitivityLevel
)

interface TusDAO<Session> {
    fun findUpload(
        session: Session,
        user: String,
        id: Long
    ): TusUpload

    fun create(
        session: Session,
        user: String,
        uploadCreationCommand: TusUploadCreationCommand
    ): Long

    fun updateProgress(
        session: Session,
        user: String,
        id: Long,
        progress: Long
    )
}

