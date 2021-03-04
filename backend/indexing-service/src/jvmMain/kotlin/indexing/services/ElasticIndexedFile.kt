package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.indexing.util.depth
import dk.sdu.cloud.indexing.util.fileName
import kotlinx.serialization.Serializable

/**
 * An [StorageFile] as it is represented in elasticsearch
 *
 * @see dk.sdu.cloud.indexing.services.IndexQueryService
 */
@Serializable
data class ElasticIndexedFile(
    val path: String,
    /**
     * Depth in the file hierarchy
     *
     * Example: /a/b/c will have a depth of 3 and /a/b/c/d will have a depth of 4 and / will have a depth of 0
     */
    val size: Long,
    val fileType: FileType,
    val rctime: String? = null,
    val fileName: String,
    val fileDepth: Int,
)

fun ElasticIndexedFile(
    path: String,
    size: Long,
    fileType: FileType,
    rctime: String? = null,
) = ElasticIndexedFile(path, size, fileType, rctime, path.normalize().fileName(), path.normalize().depth())

object ElasticIndexedFileConstants {
    // Refactoring safe without most of the performance penalty
    const val PATH_FIELD = "path"
    const val PATH_KEYWORD = "path" + ".keyword"
    const val FILE_NAME_FIELD = "fileName"
    const val FILE_NAME_KEYWORD = "fileName" + ".keyword"
    const val FILE_NAME_EXTENSION = "fileName" + ".extension"
    const val FILE_DEPTH_FIELD = "fileDepth"

    const val FILE_TYPE_FIELD = "fileType"
    const val SIZE_FIELD = "size"
}

fun ElasticIndexedFile.toMaterializedFile(): StorageFile = StorageFile(
    path = path,
    ownerName = path.components().getOrElse(1) { "unknown" },
    fileType = fileType,
    createdAt = 0L,
    modifiedAt = 0L,
    size = size,
    ownSensitivityLevel = SensitivityLevel.PRIVATE
)
