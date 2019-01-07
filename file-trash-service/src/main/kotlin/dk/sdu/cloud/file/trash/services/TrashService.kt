package dk.sdu.cloud.file.trash.services

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.DeleteFileRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.file.api.MoveRequest
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.orThrow
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

suspend fun trashDirectory(username: String, cloud: AuthenticatedCloud): String {
    val homeFolder = FileDescriptions.findHomeFolder.call(
        FindHomeFolderRequest(username),
        cloud
    ).orThrow().path
    return joinPath(homeFolder, "Trash")
}

class TrashService {

    suspend fun emptyTrash(username: String, userCloud: AuthenticatedCloud) {
        validateTrashDirectory(username, userCloud)
        FileDescriptions.deleteFile.call(DeleteFileRequest(trashDirectory(username, userCloud)), userCloud)
    }

    suspend fun moveFilesToTrash(files: List<String>, username: String, userCloud: AuthenticatedCloud): List<String> {
        return coroutineScope {
            validateTrashDirectory(username, userCloud)

            val results = files.map { async { it to moveFileToTrash(it, username, userCloud) } }.awaitAll()
            results.mapNotNull { if (!it.second) it.first else null }
        }
    }

    private suspend fun moveFileToTrash(file: String, username: String, userCloud: AuthenticatedCloud): Boolean {
        val result = FileDescriptions.move.call(
            MoveRequest(
                path = file,
                newPath = joinPath(trashDirectory(username, userCloud), file.fileName())
            ),
            userCloud
        )

        return result.status in 200..299
    }

    private suspend fun validateTrashDirectory(username: String, userCloud: AuthenticatedCloud) {
        val trashDirectoryPath = trashDirectory(username, userCloud)

        suspend fun createTrashDirectory() {
            FileDescriptions.createDirectory.call(
                CreateDirectoryRequest(trashDirectoryPath, null),
                userCloud
            ).orThrow()
        }

        val statCall = FileDescriptions.stat.call(FindByPath(trashDirectoryPath), userCloud)
        if (statCall.status == HttpStatusCode.NotFound.value) {
            createTrashDirectory()
        } else {
            val stat = (statCall as? RESTResponse.Ok)?.result ?: throw RPCException.fromStatusCode(
                HttpStatusCode.fromValue(
                    statCall.status
                )
            )

            if (stat.link || stat.fileType != FileType.DIRECTORY) {
                FileDescriptions.move.call(
                    MoveRequest(trashDirectoryPath, trashDirectoryPath, WriteConflictPolicy.RENAME),
                    userCloud
                ).orThrow()

                createTrashDirectory()
            } else {
                // No further action needed, trash directory already exists
            }
        }
    }
}
