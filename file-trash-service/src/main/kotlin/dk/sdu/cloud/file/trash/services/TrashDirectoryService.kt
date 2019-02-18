package dk.sdu.cloud.file.trash.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.file.api.joinPath
import java.util.concurrent.ConcurrentHashMap

class TrashDirectoryService(
    private val serviceClient: AuthenticatedClient
) {
    private val cachedResults = ConcurrentHashMap<String, String>()

    suspend fun findTrashDirectory(username: String): String {
        val cachedResult = cachedResults[username]
        if (cachedResult != null) return cachedResult

        val homeFolder = FileDescriptions.findHomeFolder.call(
            FindHomeFolderRequest(username),
            serviceClient
        ).orThrow().path
        return joinPath(homeFolder, "Trash").also {
            cachedResults[username] = it
        }
    }
}
