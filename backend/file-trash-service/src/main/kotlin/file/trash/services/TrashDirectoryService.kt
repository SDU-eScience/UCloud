package dk.sdu.cloud.file.trash.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.normalize
import io.ktor.http.HttpStatusCode

class TrashDirectoryService {
    fun findPersonalTrash(username: String): String {
        return "/home/$username/Trash"
    }

    fun findTrashDirectory(username: String, targetPath: String): String {
        val components = targetPath.components()
        if (username.contains("..") || username.contains("/")) {
            throw RPCException("Bad username", HttpStatusCode.BadRequest)
        }

        return if (components.size >= 3 && components[0] == "projects") {
            "/projects/${components[1]}/Personal/$username/Trash"
        } else {
            findPersonalTrash(username)
        }
    }

    fun isTrashFolder(username: String, path: String): Boolean {
        val components = path.components()
        if (components.size < 2) return false
        return when {
            components[0] == "home" -> path.normalize() == findPersonalTrash(username)
            components[0] == "projects" -> components.size >= 5 && components[4] == "Trash"
            else -> false
        }
    }
}
