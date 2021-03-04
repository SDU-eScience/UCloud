package dk.sdu.cloud.project.repository.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.project.repository.api.ProjectAclEntry
import dk.sdu.cloud.project.repository.api.Repository
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class RepositoryService(private val serviceClient: AuthenticatedClient) {
    suspend fun create(principal: SecurityPrincipal, project: String, repository: String) {
        if (repository == PERSONAL_REPOSITORY) throw RPCException("Invalid repository name", HttpStatusCode.BadRequest)

        val status = Projects.viewMemberInProject.call(
            ViewMemberInProjectRequest(project, principal.username),
            serviceClient
        ).orRethrowAs { throw RPCException("Unknown project", HttpStatusCode.NotFound) }

        if (!status.member.role.isAdmin()) {
            throw RPCException("Only admins can create new repositories", HttpStatusCode.Forbidden)
        }

        val path = joinPath(
            PROJECT_DIR_PREFIX,
            project,
            repository
        )

        val createDirectoryStatus = FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(
                path,
                principal.username
            ),
            serviceClient
        )

        if (createDirectoryStatus.statusCode != HttpStatusCode.Conflict &&
            !createDirectoryStatus.statusCode.isSuccess()
        ) {
            throw RPCException("Failed to create new repository", HttpStatusCode.InternalServerError)
        }

        MetadataDescriptions.createMetadata.call(
            CreateMetadataRequest(
                listOf(
                    MetadataUpdate(
                        path,
                        METADATA_NAME,
                        null,
                        defaultMapper.encodeToString(RepositoryMetadata())
                    )
                )
            ),
            serviceClient
        ).orThrow()
    }

    suspend fun update(
        userClient: AuthenticatedClient,
        project: String,
        oldRepository: String,
        newRepository: String
    ) {
        if (newRepository == PERSONAL_REPOSITORY) {
            throw RPCException("Invalid repository name", HttpStatusCode.BadRequest)
        }

        FileDescriptions.move.call(
            MoveRequest(
                joinPath(PROJECT_DIR_PREFIX, project, oldRepository),
                joinPath(PROJECT_DIR_PREFIX, project, newRepository),
                WriteConflictPolicy.REJECT
            ),
            userClient
        ).orThrow()
    }

    suspend fun delete(
        userClient: AuthenticatedClient,
        project: String,
        repository: String
    ) {
        if (repository == PERSONAL_REPOSITORY) throw RPCException("Invalid repository name", HttpStatusCode.BadRequest)

        FileDescriptions.deleteFile.call(
            DeleteFileRequest(joinPath(PROJECT_DIR_PREFIX, project, repository)),
            userClient
        ).orThrow()
    }

    suspend fun listRepositories(
        username: String,
        project: String
    ): List<Repository> {
        Projects.viewMemberInProject.call(
            ViewMemberInProjectRequest(project, username),
            serviceClient
        ).orRethrowAs { throw RPCException("Unknown project", HttpStatusCode.NotFound) }

        // All members can view all repositories
        return MetadataDescriptions.findByPrefix
            .call(
                FindByPrefixRequest(
                    joinPath(PROJECT_DIR_PREFIX, project),
                    username = null,
                    type = METADATA_NAME
                ),
                serviceClient
            )
            .orRethrowAs { throw RPCException("Unable to list repositories", HttpStatusCode.InternalServerError) }
            .metadata
            .mapNotNull {
                val name = it.path.normalize().removePrefix(joinPath(PROJECT_DIR_PREFIX, project).normalize() + "/")
                val metadata = runCatching { defaultMapper.decodeFromString<RepositoryMetadata>(it.jsonPayload) }
                    .getOrNull()
                if (metadata != null) {
                    name to metadata
                } else {
                    null
                }
            }
            .map { Repository(it.first) }
    }

    suspend fun listFiles(
        username: String,
        project: String,
        userClient: AuthenticatedClient,
        userClientForCleanUp: (suspend () -> AuthenticatedClient)? = null
    ): List<StorageFile> {
        val status = Projects.viewMemberInProject.call(
            ViewMemberInProjectRequest(project, username),
            serviceClient
        ).orRethrowAs { throw RPCException("Unknown project", HttpStatusCode.NotFound) }

        val filesFromRepo = if (!status.member.role.isAdmin()) {
            val allRepos = listRepositories(username, project)
            allRepos.map { StorageFile(FileType.DIRECTORY, "/projects/$project/${it.name}") }
        } else {
            FileDescriptions.listAtPath.call(
                ListDirectoryRequest("/projects/$project", -1, -1, null, null),
                userClient
            ).orThrow().items.also {
                verifyReturnedAcl(it, project, userClientForCleanUp)
            }
        }

        val myPersonalFiles = FileDescriptions.stat.call(
            StatRequest(joinPath(projectHomeDirectory(project), PERSONAL_REPOSITORY, username)),
            userClient
        ).orNull()

        return listOfNotNull(
            myPersonalFiles,
            filesFromRepo.find { it.path.fileName() == PERSONAL_REPOSITORY }
        ) + filesFromRepo.filter { it.path.fileName() != PERSONAL_REPOSITORY }
    }

    private suspend fun verifyReturnedAcl(
        files: List<StorageFile>,
        project: String,
        userClientForCleanUp: (suspend () -> AuthenticatedClient)?
    ) {
        if (userClientForCleanUp == null) return

        val groups = files
            .flatMap { file ->
                (file.acl ?: emptyList())
                    .map { it.entity }
                    .filterIsInstance<ACLEntity.ProjectAndGroup>()
                    .filter { it.projectId == project }
                    .map { it.group }
            }
            .toSet()
            .toList()

        val exists = ProjectGroups.groupExists.call(
            GroupExistsRequest(project, groups),
            serviceClient
        ).orThrow().exists

        val deadGroups = ArrayList<String>()
        for ((index, e) in exists.withIndex()) {
            if (!e) {
                deadGroups.add(groups[index])
            }
        }

        if (deadGroups.isNotEmpty()) {
            var userClient: AuthenticatedClient? = null
            for (file in files) {
                val acl = file.acl ?: emptyList()
                val newAcl = acl.filter { (entity, _) ->
                    if (entity is ACLEntity.ProjectAndGroup) {
                        entity.group !in deadGroups
                    } else {
                        true
                    }
                }

                if (newAcl.size != acl.size) {
                    FileDescriptions.updateProjectAcl.call(
                        UpdateProjectAclRequest(
                            file.path,
                            project,
                            newAcl.map {
                                ProjectAclEntryRequest(
                                    (it.entity as ACLEntity.ProjectAndGroup).group,
                                    it.rights
                                )
                            }
                        ),
                        if (userClient == null) {
                            userClient = userClientForCleanUp()
                            userClient
                        } else {
                            userClient
                        }
                    )
                }
            }
        }
    }

    suspend fun updatePermissions(
        userClient: AuthenticatedClient,
        project: String,
        repository: String,
        newAcl: List<ProjectAclEntry>
    ) {
        newAcl.map { it.group }.toSet().forEach { group ->
            val groupExists = ProjectGroups.groupExists.call(
                GroupExistsRequest(project, listOf(group)),
                serviceClient
            ).orThrow().exists.single()

            if (!groupExists) {
                throw RPCException("Group not found", HttpStatusCode.BadRequest)
            }
        }

        FileDescriptions.updateProjectAcl.call(
            UpdateProjectAclRequest(
                joinPath(PROJECT_DIR_PREFIX, project, repository),
                project,
                newAcl.map { ProjectAclEntryRequest(it.group, it.rights) }
            ),
            userClient
        ).orThrow()
    }

    companion object : Loggable {
        override val log = logger()
        const val PROJECT_DIR_PREFIX = "/projects/"
        const val METADATA_NAME = "project_repository"

        @Serializable
        private class RepositoryMetadata
    }
}
