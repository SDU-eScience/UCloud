package dk.sdu.cloud.file.services.acl

import com.fasterxml.jackson.module.kotlin.treeToValue
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FindMetadataRequest
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.parents
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.service.Loggable

/**
 * UCloud uses Access Control Lists (ACLs) for controlling access to files and directories.
 *
 * All files and directories in UCloud have an associated ACL. The ACL contains a set of permissions for a given user.
 *
 * The permissions that a user can be granted for a single file is documented in [AccessRight].
 *
 * The ACLs of UCloud apply to _all_ children of a file. This means that a user do not need permissions on a
 * specific file but can instead rely on permissions given by a parent. Consider the following example:
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
class AclService(
    private val metadataService: MetadataService,
    private val homeFolderService: HomeFolderService
) {
    private data class AclMetadata(
        val permissions: Set<AccessRight>
    )

    suspend fun updatePermissions(path: String, username: String, permissions: Set<AccessRight>) {
        val normalizedPath = path.normalize()
        log.debug("updatePermissions($normalizedPath, $username, $permissions)")

        metadataService.updateMetadata(
            Metadata(
                normalizedPath,
                METADATA_TYPE,
                username,
                defaultMapper.valueToTree(AclMetadata(permissions))
            )
        )
    }

    private suspend fun internalIsOwner(normalizedPath: String, username: String): Boolean {
        val homeFolder = homeFolderService.findHomeFolder(username).normalize()
        log.trace("user is '$username' requesting path '$normalizedPath' and home is '$homeFolder'")
        if (normalizedPath == homeFolder || normalizedPath.startsWith("$homeFolder/")) {
            log.trace("We are the owner")
            return true
        }

        return false
    }

    suspend fun isOwner(path: String, username: String): Boolean {
        return internalIsOwner(path.normalize(), username)
    }

    suspend fun hasPermission(path: String, username: String, permission: AccessRight): Boolean {
        if (username == SERVICE_USER) return true

        val normalizedPath = path.normalize()
        if (internalIsOwner(normalizedPath, username)) return true
        val relevantPaths = normalizedPath.parents().map { it.normalize() } + listOf(normalizedPath)

        return metadataService
            .listMetadata(relevantPaths, username, METADATA_TYPE)
            .flatMap { it.value }
            .any { data ->
                val aclMetadata = defaultMapper.treeToValue<AclMetadata>(data.payload)
                permission in aclMetadata.permissions
            }
    }

    suspend fun listAcl(
        paths: List<String>
    ): Map<String, List<UserWithPermissions>> {
        val normalizedPaths = paths.map { it to it.normalize() }

        return normalizedPaths
            .chunked(200)
            .flatMap { normalizedChunk ->
                val flatPermissions = HashMap<String, List<UserWithPermissions>>()
                val allParents = normalizedChunk.flatMap { it.second.parents() }.map { it.normalize() }.toSet()
                metadataService
                    .listMetadata(
                        normalizedChunk.map { it.second } + allParents,
                        null,
                        METADATA_TYPE
                    )
                    .forEach { dataList ->
                        dataList.value.forEach { data ->
                            if (data.username == null) {
                                log.warn("ACL metadata with null user detected!")
                            } else {
                                val aclMetadata = defaultMapper.treeToValue<AclMetadata>(data.payload)
                                flatPermissions[data.path] = (flatPermissions[data.path] ?: emptyList()) +
                                        listOf(UserWithPermissions(data.username, aclMetadata.permissions))
                            }
                        }
                    }

                normalizedChunk.map { (originalPath, path) ->
                    val acl = flatPermissions[path] ?: emptyList()
                    val aclEntriesFromParents = path
                        .parents()
                        .flatMap { flatPermissions[it.normalize()] ?: emptyList() }

                    val unmergedAcl = acl + aclEntriesFromParents

                    Pair(
                        originalPath,
                        unmergedAcl
                            .groupBy { it.username }
                            .map { (username, entries) ->
                                UserWithPermissions(username, entries.flatMap { it.permissions }.toSet())
                            }
                    )
                }
            }
            .toMap()
    }

    suspend fun revokePermission(path: String, username: String) {
        val normalizedPath = path.normalize()
        metadataService.removeEntries(listOf(FindMetadataRequest(normalizedPath, username, METADATA_TYPE)))
    }

    companion object : Loggable {
        override val log = logger()

        private const val METADATA_TYPE = "acl"
    }
}
