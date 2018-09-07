package dk.sdu.cloud.app.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.services.JobInformation
import dk.sdu.cloud.app.services.JobService
import dk.sdu.cloud.app.services.JobServiceException
import dk.sdu.cloud.app.utils.withAuthMock
import dk.sdu.cloud.app.utils.withDatabase
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun Application.configureJobServer(
    db: HibernateSessionFactory,
    jobService: JobService<HibernateSession>
) {
    configureBaseServer(JobController(db, jobService))
}

class JobTest {

    private val mapper = jacksonObjectMapper()

    private val job = JobWithStatus(
        "2",
        "owner",
        AppState.SUCCESS,
        "Status",
        "Name of App",
        "2.2",
        123456,
        12345678
    )

    private val jobInfo = JobInformation(
        UUID.randomUUID().toString(),
        "owner",
        "app Name",
        "2.2",
        123456,
        "status",
        "sshUser",
        "job dir",
        "work dir",
        12345678,
        AppState.RUNNING
    )

    private val followStdResponse = FollowStdStreamsResponse(
        "stdout",
        10,
        "error",
        20,
        NameAndVersion("name", "2.2"),
        AppState.RUNNING,
        "status",
        false,
        "22"
    )

    @Test
    fun `find By ID test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val jobService = mockk<JobService<HibernateSession>>()
                        configureJobServer(db, jobService)

                        every { jobService.findJobById(any(), any()) } returns job

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/hpc/jobs/2") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                        val obj = mapper.readTree(response.content)

                        assertEquals("\"2\"", obj["jobId"].toString())
                        assertEquals("\"owner\"", obj["owner"].toString())
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
                        val jobService = mockk<JobService<HibernateSession>>()
                        configureJobServer(db, jobService)

                        every { jobService.findJobById(any(), any()) } returns null

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
                        val jobService = mockk<JobService<HibernateSession>>()
                        configureJobServer(db, jobService)
                        every { jobService.recentJobs(any(), any()) } answers {
                            Page(1,10,0, listOf(job))
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

    @Test
    fun `start test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val jobService = mockk<JobService<HibernateSession>>()
                        configureJobServer(db, jobService)

                        coEvery { jobService.startJob(any(), any(), any()) } returns "Job started"

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/api/hpc/jobs") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                                setBody("""
                                    {
                                    "application" : {
                                        "name":"name Of App",
                                        "version":"2.2"
                                        },
                                    "parameters": {
                                        "param":"true"
                                        },
                                    "numberOfNodes": 2,
                                    "tasksPerNode": 3,
                                    "maxTime": {
                                        "hours": 0,
                                        "minutes": 2,
                                        "seconds": 30
                                        },
                                    "type": "start"
                                    }
                                """.trimIndent())
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun `start test - bad JSON`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val jobService = mockk<JobService<HibernateSession>>()
                        configureJobServer(db, jobService)
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/api/hpc/jobs") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                                setBody("""
                                    {
                                    "application" : {
                                     conds": 30
                                        },
                                    "type": "start"
                                    }
                                """.trimIndent())
                            }.response

                        assertEquals(HttpStatusCode.BadRequest, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun `Follow test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val jobService = mockk<JobService<HibernateSession>>()
                        configureJobServer(db, jobService)

                        every { jobService.findJobForInternalUseById(any(), any()) } answers {
                            jobInfo
                        }

                        every { jobService.followStdStreams(any(), any())} answers {
                            followStdResponse
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get,
                                "/api/hpc/jobs/follow/2?stderrLineStart=0&stderrMaxLines=100" +
                                        "&stdoutLineStart=0&stdoutMaxLines=100") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                        val obj = mapper.readTree(response.content)

                        assertEquals("\"RUNNING\"", obj["state"].toString())
                        assertEquals("\"error\"" ,obj["stderr"].toString())
                    }
                )
            }
        }
    }

    @Test
    fun `Follow test - followStdStreams throws Not Found exception`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val jobService = mockk<JobService<HibernateSession>>()
                        configureJobServer(db, jobService)

                        every { jobService.findJobForInternalUseById(any(), any()) } answers {
                            null
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get,
                                "/api/hpc/jobs/follow/2?stderrLineStart=0&stderrMaxLines=100" +
                                        "&stdoutLineStart=0&stdoutMaxLines=100") {
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
    fun `Follow test - followStdStreams throws InvalidRequest exception`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val jobService = mockk<JobService<HibernateSession>>()
                        configureJobServer(db, jobService)

                        every { jobService.findJobForInternalUseById(any(), any()) } answers {
                            jobInfo
                        }

                        every { jobService.followStdStreams(any(), any())} answers {
                            throw JobServiceException.InvalidRequest("job")
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get,
                                "/api/hpc/jobs/follow/2?stderrLineStart=0&stderrMaxLines=100" +
                                        "&stdoutLineStart=0&stdoutMaxLines=100") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.BadRequest, response.status())
                    }
                )
            }
        }
    }
}