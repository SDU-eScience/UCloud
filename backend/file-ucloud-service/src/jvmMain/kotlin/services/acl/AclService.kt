package dk.sdu.cloud.file.ucloud.services.acl

import dk.sdu.cloud.Actor
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.FilePermission
import dk.sdu.cloud.file.orchestrator.api.FilesUpdateAclRequest
import dk.sdu.cloud.file.orchestrator.api.normalize
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

fun homeFolder(username: String): String {
    return "/home/$username"
}

const val PERSONAL_REPOSITORY = "Members' Files"

interface AclService {
    suspend fun isAdmin(actor: Actor, file: UCloudFile): Boolean
    suspend fun updateAcl(actor: Actor, request: FilesUpdateAclRequest)
    suspend fun fetchMyPermissions(actor: Actor, file: UCloudFile): Set<FilePermission>
    suspend fun fetchOtherPermissions(file: UCloudFile): List<ResourceAclEntry<FilePermission>>
}

fun Collection<FilePermission>.normalize(): Set<FilePermission> {
    val result = HashSet<FilePermission>()
    result.addAll(this)
    if (contains(FilePermission.WRITE)) {
        result.add(FilePermission.READ)
        result.add(FilePermission.WRITE)
    }
    return result
}

/**
 * UCloud/Storage uses Access Control Lists (ACLs) for controlling access to files and directories.
 *
 * All files and directories in UCloud have an associated ACL. The ACL contains a set of permissions for a given user.
 *
 * The permissions that a user can be granted for a single file is documented in [FilePermission].
 *
 * The ACLs of UCloud apply to _all_ children of a file. This means that a user do not need permissions on a
 * specific file but can instead rely on permissions given by a parent. Consider the following example:
 *
 * Alice is the owner of /home/alice/shared and shares it with Bob by granting Bob [FilePermission.READ] to
 * /home/alice/shared.
 *
 * If Bob wants to read /home/alice/shared he is allowed to do so because of permissions granted by the ACL in
 * /home/alice/shared. Bob is also allowed to read files in /home/alice/shared/going/deeper/into/fs because of
 * permissions granted by /home/alice/shared. Bob is not allowed to read files in /home/alice because he does not
 * have [FilePermission.READ] in /home/alice or /home.
 *
 * All users are implicitly granted full permissions to their own home directory.
 */
