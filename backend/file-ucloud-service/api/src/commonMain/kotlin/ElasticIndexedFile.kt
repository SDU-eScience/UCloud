package dk.sdu.cloud.file.ucloud.api

import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourcePermissions
import kotlinx.serialization.Serializable

@Serializable
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
    val createdAt: Long,
    val collectionId: String,
    val owner: String,
    val projectId: String? = null
)

fun ElasticIndexedFile(
    path: String,
    size: Long,
    fileType: FileType,
    rctime: String? = null,
    permission: List<Permission>,
    createdAt: Long,
    collectionId: String,
    owner: String,
    projectId: String? = null
) = ElasticIndexedFile(
    path = path,
    size = size,
    fileType = fileType,
    rctime = rctime,
    fileName = path.normalize().fileName(),
    fileDepth = path.normalize().depth(),
    permission = permission,
    createdAt = createdAt,
    collectionId = collectionId,
    owner = owner,
    projectId = projectId
)

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
    const val OWNER_FIELD = "owner"
    const val PROJECT_ID = "projectId"
    const val COLLECTION_ID_FIELD = "collectionId"
    const val CREATED_AT_FIELD = "createdAt"
}

fun ElasticIndexedFile.toPartialUFile(): PartialUFile {
    return PartialUFile(
        path,
        UFileStatus(
            sizeInBytes = size,
            type = fileType
        ),
        createdAt,
        ResourceOwner(owner, projectId)
    )
}
