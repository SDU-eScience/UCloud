package dk.sdu.cloud.file.trash.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.DeleteFileRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.ListDirectoryRequest
import dk.sdu.cloud.file.api.MoveRequest
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.joinPath
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class TrashService(
    private val trashDirectoryService: TrashDirectoryService
) {
    suspend fun emptyTrash(username: String, userCloud: AuthenticatedClient) {
        validateTrashDirectory(username, userCloud)
        GlobalScope.launch {
            runCatching {
                for (attempt in 1..5) {
                    val filesResp = FileDescriptions.listAtPath.call(
                        ListDirectoryRequest(
                            trashDirectoryService.findTrashDirectory(username),
                            itemsPerPage = 100,
                            page = 0,
                            order = null,
                            sortBy = null
                        ),
                        userCloud
                    )

                    if (filesResp.statusCode == HttpStatusCode.NotFound) return@launch
                    val files = filesResp.orThrow()
                    if (files.items.isEmpty()) return@launch

                    files.items.map {
                        launch {
                            FileDescriptions.deleteFile.call(
                                DeleteFileRequest(it.path),
                                userCloud
                            )
                        }
                    }.joinAll()
                }
            }
        }
    }

    suspend fun moveFilesToTrash(files: List<String>, username: String, userCloud: AuthenticatedClient): List<String> {
        return coroutineScope {
            validateTrashDirectory(username, userCloud)

            val results = files.map { async { it to moveFileToTrash(it, username, userCloud) } }.awaitAll()
            results.mapNotNull { if (!it.second) it.first else null }
        }
    }

    private suspend fun moveFileToTrash(file: String, username: String, userCloud: AuthenticatedClient): Boolean {
        val result = FileDescriptions.move.call(
            MoveRequest(
                path = file,
                newPath = joinPath(trashDirectoryService.findTrashDirectory(username), file.fileName()),
                policy = WriteConflictPolicy.RENAME
            ),
            userCloud
        )

        return result.statusCode.isSuccess()
    }

    private suspend fun validateTrashDirectory(username: String, userCloud: AuthenticatedClient) {
        val trashDirectoryPath = trashDirectoryService.findTrashDirectory(username)

        suspend fun createTrashDirectory() {
            FileDescriptions.createDirectory.call(
                CreateDirectoryRequest(trashDirectoryPath, null),
                userCloud
            ).orThrow()
        }

        val statCall = FileDescriptions.stat.call(FindByPath(trashDirectoryPath), userCloud)
        if (statCall.statusCode == HttpStatusCode.NotFound) {
            createTrashDirectory()
        } else {
            val stat = statCall.orThrow()

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
