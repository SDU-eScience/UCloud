package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.indexing.util.depth
import dk.sdu.cloud.indexing.util.fileName

/**
 * An [StorageFile] as it is represented in elasticsearch
 *
 * @see dk.sdu.cloud.indexing.services.IndexQueryService
 */
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
    val fileName: String = path.normalize().fileName(),
    val fileDepth: Int = path.normalize().depth()
) {
    fun toMaterializedFile(): StorageFile = StorageFile(
        path = path,
        ownerName = path.components().getOrElse(1) { "unknown" },
        fileType = fileType,
        createdAt = 0L,
        modifiedAt = 0L,
        size = size,
        ownSensitivityLevel = SensitivityLevel.PRIVATE
    )

    @Suppress("unused")
    companion object {
        // Refactoring safe without most of the performance penalty
        val PATH_FIELD = ElasticIndexedFile::path.name
        val PATH_KEYWORD = ElasticIndexedFile::path.name + ".keyword"
        val FILE_NAME_FIELD = ElasticIndexedFile::fileName.name
        val FILE_NAME_KEYWORD = ElasticIndexedFile::fileName.name + ".keyword"
        val FILE_NAME_EXTENSION = ElasticIndexedFile::fileName.name + ".extension"
        val FILE_DEPTH_FIELD = ElasticIndexedFile::fileDepth.name

        val FILE_TYPE_FIELD = ElasticIndexedFile::fileType.name
        val SIZE_FIELD = ElasticIndexedFile::size.name
    }
}
