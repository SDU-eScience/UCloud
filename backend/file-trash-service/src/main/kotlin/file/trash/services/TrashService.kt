package dk.sdu.cloud.file.trash.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.task.api.Progress
import dk.sdu.cloud.task.api.runTask
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class TrashService(
    private val trashDirectoryService: TrashDirectoryService,
    private val wsServiceClient: AuthenticatedClient,
    private val backgroundScope: BackgroundScope
) {
    suspend fun emptyTrash(username: String, userCloud: AuthenticatedClient, suggestedTrashFolder: String?) {
        val actualTrashFolder = suggestedTrashFolder ?: trashDirectoryService.findPersonalTrash(username)
        if (!trashDirectoryService.isTrashFolder(username, actualTrashFolder)) {
            throw RPCException("Invalid trash folder", HttpStatusCode.BadRequest)
        }

        validateTrashDirectory(actualTrashFolder, userCloud, username)
        backgroundScope.launch {
            runTask(wsServiceClient, backgroundScope, "Emptying trash", username) {
                runCatching {
                    this.status = "Emptying trash"
                    for (attempt in 1..5) {
                        val filesResp = FileDescriptions.listAtPath.call(
                            ListDirectoryRequest(
                                actualTrashFolder,
                                itemsPerPage = 100,
                                page = 0,
                                order = null,
                                sortBy = null
                            ),
                            userCloud
                        )

                        val progress = Progress("Number of files", 0, filesResp.orThrow().itemsInTotal)
                        this.progress = progress

                        if (filesResp.statusCode == HttpStatusCode.NotFound) return@launch
                        val files = filesResp.orThrow()
                        if (files.items.isEmpty()) return@launch

                        files.items.map {
                            launch {
                                FileDescriptions.deleteFile.call(
                                    DeleteFileRequest(it.path),
                                    userCloud
                                )
                                progress.current++
                            }
                        }.joinAll()
                    }
                }
            }
        }
    }

    suspend fun moveFilesToTrash(files: List<String>, username: String, userCloud: AuthenticatedClient) {
        try {
            val trashFolders = files.map { trashDirectoryService.findTrashDirectory(username, it) }.toSet()
            trashFolders.forEach { folder -> validateTrashDirectory(folder, userCloud, username) }
        } catch (ex: Throwable) {
            log.warn(ex.stackTraceToString())
            throw ex
        }

        backgroundScope.launch {
            runTask(wsServiceClient, backgroundScope, "Moving files to trash", username) {
                this.status = "Moving files to trash"
                val progress = Progress("Number of files", 0, files.size)
                this.progress = progress

                files.forEach {
                    launch {
                        try {
                            moveFileToTrash(it, trashDirectoryService.findTrashDirectory(username, it), userCloud)
                            progress.current++
                        } catch (ex: Throwable) {
                            log.warn(ex.stackTraceToString())
                        }
                    }
                }
            }
        }
    }

    private suspend fun moveFileToTrash(file: String, trashFolder: String, userCloud: AuthenticatedClient): Boolean {
        val result = FileDescriptions.move.call(
            MoveRequest(
                path = file,
                newPath = joinPath(trashFolder, file.fileName()),
                policy = WriteConflictPolicy.RENAME
            ),
            userCloud
        )

        return result.statusCode.isSuccess()
    }

    private suspend fun validateTrashDirectory(
        trashDirectoryPath: String,
        userCloud: AuthenticatedClient,
        username: String
    ) {
        suspend fun createTrashDirectory() {
            val trashComponents = trashDirectoryPath.components()
            if (trashComponents.size >= 2 && trashComponents[0] == "projects") {
                FileDescriptions.createPersonalRepository.call(
                    CreatePersonalRepositoryRequest(
                        trashComponents[1],
                        username
                    ),
                    wsServiceClient
                )
            }
            FileDescriptions.createDirectory.call(
                CreateDirectoryRequest(trashDirectoryPath, null),
                userCloud
            ).orThrow()
        }

        val statCall = FileDescriptions.stat.call(StatRequest(trashDirectoryPath), userCloud)
        if (statCall.statusCode == HttpStatusCode.NotFound) {
            createTrashDirectory()
        } else {
            val stat = statCall.orThrow()

            if (stat.fileType != FileType.DIRECTORY) {
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

    companion object : Loggable {
        override val log = logger()
    }
}
