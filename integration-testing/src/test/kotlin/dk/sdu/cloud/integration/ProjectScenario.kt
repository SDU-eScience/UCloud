package dk.sdu.cloud.integration

/*
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.CreateSingleUserRequest
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.client.MultipartRequest
import dk.sdu.cloud.client.StreamingFile
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.file.api.DownloadByURI
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.ListDirectoryRequest
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.file.api.UploadRequest
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.project.api.AddMemberRequest
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.ViewProjectRequest
import dk.sdu.cloud.project.auth.api.FetchTokenRequest
import dk.sdu.cloud.project.auth.api.ProjectAuthDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.cloudContext
import dk.sdu.cloud.service.configuration
import dk.sdu.cloud.service.initWithDefaultFeatures
import dk.sdu.cloud.service.okChannel
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.service.test.assertThatProperty
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.retrySection
import dk.sdu.cloud.service.tokenValidation
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectScenario {
    @Test
    fun runScenario() = runBlocking {
        log.info("Running project scenario...")
        val prefix = "test-${UUID.randomUUID()}"
        val password = UUID.randomUUID().toString()
        val userCount = 5

        val names = (1..userCount).map { "$prefix$it" }

        log.info("Creating the following users: $names")
        val users = UserDescriptions.createNewUser.call(
            names.map { CreateSingleUserRequest(it, password, Role.USER) },
            adminClient
        ).orThrow()

        val userClouds = users.map { it.cloud() }

        val piUsername = names[0]
        val piCloud = userClouds[0]
        log.info("Creating project with $piUsername as the PI")
        val projectId = ProjectDescriptions.create.call(
            CreateProjectRequest("Integration Test", piUsername),
            adminClient
        ).orThrow().id

        log.info("Project ID: $projectId")

        run {
            log.info("Looking up project as PI")
            val projectView = ProjectDescriptions.view.call(ViewProjectRequest(projectId), piCloud).orThrow()
            assertEquals(projectId, projectView.id)
            assertThatPropertyEquals(projectView.members, { it.size }, 1)
            assertThatProperty(
                projectView,
                { it.members },
                matcher = { members ->
                    members.any { it.username == piUsername && it.role == ProjectRole.PI }
                }
            )
        }

        val piProjectCloud = createProjectCloud(projectId, piCloud)
        val testFilePi = "/home/$projectId/test"
        val testFileUser = "/home/$projectId/test-user"
        val fileContents = "Hello!"

        run {
            log.info("Uploading basic file to project")
            val file = Files.createTempFile("", "").toFile().also { it.writeText(fileContents) }

            retrySection(attempts = 5, delay = 5000) {
                MultiPartUploadDescriptions.upload.call(
                    MultipartRequest.create(
                        UploadRequest(
                            testFilePi,
                            upload = StreamingFile.fromFile(file)
                        )
                    ),
                    piProjectCloud
                ).orThrow()
            }
        }

        val userCloud1 = run {
            log.info("Adding user to project...")
            ProjectDescriptions.addMember.call(
                AddMemberRequest(projectId, ProjectMember(names[1], ProjectRole.USER)),
                piCloud
            ).orThrow()

            val userCloud = createProjectCloud(projectId, userClouds[1])

            log.info("Listing directory")
            val page = FileDescriptions.listAtPath.call(
                ListDirectoryRequest(
                    path = testFilePi.parent(),
                    itemsPerPage = null,
                    page = null,
                    order = null,
                    sortBy = null
                ),
                userCloud
            ).orThrow()

            assertThatProperty(page, { it.items }, matcher = { items -> items.any { it.path == testFilePi } })

            log.info("Downloading file")
            val downloadedFile = FileDescriptions.download
                .call(
                    DownloadByURI(testFilePi, token = null),
                    userCloud
                )
                .okChannel
                .stream
                .toInputStream()
                .bufferedReader()
                .readText()

            assertEquals(fileContents, downloadedFile)

            userCloud
        }

        run {
            log.info("Uploading basic file to project")
            val file = Files.createTempFile("", "").toFile().also { it.writeText(fileContents) }

            retrySection(attempts = 5, delay = 5000) {
                MultiPartUploadDescriptions.upload.call(
                    MultipartRequest.create(
                        UploadRequest(
                            testFileUser,
                            upload = StreamingFile.fromFile(file)
                        )
                    ),
                    userCloud1
                ).orThrow()
            }
        }

        run {
            log.info("Check that user file has PI listed as owner")
            retrySection {
                val fileStat = FileDescriptions.stat.call(FindByPath(testFileUser), userCloud1).orThrow()
                assertEquals("$projectId#PI", fileStat.ownerName)
                assertEquals("$projectId#USER", fileStat.creator)
            }
        }

        return@runBlocking
    }

    private suspend fun createProjectCloud(
        projectId: String,
        userCloud: AuthenticatedCloud
    ): JWTAuthenticatedCloud {
        log.info("Creating accessToken for project")

        val token = retrySection(attempts = 30, delay = 500) {
            ProjectAuthDescriptions.fetchToken.call(FetchTokenRequest(projectId), userCloud).orThrow().accessToken
        }

        return cloudContext.jwtAuth(token)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
*/
