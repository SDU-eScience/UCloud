package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.acl.AccessRights
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.t
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.project.repository.api.*
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.retrySection
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import org.junit.Ignore
import org.junit.Test
@Ignore
class ProjectRepositories : IntegrationTest() {
    @Test
    fun `test that personal repository is automatically created and accessible`() =
        t {
            val project = initializeNormalProject(initializeRootProject())
            // This sections need to retry because repository creation is asynchronous
            retrySection {
                FileDescriptions.listAtPath.call(
                    ListDirectoryRequest(
                        joinPath(projectHomeDirectory(project.projectId), PERSONAL_REPOSITORY, project.piUsername)
                    ),
                    project.piClient
                ).orThrow()
            }

            val (newUserClient, newUser) = createUser()
            addMemberToProject(project.projectId, project.piClient, newUserClient, newUser)
            retrySection {
                // Files are accessible by both admins and the user

                // Test the PI
                FileDescriptions.listAtPath.call(
                    ListDirectoryRequest(
                        joinPath(projectHomeDirectory(project.projectId), PERSONAL_REPOSITORY, newUser)
                    ),
                    project.piClient
                ).orThrow()

                // Test the user
                FileDescriptions.listAtPath.call(
                    ListDirectoryRequest(
                        joinPath(projectHomeDirectory(project.projectId), PERSONAL_REPOSITORY, newUser)
                    ),
                    newUserClient
                ).orThrow()

                // Test that users cannot read other personal repos
                assertThatInstance(
                    FileDescriptions.listAtPath.call(
                        ListDirectoryRequest(
                            joinPath(projectHomeDirectory(project.projectId), PERSONAL_REPOSITORY, project.piUsername)
                        ),
                        newUserClient
                    ),
                    "was unsuccessful"
                ) { !it.statusCode.isSuccess() }
            }

            if (false) { // This part is really slow because of caching of project membership
                Projects.changeUserRole.call(
                    ChangeUserRoleRequest(project.projectId, newUser, ProjectRole.ADMIN),
                    project.piClient
                ).orThrow()

                retrySection(attempts = 90, delay = 1000) {
                    // When promoted we should be able to read the directory
                    FileDescriptions.listAtPath.call(
                        ListDirectoryRequest(
                            joinPath(projectHomeDirectory(project.projectId), PERSONAL_REPOSITORY, project.piUsername)
                        ),
                        newUserClient
                    ).orThrow()
                }
            }
        }

