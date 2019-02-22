package dk.sdu.cloud.integration

/*
import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.UsageRequest
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingTimeDescriptions
import dk.sdu.cloud.app.api.AccountingEvents
import dk.sdu.cloud.app.api.JobCompletedEvent
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.auth.api.CreateSingleUserRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.project.api.AddMemberRequest
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.auth.api.FetchTokenRequest
import dk.sdu.cloud.project.auth.api.ProjectAuthDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.forStream
import dk.sdu.cloud.service.kafka
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.service.test.retrySection
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class AppAccountingForProjects {
    @Test
    fun runScenario() = runBlocking {
        log.info("Running scenario...")
        val names = (1..3).map { "test-${UUID.randomUUID()}_${it}_" }
        val password = UUID.randomUUID().toString()

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

        ProjectDescriptions.addMember.call(
            AddMemberRequest(projectId, ProjectMember(names[1], ProjectRole.USER)),
            piCloud
        ).orThrow()

        ProjectDescriptions.addMember.call(
            AddMemberRequest(
                projectId,
                ProjectMember(names[2], ProjectRole.DATA_STEWARD)
            ), piCloud
        ).orThrow()

        val jobAccountingProducer = micro.kafka.producer.forStream(AccountingEvents.jobCompleted)

        ProjectRole.values().forEach { role ->
            jobAccountingProducer.emit(
                JobCompletedEvent(
                    UUID.randomUUID().toString(),
                    "$projectId#$role",
                    SimpleDuration(1, 0, 0),
                    1,
                    System.currentTimeMillis(),
                    NameAndVersion("app", "1.0.0"),
                    true
                )
            )
        }

        retrySection(delay = 1000, attempts = 10) {
            userClouds.forEachIndexed { index, authenticatedCloud ->
                val projectCloud = createProjectCloud(projectId, authenticatedCloud)

                val result = ComputeAccountingTimeDescriptions.usage.call(UsageRequest(), projectCloud).orThrow()
                assertEquals(ProjectRole.values().size * 3600 * 1000L, result.usage)
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
