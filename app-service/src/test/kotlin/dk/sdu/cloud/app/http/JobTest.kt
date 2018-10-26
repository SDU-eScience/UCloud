package dk.sdu.cloud.app.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.Tool
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.app.api.WordInvocationParameter
import dk.sdu.cloud.app.services.JobDao
import dk.sdu.cloud.app.services.VerifiedJobWithAccessToken
import dk.sdu.cloud.app.utils.withAuthMock
import dk.sdu.cloud.app.utils.withDatabase
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dk.sdu.cloud.app.api.Application as CloudApp

private fun Application.configureJobServer(
    db: DBSessionFactory<HibernateSession>,
    jobService: JobDao<HibernateSession>
) {
    configureBaseServer(JobController(db, mockk(relaxed = true), jobService))
}

class JobTest {
    private val mapper = jacksonObjectMapper()
    private val tool = Tool(
        "",
        System.currentTimeMillis(),
        System.currentTimeMillis(),
        NormalizedToolDescription(
            NameAndVersion("tool", "1.0.0"),
            "container",
            1,
            1,
            SimpleDuration(1, 0, 0),
            emptyList(),
            listOf("asd"),
            "title",
            "description",
            ToolBackend.SINGULARITY
        )
    )
    private val app = CloudApp(
        "appOwner",
        System.currentTimeMillis(),
        System.currentTimeMillis(),
        NormalizedApplicationDescription(
            NameAndVersion("app", "1.0.0"),
            tool.description.info,
            listOf("asd"),
            "app",
            "description",
            listOf(WordInvocationParameter("foo")),
            emptyList(),
            emptyList()
        ),
        tool
    )

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
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val jobService = mockk<JobDao<HibernateSession>>()
                        configureJobServer(db, jobService)

                        every { jobService.findOrNull(any(), any(), any()) } returns job
                    },

                    test = {
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
                )
            }
        }
    }

    @Test
    fun `find By ID test - not found`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val jobService = mockk<JobDao<HibernateSession>>()
                        configureJobServer(db, jobService)

                        every { jobService.findOrNull(any(), any(), any()) } returns null

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/hpc/jobs/2") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.NotFound, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun `list recent test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val jobService = mockk<JobDao<HibernateSession>>()
                        configureJobServer(db, jobService)
                        every { jobService.list(any(), any(), any()) } answers {
                            Page(1, 10, 0, listOf(job))
                        }
                    },

                    test = {
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
                )
            }
        }
    }
}
