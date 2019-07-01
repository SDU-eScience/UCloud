package dk.sdu.cloud.file.services.acl

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.parents
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

    suspend fun handleFilesMoved(from: List<String>, to: List<String>) {
        if (from.size != to.size) throw IllegalArgumentException("from.size != to.size")
        if (from.isEmpty()) return

        val observedChanges = HashMap<String, String>()

        db.withTransaction {
            from.zip(to).sortedBy { it.first.length }.forEach { (rawOld, rawNew) ->
                val old = rawOld.normalize()
                val new = rawNew.normalize()

                val canSkipUpdate = old.parents().map { it.normalize() }.any { oldParent ->
                    // If we haven't observed another change here then we need to update.
                    val newPrefix = observedChanges[oldParent] ?: return@any false

                    // We have observed a change to this parent which would have affected this one. We need to
                    // determine if these are related or not.

                    // First we check if the prefixes are changing in the same way.
                    if (!new.startsWith("$newPrefix/")) return@any false

                    // Secondly we need to make sure that _only_ the prefix has changed. This means that the suffix
                    // of old must match the new suffix. The suffix is anything after the prefix.

                    // TODO Should this be an assert instead?
                    if (newPrefix.length >= new.length) return@any false

                    val newSuffix = new.substring(newPrefix.length + 1)
                    val oldSuffix = old.substring(oldParent.length + 1)

                    newSuffix == oldSuffix
                }

                observedChanges[old] = new

                if (!canSkipUpdate) {
                    dao.handleFilesMoved(it, old, new)
                }
            }
        }
    }

    suspend fun handleFilesDeleted(paths: List<String>) {
        if (paths.isEmpty()) return

        val filesToDelete = HashSet<String>()
        paths.asSequence().sortedBy { it.length }.map { it.normalize() }.forEach { path ->
            if (path.parents().all { it.normalize() !in filesToDelete }) {
                filesToDelete.add(path)
            }
        }

        filesToDelete.chunked(64).forEach { chunk ->
            db.withTransaction {
                dao.handleFilesDeleted(it, chunk)
            }
        }
    }

    fun dumpAllForDebugging(): Map<String, List<UserWithPermissions>> {
        db.withTransaction {
            return dao.dumpAllForDebugging(it)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
