package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.joinPath
import io.ktor.http.*

class TrashService(
    private val pathConverter: PathConverter,
) {
    private fun findPersonalTrash(username: String): InternalFile {
        return pathConverter.relativeToInternal(
            RelativeInternalFile("/${PathConverter.HOME_DIRECTORY}/$username/$TRASH_FOLDER")
        )
    }

    fun findTrashDirectory(username: String, targetPath: InternalFile): InternalFile {
        val components = targetPath.components()
        if (username.contains("..") || username.contains("/")) {
            throw RPCException("Bad username", HttpStatusCode.BadRequest)
        }

        return if (components.size >= 3 && components[0] == "projects") {
            pathConverter.relativeToInternal(
                RelativeInternalFile(
                    joinPath(
                        PathConverter.PROJECT_DIRECTORY,
                        components[1],
                        PERSONAL_REPOSITORY,
                        username,
                        TRASH_FOLDER
                    )
                )
            )
        } else {
            findPersonalTrash(username)
        }
    }

    fun isTrashFolder(username: String?, file: InternalFile): Boolean {
        val components = pathConverter.internalToRelative(file).components()
        if (components.size < 2) return false
        return when {
            components[0] == "home" -> (username != null && file == findPersonalTrash(username)) ||
                (username == null && components.size == 3 && components[2] == TRASH_FOLDER)
            components[0] == "projects" -> components.size == 5 && components[4] == TRASH_FOLDER
            else -> false
        }
    }

    companion object {
        const val TRASH_FOLDER = "Trash"
    }
}