package dk.sdu.cloud.app.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.app.api.WordInvocationParameter
import dk.sdu.cloud.app.services.JobDao
import dk.sdu.cloud.app.services.VerifiedJobWithAccessToken
import dk.sdu.cloud.app.services.normAppDesc
import dk.sdu.cloud.app.services.withInvocation
import dk.sdu.cloud.app.services.withNameAndVersion
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.withKtorTest
import dk.sdu.cloud.service.tokenValidation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dk.sdu.cloud.app.api.Application as CloudApp

private fun KtorApplicationTestSetupContext.configureJobServer(
    jobService: JobDao<HibernateSession>
): List<Controller> {
    micro.install(HibernateFeature)
    val tokenValidation = micro.tokenValidation as TokenValidationJWT
    return listOf(JobController(micro.hibernateDatabase, mockk(relaxed = true), jobService, tokenValidation))
}

class JobTest {
    private val mapper = jacksonObjectMapper()

    private val app = normAppDesc
        .withNameAndVersion("app", "1.0.0")
        .withInvocation(listOf(WordInvocationParameter("foo")))

    private val job = VerifiedJobWithAccessToken(
        VerifiedJob(
            app,
            emptyList(),
            "2",
            "someOwner",
            1,
            1,
            SimpleDuration(1, 0, 0),
            VerifiedJobInput(emptyMap()),
            "abacus",
            JobState.SUCCESS,
            "Prepared"
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
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/hpc/jobs/2") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val obj = mapper.readTree(response.content)
                    assertEquals("\"${job.job.id}\"", obj["jobId"].toString())
                    assertEquals("\"${job.job.owner}\"", obj["owner"].toString())
                }
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
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/hpc/jobs/2") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
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
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/hpc/jobs?itemsPerPage=10&page=0") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val obj = mapper.readTree(response.content)

                    assertEquals(1, obj["itemsInTotal"].asInt())
                    assertTrue(obj["items"].toString().contains("\"state\":\"SUCCESS\""))
                }
            }
        )
    }
}
