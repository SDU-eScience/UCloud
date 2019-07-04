package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.Timestamps
import dk.sdu.cloud.file.api.createdAt
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.link
import dk.sdu.cloud.file.api.modifiedAt
import dk.sdu.cloud.file.api.ownSensitivityLevel
import dk.sdu.cloud.file.api.ownerName
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.api.size

/**
 * An [StorageFile] as it is represented in elasticsearch
 *
 * @see dk.sdu.cloud.indexing.services.IndexingService
 * @see dk.sdu.cloud.indexing.services.IndexQueryService
 */
data class ElasticIndexedFile(
    val id: String,
    val path: String,
    val fileName: String,
    val owner: String,

    /**
     * Depth in the file hierarchy
     *
     * Example: /a/b/c will have a depth of 3 and /a/b/c/d will have a depth of 4 and / will have a depth of 0
     */
    val fileDepth: Int,

    val fileType: FileType,

    val size: Long,
    val fileTimestamps: Timestamps,

    val fileIsLink: Boolean,

    val sensitivity: SensitivityLevel?
) {
    fun toMaterializedFile(): StorageFile = StorageFile(
        fileId = id,
        path = path,
        ownerName = owner,
        fileType = fileType,
        createdAt = fileTimestamps.created,
        modifiedAt = fileTimestamps.modified,
        size = size,
        link = fileIsLink,
        ownSensitivityLevel = sensitivity
    )

    @Suppress("unused")
    companion object {
        // Refactoring safe without most of the performance penalty
        val ID_FIELD = ElasticIndexedFile::id.name
        val PATH_FIELD = ElasticIndexedFile::path.name
        val PATH_KEYWORD = ElasticIndexedFile::path.name + ".keyword"
        val FILE_NAME_FIELD = ElasticIndexedFile::fileName.name
        val FILE_NAME_KEYWORD = ElasticIndexedFile::fileName.name + ".keyword"
        val FILE_NAME_EXTENSION = ElasticIndexedFile::fileName.name + ".extension"
        val OWNER_FIELD = ElasticIndexedFile::owner.name
        val FILE_DEPTH_FIELD = ElasticIndexedFile::fileDepth.name

        val FILE_TYPE_FIELD = ElasticIndexedFile::fileType.name
        val SIZE_FIELD = ElasticIndexedFile::size.name
        val FILE_TIMESTAMPS_FIELD = ElasticIndexedFile::fileTimestamps.name
        val TIMESTAMP_CREATED_FIELD = FILE_TIMESTAMPS_FIELD + "." + Timestamps::created.name
        val TIMESTAMP_MODIFIED_FIELD = FILE_TIMESTAMPS_FIELD + "." + Timestamps::modified.name
        val TIMESTAMP_ACCESSED_FIELD = FILE_TIMESTAMPS_FIELD + "." + Timestamps::accessed.name

        val FILE_IS_LINK_FIELD = ElasticIndexedFile::fileIsLink.name

        val SENSITIVITY_FIELD = ElasticIndexedFile::sensitivity.name

    }
}

fun StorageFile.withSensitivity(level: SensitivityLevel): StorageFile = StorageFile(
    fileId = fileId,
    path = path,
    ownerName = ownerName,
    fileType = fileType,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    size = size,
    link = link,
    ownSensitivityLevel = ownSensitivityLevel,
    sensitivityLevel = level
)
