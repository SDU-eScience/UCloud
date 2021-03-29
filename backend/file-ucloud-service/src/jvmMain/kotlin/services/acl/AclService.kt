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
val SERVICE_USER = "_storage"

interface AclService {
    suspend fun isAdmin(actor: Actor, file: UCloudFile): Boolean
    suspend fun updateAcl(actor: Actor, request: FilesUpdateAclRequest)
    suspend fun fetchMyPermissions(actor: Actor, file: UCloudFile): Set<FilePermission>
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

    private fun isPersonalWorkspace(file: RelativeInternalFile): Boolean =
        file.path.startsWith("/${PathConverter.HOME_DIRECTORY}/")

    private fun isProjectWorkspace(file: RelativeInternalFile): Boolean =
        file.path.startsWith("/${PathConverter.PROJECT_DIRECTORY}/")

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

            if (isPersonalWorkspace(relativeFile)) {
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
            } else if (isProjectWorkspace(relativeFile)) {
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
            } else {
                throw FSException.NotFound()
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
                        .toSet()
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

                    permissions
                }
                else -> {
                    throw FSException.NotFound()
                }
            }
        }
    }

    /*



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
     */

    companion object : Loggable {
        override val log = logger()

        const val ACL_USER_METADATA_TYPE = "acl"
        const val ACL_PROJECT_METADATA_TYPE = "project-acl"
    }
}
