package dk.sdu.cloud.integration

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.acl.AccessRights
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.project.repository.api.*
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.retrySection
import io.ktor.http.isSuccess
import org.junit.Test

class ProjectRepositories : IntegrationTest() {
    @Test
    fun `test that personal repository is automatically created and accessible`() = t {
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
        val groupName = "group"

        run {
            ProjectGroups.create.call(
                CreateGroupRequest(groupName),
                project.piClient.withProject(project.projectId)
            ).orThrow()

            ProjectGroups.addGroupMember.call(
                AddGroupMemberRequest(groupName, newUser),
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
                    listOf(ProjectAclEntry(groupName, AccessRights.READ_WRITE))
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
}
