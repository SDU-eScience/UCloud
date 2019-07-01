package dk.sdu.cloud.file.services.acl

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

/**
 * SDUCloud uses Access Control Lists (ACLs) for controlling access to files and directories.
 *
 * All files and directories in SDUCloud have an associated ACL. The ACL contains a set of permissions for a given user.
 *
 * The permissions that a user can be granted for a single file is documented in [AccessRight].
 *
 * The ACLs of SDUCloud apply to _all_ children of a file. This means that a user do not need permissions on a
 * specfic file but can instead rely on permissions given by a parent. Consider the following example:
 *
 * Alice is the owner of /home/alice/shared and shares it with Bob by granting Bob [AccessRight.READ] to
 * /home/alice/shared.
 *
 * If Bob wants to read /home/alice/shared he is allowed to do so because of permissions granted by the ACL in
 * /home/alice/shared. Bob is also allowed to read files in /home/alice/shared/going/deeper/into/fs because of
 * permissions granted by /home/alice/shared. Bob is not allowed to read files in /home/alice because he does not
 * have [AccessRight.READ] in /home/alice or /home.
 *
 * All users are implicitly granted full permissions to their own home directory.
 */
class AclService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: AclDao<Session>,
    private val homeFolderService: HomeFolderService
) {
    suspend fun updatePermissions(path: String, username: String, permissions: Set<AccessRight>) {
        val normalizedPath = path.normalize()

        log.debug("updatePermissions($normalizedPath, $username, $permissions)")
        db.withTransaction {
            dao.updatePermissions(it, normalizedPath, username, permissions)
        }
    }

    suspend fun hasPermission(path: String, username: String, permission: AccessRight): Boolean {
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