class AclServiceImpl(
    private val serviceClient: AuthenticatedClient,
    private val projectCache: ProjectCache,
    private val pathConverter: PathConverter,
    private val db: DBContext,
    private val metadataDao: MetadataDao,
) : AclService {
    @Serializable
    private data class UserAclMetadata(val permissions: Set<FilePermission>)

    @Serializable
    private data class ProjectAclEntity(val group: String, val permissions: Set<FilePermission>)

    @Serializable
    private data class ProjectAclMetadata(val entries: List<ProjectAclEntity>)

    override suspend fun isAdmin(actor: Actor, file: UCloudFile): Boolean {
        if (actor == Actor.System) return true

        val normalizedFile = pathConverter.ucloudToRelative(file).normalize()
        val username = actor.safeUsername()

        if (isPersonalWorkspace(normalizedFile)) {
            val homeFolder = homeFolder(username).normalize()
            if (normalizedFile.path == homeFolder || normalizedFile.path.startsWith("$homeFolder/")) {
                return true
            }
        } else if (isProjectWorkspace(normalizedFile)) {
            val components = normalizedFile.components()
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

    override suspend fun updateAcl(actor: Actor, request: FilesUpdateAclRequest) {
        log.debug("Executing ACL update request: $request")

        for (reqItem in request.items) {
            val ucloudFile = UCloudFile.create(reqItem.path)
            val relativeFile = pathConverter.ucloudToRelative(ucloudFile)

            if (!isAdmin(actor, ucloudFile)) {
                throw RPCException("Only the owner can update the ACL", HttpStatusCode.Forbidden)
            }

            when {
                isPersonalWorkspace(relativeFile) -> {
                    db.withSession { session ->
                        metadataDao.removeEntry(session, relativeFile, null, ACL_USER_METADATA_TYPE)
                        reqItem.newAcl.forEach { aclEntry ->
                            val entity = aclEntry.entity
                            if (entity !is AclEntity.User) {
                                throw RPCException("Unsupported entity type", HttpStatusCode.BadRequest)
                            }

                            metadataDao.createMetadata(session, Metadata(
                                relativeFile,
                                ACL_USER_METADATA_TYPE,
                                entity.username,
                                defaultMapper.encodeToJsonElement(
                                    UserAclMetadata(aclEntry.permissions.toSet())
                                ) as JsonObject
                            ))
                        }
                    }
                }
                isProjectWorkspace(relativeFile) -> {
                    val pathComponents = relativeFile.components()
                    val projectId = pathComponents[1]
                    if (pathComponents.size == 3 && pathComponents[2] == PERSONAL_REPOSITORY) {
                        throw RPCException(
                            "Cannot update permissions of '${PERSONAL_REPOSITORY}' repository",
                            HttpStatusCode.Forbidden
                        )
                    }

                    db.withSession { session ->
                        metadataDao.updateMetadata(
                            session,
                            Metadata(
                                relativeFile,
                                ACL_PROJECT_METADATA_TYPE,
                                projectId,
                                defaultMapper.encodeToJsonElement(
                                    ProjectAclMetadata(
                                        reqItem.newAcl.map { entry ->
                                            val entity = entry.entity
                                            if (entity !is AclEntity.ProjectGroup) {
                                                throw RPCException("Unsupported entity type", HttpStatusCode.BadRequest)
                                            }

                                            if (entity.projectId != projectId) {
                                                throw RPCException("Invalid project supplied", HttpStatusCode.BadRequest)
                                            }

                                            ProjectAclEntity(entity.group, entry.permissions.toSet())
                                        }
                                    )
                                ) as JsonObject
                            )
                        )
                    }
                }
                else -> {
                    throw FSException.NotFound()
                }
            }
        }
    }

    override suspend fun fetchMyPermissions(actor: Actor, file: UCloudFile): Set<FilePermission> {
        if (actor == Actor.System || isAdmin(actor, file)) {
            return setOf(FilePermission.READ, FilePermission.WRITE, FilePermission.ADMINISTRATOR)
        }

        val relativeFile = pathConverter.ucloudToRelative(file)
        val username = actor.safeUsername()

        val relevantPaths = relativeFile.parents().map { it.normalize() } + listOf(relativeFile)

        return db.withSession { session ->
            when {
                isPersonalWorkspace(relativeFile) -> {
                    metadataDao
                        .listMetadata(session, relevantPaths, actor.username, ACL_USER_METADATA_TYPE)
                        .flatMap { entry ->
                            entry.value.flatMap {
                                defaultMapper.decodeFromJsonElement<UserAclMetadata>(it.payload).permissions
                            }
                        }
                        .normalize()
                }
                isProjectWorkspace(relativeFile) -> {
                    val components = relativeFile.components()
                    val projectId = components[1]

                    val relevantEntries = metadataDao
                        .listMetadata(session, relevantPaths, projectId, ACL_PROJECT_METADATA_TYPE)
                        .flatMap { it.value }
                        .flatMap { data ->
                            defaultMapper.decodeFromJsonElement<ProjectAclMetadata>(data.payload).entries
                        }

                    val memberStatus = projectCache.memberStatus.get(username)
                    val relevantGroups = (memberStatus?.groups?.filter { it.project == projectId }?.map { it.group }
                        ?: emptySet()).toSet()

                    val permissions = HashSet<FilePermission>()
                    relevantEntries.forEach { entry ->
                        if (entry.group in relevantGroups) {
                            permissions.addAll(entry.permissions)
                        }
                    }

                    permissions.normalize()
                }
                else -> {
                    throw FSException.NotFound()
                }
            }
        }
    }

    override suspend fun fetchOtherPermissions(file: UCloudFile): List<ResourceAclEntry<FilePermission>> {
        val relativeFile = pathConverter.ucloudToRelative(file)

        return db.withSession { session ->
            when {
                isPersonalWorkspace(relativeFile) -> {
                    val relevantPaths = relativeFile.parents() + relativeFile
                    metadataDao
                        .listMetadata(session, relevantPaths, null, ACL_USER_METADATA_TYPE)
                        .flatMap { entry ->
                            entry.value.mapNotNull {
                                ResourceAclEntry(
                                    AclEntity.User(it.username ?: return@mapNotNull null),
                                    defaultMapper
                                        .decodeFromJsonElement<UserAclMetadata>(it.payload)
                                        .permissions
                                        .normalize()
                                        .toList()
                                )
                            }
                        }
                }

                isProjectWorkspace(relativeFile) -> {
                    val components = relativeFile.components()
                    val projectId = components[1]

                    val relevantEntries = metadataDao
                        .listMetadata(session, listOf(relativeFile), projectId, ACL_PROJECT_METADATA_TYPE)
                        .flatMap { it.value }
                        .flatMap { data ->
                            defaultMapper.decodeFromJsonElement<ProjectAclMetadata>(data.payload).entries
                        }

                    relevantEntries.map {
                        ResourceAclEntry(
                            AclEntity.ProjectGroup(projectId, it.group),
                            it.permissions.normalize().toList()
                        )
                    }
                }

                else -> {
                    throw FSException.NotFound()
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        const val ACL_USER_METADATA_TYPE = "acl"
        const val ACL_PROJECT_METADATA_TYPE = "project-acl"
    }
}
