package dk.sdu.cloud.file.gateway.api

import dk.sdu.cloud.file.api.StorageFile

val DEFAULT_RESOURCES_TO_LOAD = FileResource.values().joinToString(",") { it.text }

internal const val FAVORITES_BACKEND = "favorites"
internal const val STORAGE_BACKEND = "storage"

enum class FileResource(val text: String, internal val backend: String) {
    FAVORITES("favorited", FAVORITES_BACKEND),

    FILE_TYPE("fileType", STORAGE_BACKEND),
    PATH("path", STORAGE_BACKEND),
    CREATED_AT("createdAt", STORAGE_BACKEND),
    MODIFIED_AT("modifiedAt", STORAGE_BACKEND),
    OWNER_NAME("ownerName", STORAGE_BACKEND),
    SIZE("size", STORAGE_BACKEND),
    ACL("acl", STORAGE_BACKEND),
    SENSITIVITY_LEVEL("sensitivityLevel", STORAGE_BACKEND),
    OWN_SENSITIVITY_LEVEL("ownSensitivityLevel", STORAGE_BACKEND),
    LINK("link", STORAGE_BACKEND),
    FILE_ID("fileId", STORAGE_BACKEND),
    CREATOR("creator", STORAGE_BACKEND)
}

internal fun fileResourcesToString(load: Set<FileResource>) =
    load.joinToString(",") { it.text }

interface LoadFileResource {
    val attributes: String?
}

val LoadFileResource.resourcesToLoad: Set<FileResource>
    get() = (attributes ?: DEFAULT_RESOURCES_TO_LOAD).split(",").mapNotNull { param ->
        FileResource.values().find { it.text == param }
    }.toSet().normalize()

class StorageFileWithMetadata(
    delegate: StorageFile,

    // custom resources
    val favorited: Boolean?
) : StorageFile by delegate

private fun Set<FileResource>.normalize(): Set<FileResource> {
    val result = HashSet(this)
    if (FileResource.FAVORITES in result) {
        result += FileResource.FILE_ID
    }
    return result
}
