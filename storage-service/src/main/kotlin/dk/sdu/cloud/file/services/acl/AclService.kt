package dk.sdu.cloud.file.services.acl

import com.fasterxml.jackson.module.kotlin.treeToValue
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

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
    private val homeFolderService: HomeFolderService,
    private val serviceClient: AuthenticatedClient
) {
    private data class UserAclMetadata(val permissions: Set<AccessRight>)
    private data class ProjectAclEntity(val group: String, val permissions: Set<AccessRight>)
    private data class ProjectAclMetadata(val entries: List<ProjectAclEntity>)

    suspend fun updateAcl(request: UpdateAclRequest, user: String) {
        log.debug("Executing ACL update request: $request")

        if (!isOwner(request.path, user)) {
            throw RPCException("Only the owner can update the ACL", HttpStatusCode.Forbidden)
        }

        val bulkChanges = ArrayList<UserWithPermissions>()
        request.changes.forEach { change ->
            if (change.revoke) {
                revokePermission(request.path, change.entity)
            } else {
                bulkChanges.add(UserWithPermissions(change.entity, change.rights))
            }
        }

        updatePermissions(request.path, bulkChanges)
    }

    suspend fun updatePermissions(path: String, entries: List<UserWithPermissions>) {
        val normalizedPath = path.normalize()
        log.debug("updatePermissions($normalizedPath, $entries)")

        val metadataByProject = HashMap<String, List<ProjectAclEntity>>()
        for (entry in entries) {
            val entity = entry.entity
            val permissions = entry.permissions

            when (entity) {
                is ACLEntity.User -> {
                    metadataService.updateMetadata(
                        Metadata(
                            normalizedPath,
                            USER_METADATA_TYPE,
                            entity.username,
                            defaultMapper.valueToTree(UserAclMetadata(permissions))
                        )
                    )
                }

                is ACLEntity.ProjectAndGroup -> {
                    val existing = metadataByProject[entity.projectId] ?: emptyList()
                    metadataByProject[entity.projectId] = existing + listOf(ProjectAclEntity(entity.group, permissions))
                }
            }
        }

        for ((project, projectEntries) in metadataByProject) {
            metadataService.updateMetadata(
                Metadata(
                    normalizedPath,
                    PROJECT_METADATA_TYPE,
                    project,
                    defaultMapper.valueToTree(ProjectAclMetadata(projectEntries))
                )
            )
        }
    }

    private suspend fun internalIsOwner(normalizedPath: String, username: String): Boolean {
        if (normalizedPath.startsWith("/home/")) {
            val homeFolder = homeFolderService.findHomeFolder(username).normalize()
            log.trace("user is '$username' requesting path '$normalizedPath' and home is '$homeFolder'")
            if (normalizedPath == homeFolder || normalizedPath.startsWith("$homeFolder/")) {
                log.trace("We are the owner")
                return true
            }
        } else if (normalizedPath.startsWith("/projects/")) {
            val components = normalizedPath.components()
            if (components.size < 2) return false
            val projectId = components[1]

            return Projects.viewMemberInProject.call(
                ViewMemberInProjectRequest(projectId, username),
                serviceClient
            ).orNull()?.member?.role?.isAdmin() == true
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

        val hasPermissionAsUser = metadataService
            .listMetadata(relevantPaths, username, USER_METADATA_TYPE)
            .flatMap { it.value }
            .any { data ->
                val aclMetadata = defaultMapper.treeToValue<UserAclMetadata>(data.payload)
                permission in aclMetadata.permissions
            }

        if (hasPermissionAsUser) return true

        val queries = metadataService
            .listMetadata(relevantPaths, null, PROJECT_METADATA_TYPE)
            .flatMap { it.value }
            .flatMap { data ->
                defaultMapper.treeToValue<ProjectAclMetadata>(data.payload).entries.map { data.username to it }
            }
            .filter { (project, entry) -> project != null && permission in entry.permissions }
            .map { (project, entry) -> IsMemberQuery(project!!, entry.group, username) }

        if (queries.isEmpty()) {
            return false
        }

        for (projectId in queries.map { it.project }.toSet()) {
            val isAdmin = Projects.viewMemberInProject.call(
                ViewMemberInProjectRequest(projectId, username),
                serviceClient
            ).orNull()?.member?.role?.isAdmin() == true

            if (isAdmin) {
                return true
            }
        }

        val responses = ProjectGroups.isMember
            .call(
                IsMemberRequest(queries),
                serviceClient
            )
            .orRethrowAs {
                throw RPCException(
                    "Permission denied - Projects unavailable",
                    HttpStatusCode.Forbidden
                )
            }
            .responses

        return responses.any { it }
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
                        USER_METADATA_TYPE
                    )
                    .forEach { dataList ->
                        dataList.value.forEach { data ->
                            if (data.username == null) {
                                log.warn("ACL metadata with null user detected!")
                            } else {
                                val aclMetadata = defaultMapper.treeToValue<UserAclMetadata>(data.payload)
                                flatPermissions[data.path] = (flatPermissions[data.path] ?: emptyList()) +
                                        listOf(
                                            UserWithPermissions(
                                                ACLEntity.User(data.username),
                                                aclMetadata.permissions
                                            )
                                        )
                            }
                        }
                    }

                metadataService
                    .listMetadata(
                        normalizedChunk.map { it.second } + allParents,
                        null,
                        PROJECT_METADATA_TYPE
                    )
                    .forEach { dataList ->
                        dataList.value.forEach { data ->
                            if (data.username == null) {
                                log.warn("Project ACL metadata with null project detected!")
                            } else {
                                val aclMetadata = defaultMapper.treeToValue<ProjectAclMetadata>(data.payload)
                                flatPermissions[data.path] = (flatPermissions[data.path] ?: emptyList()) +
                                        aclMetadata.entries.map {
                                            UserWithPermissions(
                                                ACLEntity.ProjectAndGroup(data.username, it.group),
                                                it.permissions
                                            )
                                        }
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
                            .groupBy { it.entity }
                            .map { (username, entries) ->
                                UserWithPermissions(username, entries.flatMap { it.permissions }.toSet())
                            }
                    )
                }
            }
            .toMap()
    }

    suspend fun revokePermission(path: String, entity: ACLEntity) {
        val normalizedPath = path.normalize()

        when (entity) {
            is ACLEntity.User -> {
                metadataService.removeEntries(
                    listOf(
                        FindMetadataRequest(
                            normalizedPath,
                            USER_METADATA_TYPE,
                            entity.username
                        )
                    )
                )
            }

            is ACLEntity.ProjectAndGroup -> {
                val metadata = metadataService
                    .findMetadata(
                        normalizedPath,
                        entity.projectId,
                        PROJECT_METADATA_TYPE
                    )
                    ?.payload
                    ?.let { defaultMapper.treeToValue<ProjectAclMetadata>(it) }

                if (metadata == null) {
                    // Do nothing
                } else {
                    val newEntries = metadata.entries.filter { it.group != entity.group }
                    if (newEntries.isEmpty()) {
                        metadataService.removeEntries(
                            listOf(
                                FindMetadataRequest(
                                    normalizedPath,
                                    PROJECT_METADATA_TYPE,
                                    entity.projectId
                                )
                            )
                        )
                    } else {
                        metadataService.updateMetadata(
                            Metadata(
                                normalizedPath,
                                PROJECT_METADATA_TYPE,
                                entity.projectId,
                                defaultMapper.valueToTree(ProjectAclMetadata(newEntries))
                            )
                        )
                    }
                }
            }
        }

    }

    companion object : Loggable {
        override val log = logger()

        private const val USER_METADATA_TYPE = "acl"
        private const val PROJECT_METADATA_TYPE = "project-acl"
    }
}
