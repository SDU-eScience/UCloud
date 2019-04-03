package dk.sdu.cloud.app.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.api.FollowStdStreamsResponse
import dk.sdu.cloud.app.api.JobStartedResponse
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.JobWithStatus
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.StartJobRequest
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.app.api.WordInvocationParameter
import dk.sdu.cloud.app.services.JobDao
import dk.sdu.cloud.app.services.JobOrchestrator
import dk.sdu.cloud.app.services.VerifiedJobWithAccessToken
import dk.sdu.cloud.app.services.normAppDesc
import dk.sdu.cloud.app.services.withInvocation
import dk.sdu.cloud.app.services.withNameAndVersion
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionResponse
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import dk.sdu.cloud.app.api.Application as CloudApp

private fun KtorApplicationTestSetupContext.configureJobServer(
    jobService: JobDao<HibernateSession>,
    tokenValidation: TokenValidationJWT = micro.tokenValidation as TokenValidationJWT,
    orchestrator: JobOrchestrator<HibernateSession> = mockk(relaxed = true)
): List<Controller> {
    micro.install(HibernateFeature)
    return listOf(
        JobController(
            micro.hibernateDatabase,
            orchestrator,
            jobService,
            tokenValidation,
            ClientMock.authenticatedClient
        )
    )
}

class JobTest {
    private val app = normAppDesc
        .withNameAndVersion("app", "1.0.0")
        .withInvocation(listOf(WordInvocationParameter("foo")))

    private val job = VerifiedJobWithAccessToken(
        VerifiedJob(
            application = app,
            files = emptyList(),
            id = "2",
            owner = "someOwner",
            nodes = 1,
            tasksPerNode = 1,
            maxTime = SimpleDuration(1, 0, 0),
            jobInput = VerifiedJobInput(emptyMap()),
            backend = "abacus",
            currentState = JobState.SUCCESS,
            status = "Prepared",
            archiveInCollection = app.metadata.title
        ),
        "accessToken"
    )

    @Test
    fun `find By ID test`() {
        withKtorTest(
            setup = {
                val jobService = mockk<JobDao<HibernateSession>>()
                every { jobService.findOrNull(any(), any(), any()) } returns job
                configureJobServer(jobService)
            },

            test = {
                val response = sendRequest(
                        method = HttpMethod.Get,
                        path = "/api/hpc/jobs/2",
                        user = TestUsers.user
                    )
                response.assertSuccess()

                val results = defaultMapper.readValue<JobWithStatus>(response.response.content!!)
                assertEquals(job.job.id, results.jobId)
                assertEquals(job.job.owner, results.owner)

            }
        )
    }

    @Test
    fun `find By ID test - not found`() {
        withKtorTest(
            setup = {
                val jobService = mockk<JobDao<HibernateSession>>()

                every { jobService.findOrNull(any(), any(), any()) } returns null

                configureJobServer(jobService)
            },

            test = {
                sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/hpc/jobs/2",
                    user = TestUsers.user
                ).assertStatus(HttpStatusCode.NotFound)
            }
        )
    }

    @Test
    fun `list recent test`() {
        withKtorTest(
            setup = {
                val jobService = mockk<JobDao<HibernateSession>>()

                every { jobService.list(any(), any(), any()) } answers {
                    Page(1, 10, 0, listOf(job))
                }

                configureJobServer(jobService)
            },

            test = {
                val response = sendRequest(
                        method = HttpMethod.Get,
                        path = "/api/hpc/jobs?itemsPerPage=10&page=0",
                        user = TestUsers.user
                    )
                response.assertSuccess()

                val results = defaultMapper.readValue<Page<JobWithStatus>>(response.response.content!!)
                assertEquals(1, results.itemsInTotal)
                assertEquals(JobState.SUCCESS, results.items.first().state)
            }
        )
    }

    @Test
    fun `start test`() {
        withKtorTest(
            setup = {
                val jobService = mockk<JobDao<HibernateSession>>()
                val tokenValidation = mockk<TokenValidationJWT>(relaxed = true)
                val orchestrator = mockk<JobOrchestrator<HibernateSession>>()

                ClientMock.mockCallSuccess(
                    AuthDescriptions.tokenExtension,
                    TokenExtensionResponse("token", null, null)
                )

                coEvery { orchestrator.startJob(any(), any(), any()) } answers {
                    "jobId"
                }

                configureJobServer(jobService, tokenValidation, orchestrator)
            },
            test = {
                val response = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/hpc/jobs",
                    user = TestUsers.user,
                    request = StartJobRequest(
                        NameAndVersion("name", "2.2"),
                        emptyMap()
                    )
                )
                response.assertSuccess()

                val results = defaultMapper.readValue<JobStartedResponse>(response.response.content!!)
                assertEquals("jobId", results.jobId)
            }
        )
    }

    @Test
    fun `start test - extension extent failed`() {
        withKtorTest(
            setup = {
                val jobService = mockk<JobDao<HibernateSession>>()
                ClientMock.mockCallError(
                    AuthDescriptions.tokenExtension,
                    null,
                    HttpStatusCode.Unauthorized
                )
                configureJobServer(jobService)
            },
            test = {
                sendJson(
                    method = HttpMethod.Post,
                    path = "/api/hpc/jobs",
                    user = TestUsers.user,
                    request = StartJobRequest(
                        NameAndVersion("name", "2.2"),
                        emptyMap()
                    )
                ).assertStatus(HttpStatusCode.Unauthorized)
            }
        )
    }

    @Test
    fun `follow test`() {
        withKtorTest(
            setup = {
                val jobService = mockk<JobDao<HibernateSession>>()
                val orchestrator = mockk<JobOrchestrator<HibernateSession>>()

                coEvery { orchestrator.followStreams(any()) } answers {
                    FollowStdStreamsResponse(
                        "stdout",
                        10,
                        "stderr",
                        10,
                        NameAndVersion("name", "2.2"),
                        JobState.RUNNING,
                        "running",
                        false,
                        "jobId",
                        null,
                        app.metadata)
                }

                configureJobServer(jobService, orchestrator = orchestrator)
            },
            test = {
                sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/hpc/jobs/follow/jobId",
                    user = TestUsers.user,
                    params = mapOf(
                        "stderrLineStart" to 0,
                        "stderrMaxLines" to 10,
                        "stdoutLineStart" to 0,
                        "stdoutMaxLines" to 10
                    )
                ).assertSuccess()
            }
        )
    }
}
