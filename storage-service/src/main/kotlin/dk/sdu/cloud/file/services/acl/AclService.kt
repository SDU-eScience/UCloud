package dk.sdu.cloud.file.services.acl

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class AclService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: AclDao<Session>,
    private val homeFolderService: HomeFolderService
) {
    suspend fun createOrUpdatePermission(path: String, username: String, permission: AclPermission) {
        val normalizedPath = path.normalize()

        log.debug("createOrUpdatePermission($normalizedPath, $username, $permission)")
        db.withTransaction {
            dao.createOrUpdatePermission(it, normalizedPath, username, permission)
        }
    }

    suspend fun hasPermission(path: String, username: String, permission: AclPermission): Boolean {
        if (username == SERVICE_USER) return true

        val normalizedPath = path.normalize()
        val homeFolder = homeFolderService.findHomeFolder(username).normalize()
        log.debug("Checking permissions for $username at $path with home folder $homeFolder")
        if (path == homeFolder || path.startsWith("$homeFolder/")) {
            return true
        }

        return db.withTransaction {
            dao.hasPermission(it, normalizedPath, username, permission)
        }
    }

    suspend fun listAcl(paths: List<String>): Map<String, List<UserWithPermissions>> {
        val normalizedPaths = paths.map { it.normalize() }

        return db.withTransaction { dao.listAcl(it, normalizedPaths) }
    }

    suspend fun revokePermission(path: String, username: String) {
        val normalizedPath = path.normalize()

        db.withTransaction {
            dao.revokePermission(it, normalizedPath, username)
        }
    }

    suspend fun handleFilesMoved(path: String) {
        val normalizedPath = path.normalize()

        db.withTransaction {
            dao.handleFilesMoved(it, normalizedPath)
        }
    }

    suspend fun handleFilesDeleted(path: String) {
        val normalizedPath = path.normalize()

        db.withTransaction {
            dao.handleFilesDeleted(it, normalizedPath)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
