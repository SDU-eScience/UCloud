package dk.sdu.cloud.file.trash.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.api.personalDirectory
import io.ktor.http.HttpStatusCode

class TrashDirectoryService {
    fun findPersonalTrash(username: String): String {
        return "/home/$username/$TRASH_FOLDER"
    }

    fun findTrashDirectory(username: String, targetPath: String): String {
        val components = targetPath.components()
        if (username.contains("..") || username.contains("/")) {
            throw RPCException("Bad username", HttpStatusCode.BadRequest)
        }

        return if (components.size >= 3 && components[0] == "projects") {
            joinPath(personalDirectory(components[1], username), TRASH_FOLDER)
        } else {
            findPersonalTrash(username)
        }
    }

    fun isTrashFolder(username: String, path: String): Boolean {
        val components = path.components()
        if (components.size < 2) return false
        return when {
            components[0] == "home" -> path.normalize() == findPersonalTrash(username)
            components[0] == "projects" -> components.size >= 5 && components[4] == TRASH_FOLDER
            else -> false
        }
    }

    companion object {
        const val TRASH_FOLDER = "Trash"
    }
}
