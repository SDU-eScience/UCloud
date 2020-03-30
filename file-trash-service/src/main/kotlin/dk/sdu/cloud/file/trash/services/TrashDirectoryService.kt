package dk.sdu.cloud.file.trash.services

import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.normalize

class TrashDirectoryService {
    fun findPersonalTrash(username: String): String {
        return "/home/$username/Trash"
    }

    fun findTrashDirectory(username: String, targetPath: String): String {
        val components = targetPath.components()
        return if (components.size >= 3 && components[0] == "projects") {
            "/projects/${components[1]}/${components[2]}/Trash"
        } else {
            findPersonalTrash(username)
        }
    }

    fun isTrashFolder(username: String, path: String): Boolean {
        val components = path.components()
        if (components.size < 2) return false
        return when {
            components[0] == "home" -> path.normalize() == findPersonalTrash(username)
            components[1] == "projects" -> components.size > 3 && components[2] == "Trash"
            else -> false
        }
    }
}
