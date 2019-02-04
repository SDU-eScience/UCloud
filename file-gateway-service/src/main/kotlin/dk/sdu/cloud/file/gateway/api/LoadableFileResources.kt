package dk.sdu.cloud.file.gateway.api

import dk.sdu.cloud.file.api.StorageFile

const val DEFAULT_RESOURCES_TO_LOAD = "fav"

enum class FileResource(val text: String) {
    FAVORITES("fav")
}

internal fun fileResourcesToString(load: Set<FileResource>) =
    load.joinToString(",") { it.text }

interface LoadFileResource {
    val load: String?
}

val LoadFileResource.resourcesToLoad: Set<FileResource>
    get() = (load ?: DEFAULT_RESOURCES_TO_LOAD).split(",").mapNotNull { param ->
        FileResource.values().find { it.text == param }
    }.toSet()

class StorageFileWithMetadata(
    delegate: StorageFile,

    // custom resources
    val favorited: Boolean?
) : StorageFile by delegate