    @Test
    fun `test crud`() = t {
        val project = initializeNormalProject(initializeRootProject())

        val repoName = "Test"
        ProjectRepository.create.call(
            RepositoryCreateRequest(repoName),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        assertThatInstance(
            ProjectRepository.list.call(
                RepositoryListRequest(),
                project.piClient.withProject(project.projectId)
            ).orThrow(),
            "has our new repository"
        ) { it.items.any { it.name == repoName } }

        assertThatInstance(
            ProjectRepository.listFiles.call(
                RepositoryListRequest(),
                project.piClient.withProject(project.projectId)
            ).orThrow(),
            "has our new repository"
        ) { it.items.any { it.path.fileName() == repoName } }

        retrySection {
            FileDescriptions.listAtPath.call(
                ListDirectoryRequest(joinPath(projectHomeDirectory(project.projectId), repoName)),
                project.piClient.withProject(project.projectId)
            ).orThrow()
        }

        val newName = "TestNew"
        ProjectRepository.update.call(
            RepositoryUpdateRequest(repoName, newName),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        FileDescriptions.listAtPath.call(
            ListDirectoryRequest(joinPath(projectHomeDirectory(project.projectId), newName)),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        ProjectRepository.delete.call(
            RepositoryDeleteRequest(newName),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        assertThatInstance(
            FileDescriptions.listAtPath.call(
                ListDirectoryRequest(joinPath(projectHomeDirectory(project.projectId), newName)),
                project.piClient.withProject(project.projectId)
            ),
            "was unsuccessful"
        ) { !it.statusCode.isSuccess() }

        assertThatInstance(
            ProjectRepository.list.call(
                RepositoryListRequest(),
                project.piClient.withProject(project.projectId)
            ).orThrow(),
            "has no repositories"
        ) { it.items.none { it.name == repoName } }

        assertThatInstance(
            ProjectRepository.listFiles.call(
                RepositoryListRequest(),
                project.piClient.withProject(project.projectId)
            ).orThrow(),
            "has no repositories"
        ) { it.items.none { it.path.fileName() == repoName } }
    }

    @Test
    fun `test permissions`() = t {
        val project = initializeNormalProject(initializeRootProject())
        val (newUserClient, newUser) = createUser()
        addMemberToProject(project.projectId, project.piClient, newUserClient, newUser)
        val groupId: String

        run {
            groupId = ProjectGroups.create.call(
                CreateGroupRequest("group"),
                project.piClient.withProject(project.projectId)
            ).orThrow().id

            ProjectGroups.addGroupMember.call(
                AddGroupMemberRequest(groupId, newUser),
                project.piClient.withProject(project.projectId)
            ).orThrow()
        }

        run {
            val repoName = "repo"
            val newRepoName = "reponew"
            ProjectRepository.create.call(
                RepositoryCreateRequest(repoName),
                project.piClient.withProject(project.projectId)
            ).orThrow()

            ProjectRepository.updatePermissions.call(
                UpdatePermissionsRequest(
                    repoName,
                    listOf(ProjectAclEntry(groupId, AccessRights.READ_WRITE))
                ),
                project.piClient.withProject(project.projectId)
            ).orThrow()

            assertThatInstance(
                ProjectRepository.list.call(
                    RepositoryListRequest(),
                    newUserClient.withProject(project.projectId)
                ).orThrow(),
                "contains the repository"
            ) { it.items.any { it.name == repoName } }

            ProjectRepository.update.call(
                RepositoryUpdateRequest(repoName, newRepoName),
                project.piClient.withProject(project.projectId)
            ).orThrow()

            assertThatInstance(
                ProjectRepository.list.call(
                    RepositoryListRequest(),
                    newUserClient.withProject(project.projectId)
                ).orThrow(),
                "contains the repository"
            ) { it.items.any { it.name == newRepoName } }

            assertThatInstance(
                ProjectRepository.list.call(
                    RepositoryListRequest(),
                    newUserClient.withProject(project.projectId)
                ).orThrow(),
                "does not contain the old repository"
            ) { it.items.none { it.name == repoName } }
        }

        run {
            val secondaryRepo = "repo2"
            ProjectRepository.create.call(
                RepositoryCreateRequest(secondaryRepo),
                project.piClient.withProject(project.projectId)
            ).orThrow()

            assertThatInstance(
                ProjectRepository.list.call(
                    RepositoryListRequest(),
                    newUserClient.withProject(project.projectId)
                ).orThrow(),
                "does not contain the new repository"
            ) { it.items.any { it.name == secondaryRepo } }
        }
    }

    @Test
    fun `test permission with group deletion`() = t {
        val project = initializeNormalProject(initializeRootProject())
        val users = (0..5).map { createUser() }
        for (user in users) {
            addMemberToProject(project.projectId, project.piClient, user.client, user.username)
        }

        val group = createGroup(project, users.map { it.username }.toSet())

        val repo = "repo"
        ProjectRepository.create.call(
            RepositoryCreateRequest(repo),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        ProjectRepository.updatePermissions.call(
            UpdatePermissionsRequest(repo, listOf(ProjectAclEntry(group.groupId, AccessRights.READ_WRITE))),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        // Check that the first user can read/write the repo
        val folderName = "foobar"
        FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(joinPath(projectHomeDirectory(project.projectId), repo, folderName)),
            users[0].client
        ).orThrow()

        // Delete the repo and check again (different user to avoid caching issues)
        ProjectGroups.delete.call(
            DeleteGroupsRequest(setOf(group.groupId)),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        assertThatInstance(
            FileDescriptions.listAtPath.call(
                ListDirectoryRequest(joinPath(projectHomeDirectory(project.projectId), repo, folderName)),
                users[1].client
            ),
            "fail because the group no longer exists"
        ) { !it.statusCode.isSuccess() }
    }

    @Test
    fun `test repository creation by normal user fails`() = t {
        val project = initializeNormalProject(initializeRootProject())
        val user = createUser()
        addMemberToProject(project.projectId, project.piClient, user.client, user.username)
        assertThatInstance(
            ProjectRepository.create.call(
                RepositoryCreateRequest("whatever"),
                user.client.withProject(project.projectId)
            ),
            "should fail because we are a user"
        ) { it.statusCode == HttpStatusCode.Forbidden }
    }

    @Test
    fun `test repository creation fails for personal repos`() = t {
        val project = initializeNormalProject(initializeRootProject())
        assertThatInstance(
            ProjectRepository.create.call(
                RepositoryCreateRequest(PERSONAL_REPOSITORY),
                project.piClient.withProject(project.projectId)
            ),
            "should fail because"
        ) { it.statusCode == HttpStatusCode.BadRequest }
    }

    @Test
    fun `list repositories in unknown project`() = t {
        val user = createUser()
        assertThatInstance(
            ProjectRepository.list.call(RepositoryListRequest(), user.client.withProject("notarealproject")),
            "fails because it does not exist"
        ) { it.statusCode == HttpStatusCode.NotFound }
    }

    @Test
    fun `list repositories in unknown project that exists`() = t {
        val project = initializeNormalProject(initializeRootProject())
        val user = createUser()
        assertThatInstance(
            ProjectRepository.list.call(RepositoryListRequest(), user.client.withProject(project.projectId)),
            "fails because it does not exist"
        ) { it.statusCode == HttpStatusCode.NotFound }
    }

    @Test
    fun `list repo files as a normal user`() = t {
        val project = initializeNormalProject(initializeRootProject())
        val user = createUser()
        addMemberToProject(project.projectId, project.piClient, user.client, user.username)
        val repo = "repo"
        ProjectRepository.create.call(
            RepositoryCreateRequest(repo),
            project.piClient.withProject(project.projectId)
        ).orThrow()
        assertThatInstance(
            ProjectRepository.listFiles.call(
                RepositoryListRequest(),
                user.client.withProject(project.projectId)
            ).orThrow(),
            "should contain a file"
        ) { it.items.any { it.path.fileName() == repo && it.fileType == FileType.DIRECTORY } }
    }

    @Test
    fun `test updating permissions with bad group`() = t {
        val project = initializeNormalProject(initializeRootProject())
        val repo = "repo"
        ProjectRepository.create.call(
            RepositoryCreateRequest(repo),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        assertThatInstance(
            ProjectRepository.updatePermissions.call(
                UpdatePermissionsRequest(repo, listOf(ProjectAclEntry("badgroup", setOf(FileRights.READ)))),
                project.piClient.withProject(project.projectId)
            ),
            "fails because the group doesn't exist"
        ) { it.statusCode == HttpStatusCode.BadRequest }
    }
}
