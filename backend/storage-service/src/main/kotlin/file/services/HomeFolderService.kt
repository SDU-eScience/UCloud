package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.homeDirectory

class HomeFolderService {
    fun findHomeFolder(username: String): String {
        return homeDirectory(username)
    }
}
