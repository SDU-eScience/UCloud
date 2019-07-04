package dk.sdu.cloud.file.services.acl

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.parents
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.file.util.FSException
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
    private val homeFolderService: HomeFolderService,
    private val pathNormalizer: (String) -> String?
) {
    suspend fun updatePermissions(path: String, username: String, permissions: Set<AccessRight>) {
        val normalizedPath = pathNormalizer(path) ?: throw FSException.NotFound()

        log.debug("updatePermissions($normalizedPath, $username, $permissions)")
        db.withTransaction {
            dao.updatePermissions(it, normalizedPath, username, permissions)
        }
    }

    private suspend fun internalIsOwner(normalizedPath: String, username: String): Boolean {
        val homeFolder = homeFolderService.findHomeFolder(username).normalize()
        log.debug("user is '$username' requesting path '$normalizedPath' and home is '$homeFolder'")
        if (normalizedPath == homeFolder || normalizedPath.startsWith("$homeFolder/")) {
            log.debug("We are the owner")
            return true
        }

        return false
    }

    suspend fun isOwner(path: String, username: String): Boolean {
        return internalIsOwner(pathNormalizer(path) ?: return false, username)
    }

    suspend fun hasPermission(path: String, username: String, permission: AccessRight): Boolean {
        if (username == SERVICE_USER) return true

        val normalizedPath = pathNormalizer(path) ?: run {
            log.debug("pathNormalizer for $path returned null!")
            return false
        }
        if (internalIsOwner(normalizedPath, username)) return true

        return db.withTransaction {
            dao.hasPermission(it, normalizedPath, username, permission)
        }
    }

    suspend fun listAcl(
        paths: List<String>
    ): Map<String, List<UserWithPermissions>> {
        val normalizedPaths = paths.mapNotNull { it to (pathNormalizer(it) ?: return@mapNotNull null) }
        log.debug("NormalizedPaths: $normalizedPaths")

        val allParents = normalizedPaths.flatMap { it.second.parents() }.map { it.normalize() }.toSet()

        log.debug("Gathering ACLs from: $allParents")

        val res = db.withTransaction {
            dao.listAcl(it, normalizedPaths.map { it.second } + allParents)
        }

        log.debug("Result is $res")

        return normalizedPaths.map { (originalPath, path) ->
            val acl = res[path] ?: emptyList()
            val aclEntriesFromParents = path.parents().flatMap { res[it.normalize()] ?: emptyList() }

            originalPath to (acl + aclEntriesFromParents)
        }.toMap().also {
            log.debug("Merged result is: $it")
        }
    }

    suspend fun listAclsForChildrenOf(
        path: String,
        knownChildren: List<String>
    ): Map<String, List<UserWithPermissions>> {
        val normalizedPath = pathNormalizer(path) ?: return emptyMap()
        val allParents = normalizedPath.parents().map { it.normalize() }.toSet() + setOf(normalizedPath)

        lateinit var childrenAcls: Map<String, List<UserWithPermissions>>
        lateinit var parentAcls: Map<String, List<UserWithPermissions>>

        db.withTransaction { session ->
            childrenAcls = dao.listAclsForChildrenOf(session, normalizedPath)
            parentAcls = dao.listAcl(session, allParents.toList())
        }

        return knownChildren.mapNotNull { originalPath ->
            val normalizedChild = pathNormalizer(originalPath) ?: return@mapNotNull null

            val aclEntriesFromChildren = childrenAcls[normalizedChild] ?: emptyList()
            val aclEntriesFromParents =
                normalizedChild.parents().flatMap { parentAcls[it.normalize()] ?: emptyList() }

            val unmergedAcl = aclEntriesFromChildren + aclEntriesFromParents

            originalPath to unmergedAcl.groupBy { it.username }.map { (username, entries) ->
                UserWithPermissions(username, entries.flatMap { it.permissions }.toSet())
            }
        }.toMap()
    }

    suspend fun revokePermission(path: String, username: String) {
        val normalizedPath = pathNormalizer(path) ?: throw FSException.NotFound()

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
                // Note: We don't need to resolve symlinks here (in fact we don't want to)
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
