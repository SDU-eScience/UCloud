package dk.sdu.cloud.file.services.acl

import com.fasterxml.jackson.module.kotlin.treeToValue
import dk.sdu.cloud.Actor
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.file.services.ProjectCache
import dk.sdu.cloud.file.services.SynchronizedFoldersTable
import dk.sdu.cloud.file.synchronization.services.SyncthingClient
import dk.sdu.cloud.file.withMounterInfo
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.sync.mounter.api.*
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

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
    private val serviceClient: AuthenticatedClient,
    private val projectCache: ProjectCache,
    private val db: AsyncDBSessionFactory,
    private val syncthing: SyncthingClient
) {
    @Serializable
    private data class UserAclMetadata(val permissions: Set<AccessRight>)
    @Serializable
    private data class ProjectAclEntity(val group: String, val permissions: Set<AccessRight>)
    @Serializable
    private data class ProjectAclMetadata(val entries: List<ProjectAclEntity>)

    suspend fun updateAcl(request: UpdateAclRequest, user: String) {
        log.debug("Executing ACL update request: $request")

        if (!isOwner(request.path, user)) {
            throw RPCException("Only the owner can update the ACL", HttpStatusCode.Forbidden)
        }

        val bulkChanges = ArrayList<AclEntryRequest>()
        request.changes.forEach { change ->
            if (change.revoke) {
                revokePermission(request.path, change.entity)
            } else {
                bulkChanges.add(change)
            }
        }

        updateUserPermissions(request.path, bulkChanges)
    }

    private suspend fun updateUserPermissions(path: String, entries: List<AclEntryRequest>) {
        val normalizedPath = path.normalize()
        log.debug("updateUserPermissions($normalizedPath, $entries)")

        for (entry in entries) {
            require(!entry.revoke)

            val entity = entry.entity
            val permissions = entry.rights
            metadataService.updateMetadata(
                Metadata(
                    normalizedPath,
                    USER_METADATA_TYPE,
                    entity.username,
                    defaultMapper.encodeToJsonElement(UserAclMetadata(permissions)) as JsonObject
                )
            )
        }
    }

    private suspend fun revokePermission(path: String, entity: ACLEntity.User) {
        val normalizedPath = path.normalize()
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

    suspend fun updateProjectAcl(request: UpdateProjectAclRequest, username: String) {
        if (!isOwner(request.path, username)) {
            throw RPCException("Only the owner can update the ACL", HttpStatusCode.Forbidden)
        }

        val pathComponents = request.path.components()
        if (pathComponents.size == 3 && pathComponents[0] == "projects" && pathComponents[2] == PERSONAL_REPOSITORY) {
            throw RPCException("Cannot update permissions of '${PERSONAL_REPOSITORY}' repository", HttpStatusCode.Forbidden)
        }

        metadataService.updateMetadata(
            Metadata(
                request.path.normalize(),
                PROJECT_METADATA_TYPE,
                request.project,
                defaultMapper.encodeToJsonElement(
                    ProjectAclMetadata(
                        request.newAcl.map { entry ->
                            ProjectAclEntity(entry.group, entry.rights)
                        }
                    )
                ) as JsonObject
            )
        )

        updateSynchronizationType(request.path.normalize())
    }

    suspend fun isOwner(path: String, username: String): Boolean {
        val normalizedPath = path.normalize()
        if (normalizedPath.startsWith("/home/")) {
            val homeFolder = homeFolderService.findHomeFolder(username).normalize()
            if (normalizedPath == homeFolder || normalizedPath.startsWith("$homeFolder/")) {
                return true
            }
        } else if (normalizedPath.startsWith("/projects/")) {
            val components = normalizedPath.components()
            if (components.size < 2) return false
            val projectId = components[1]
            val viewMember = projectCache.viewMember(projectId, username) ?: return false

            // Note: Even if username matches we must be a member of the project. This allows us to keep files after
            // a user leaves.
            if (components.size >= 4 && components[2] == PERSONAL_REPOSITORY && components[3] == username) {
                return true
            }

            return viewMember.role.isAdmin()
        }

        return false
    }

    suspend fun hasPermission(path: String, username: String, permission: AccessRight): Boolean {
        if (username == SERVICE_USER) return true

        val normalizedPath = path.normalize()

        if (isOwner(normalizedPath, username)) return true
        val relevantPaths = normalizedPath.parents().map { it.normalize() } + listOf(normalizedPath)

        val hasPermissionAsUser = metadataService
            .listMetadata(relevantPaths, username, USER_METADATA_TYPE)
            .flatMap { it.value }
            .any { data ->
                val aclMetadata = defaultMapper.decodeFromJsonElement<UserAclMetadata>(data.payload)
                permission in aclMetadata.permissions
            }

        if (hasPermissionAsUser) return true

        val queries = metadataService
            .listMetadata(relevantPaths, null, PROJECT_METADATA_TYPE)
            .flatMap { it.value }
            .flatMap { data ->
                defaultMapper.decodeFromJsonElement<ProjectAclMetadata>(data.payload).entries.map {
                    data.username to it
                }
            }
            .filter { (project, entry) -> project != null && permission in entry.permissions }
            .map { (project, entry) -> IsMemberQuery(project!!, entry.group, username) }

        if (queries.isEmpty()) {
            return false
        }

        for (projectId in queries.map { it.project }.toSet()) {
            val isAdmin = projectCache.viewMember(projectId, username)?.role?.isAdmin() == true
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
                                val aclMetadata = defaultMapper.decodeFromJsonElement<UserAclMetadata>(data.payload)
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
                                val aclMetadata = defaultMapper.decodeFromJsonElement<ProjectAclMetadata>(data.payload)
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

    private suspend fun updateSynchronizationType(path: String) {
        data class SynchronizedFolderWithNewAccess(
            val id: String,
            val user: String,
            val path: String,
            val oldType: SynchronizationType,
            val newType: SynchronizationType?
        )

        val changes = db.withSession { session ->
            val syncedChildren = session.sendPreparedStatement(
                {
                    setParameter("path", path)
                },
                """
                    select id, user_id, path, access_type from storage.synchronized_folders
                    where path like :path || '/%'
                """
            ).rows

            val changes: List<SynchronizedFolderWithNewAccess> = syncedChildren.map {
                val newType = if (hasPermission(
                        it.getField(SynchronizedFoldersTable.path),
                        it.getField(SynchronizedFoldersTable.user),
                        AccessRight.WRITE
                    )
                ) {
                    SynchronizationType.SEND_RECEIVE
                } else if (hasPermission(
                        it.getField(SynchronizedFoldersTable.path),
                        it.getField(SynchronizedFoldersTable.user),
                        AccessRight.READ
                    )
                ) {
                    SynchronizationType.SEND_ONLY
                } else {
                    null
                }

                SynchronizedFolderWithNewAccess(
                    it.getField(SynchronizedFoldersTable.id),
                    it.getField(SynchronizedFoldersTable.user),
                    it.getField(SynchronizedFoldersTable.path),
                    SynchronizationType.valueOf(it.getField(SynchronizedFoldersTable.accessType)),
                    newType
                )
            }

            val toUpdate = changes.filter { it.newType != null }
            val toDelete = changes.filter { it.newType == null }

            if (toUpdate.isNotEmpty()) {
                session.sendPreparedStatement(
                    {
                        setParameter("ids", toUpdate.map { it.id })
                        setParameter("users", toUpdate.map { it.user })
                        setParameter("access", toUpdate.map { it.newType })
                    },
                    """
                        update storage.synchronized_folders f
                        set access_type = new.access
                        from (
                            select
                                unnest(:ids::text[]) as id,
                                unnest(:users::text[]) as user,
                                unnest(:access::text[]) as access
                        ) as new
                        where f.id = new.id and f.user_id = new.user
                    """
                )
            }

            if (toDelete.isNotEmpty()) {
                val devices = session.sendPreparedStatement(
                    {
                        setParameter("ids", toDelete.map { it.id })
                    },
                    """
                        delete from storage.synchronized_folders f
                        where f.id in (select unnest(:ids::text[]))
                        returning id, device_id
                    """
                ).rows.mapNotNull {
                    val id = it.getString(0)!!
                    val deviceId = it.getString(1)!!
                    val device = syncthing.config.devices.find { it.id == deviceId }
                    if (device != null) {
                        id to device
                    } else {
                        null
                    }
                }
                val grouped = devices.groupBy { it.second.id }
                grouped.forEach { (_, requests) ->
                    Mounts.unmount.call(
                        UnmountRequest(
                            requests.map { MountFolderId(it.first) }
                        ),
                        serviceClient.withMounterInfo(requests[0].second)
                    ).orRethrowAs {
                        throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                    }
                }
            }

            changes.size
        }

        if (changes > 0) {
            syncthing.writeConfig()
        }
    }

    companion object : Loggable {
        override val log = logger()

        const val USER_METADATA_TYPE = "acl"
        const val PROJECT_METADATA_TYPE = "project-acl"
    }
}
