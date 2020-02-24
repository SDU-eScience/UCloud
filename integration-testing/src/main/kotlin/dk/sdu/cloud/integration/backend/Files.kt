package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.ListDirectoryRequest
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.path

val UserAndClient.homeFolder: String
    get() = "/home/$username"

suspend fun UserAndClient.createDir(vararg components: String) {
    FileDescriptions.createDirectory.call(
        CreateDirectoryRequest(
            path = joinPath(homeFolder, *components),
            owner = null
        ),
        client
    ).orThrow()
}

fun UserAndClient.requireFile(list: List<StorageFile>, type: FileType, fileName: String) {
    val result = list.find {
        it.path.fileName() == fileName
    } ?: throw IllegalArgumentException("${fileName.toList()} was not in output ${list.map { it.path }}")

    if (result.fileType != type) {
        throw IllegalArgumentException("Invalid type of $fileName. Was ${result.fileType} and not $type")
    }
}

suspend fun UserAndClient.listAt(vararg components: String): List<StorageFile> {
    return FileDescriptions.listAtPath.call(
        ListDirectoryRequest(
            joinPath(homeFolder, *components),
            itemsPerPage = 100,
            page = 0,
            order = null,
            sortBy = null
        ),
        client
    ).orThrow().items
}
