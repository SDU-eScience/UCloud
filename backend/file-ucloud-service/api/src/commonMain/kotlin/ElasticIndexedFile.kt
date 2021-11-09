package dk.sdu.cloud.file.ucloud.api

import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.file.orchestrator.api.depth
import dk.sdu.cloud.file.orchestrator.api.fileName
import dk.sdu.cloud.file.orchestrator.api.normalize
import dk.sdu.cloud.provider.api.Permission

data class ElasticIndexedFile(
    val path: String, //Also is ID in elastic
    val size: Long,
    val fileType: FileType,
    val rctime: String? = null,
    val fileName: String,
    /**
    * Depth in the file hierarchy
    *
    * Example: /a/b/c will have a depth of 3 and /a/b/c/d will have a depth of 4 and / will have a depth of 0
    */
    val fileDepth: Int,
    val permission: List<Permission>,
    val createdBy: String
)

fun ElasticIndexedFile(
    path: String,
    size: Long,
    fileType: FileType,
    rctime: String? = null,
    permission: List<Permission>,
    createdBy: String
) = ElasticIndexedFile(path, size, fileType, rctime, path.normalize().fileName(), path.normalize().depth(), permission, createdBy)

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
    const val PERMISSION_FIELD = "permission"
    const val PERMISSION_KEYWORD = "permission" + ".keyword"
}
