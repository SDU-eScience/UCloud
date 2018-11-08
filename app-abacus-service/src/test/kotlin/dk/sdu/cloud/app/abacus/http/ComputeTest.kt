package dk.sdu.cloud.app.abacus.http

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ser.BeanSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.Role
import dk.sdu.cloud.app.abacus.Utils.withAuthMock
import dk.sdu.cloud.app.abacus.service.JobData
import dk.sdu.cloud.app.abacus.services.JobFileService
import dk.sdu.cloud.app.abacus.services.JobTail
import dk.sdu.cloud.app.abacus.services.SlurmScheduler
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnection
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.api.InternalFollowStdStreamsRequest
import dk.sdu.cloud.app.api.SubmitFileToComputation
import dk.sdu.cloud.client.StreamingFile
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.application.Application
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import io.ktor.http.content.PartData
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.experimental.io.ByteReadChannel
import kotlinx.coroutines.experimental.io.jvm.javaio.toInputStream
import kotlinx.io.streams.asInput
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.test.assertEquals

fun Application.configureBaseServer(vararg controllers: Controller) {
    installDefaultFeatures(
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        requireJobId = true
    )

    routing {
        configureControllers(*controllers)
    }
}

fun TestApplicationRequest.setUser(username: String = "user1", role: Role = Role.USER) {
    addHeader(HttpHeaders.Authorization, "Bearer $username/$role")
}

private fun Application.configureComputeServer(
    jobFileService: JobFileService,
    slurmScheduler: SlurmScheduler<HibernateSession>,
    jobTail: JobTail
) {
    configureBaseServer(ComputeController(jobFileService, slurmScheduler, jobTail))
}
class ComputeTest {

    private val connectionPool: SSHConnectionPool = mockk(relaxed = true)
    private val connection: SSHConnection = mockk(relaxed = true)
    private val jobFileService: JobFileService = mockk(relaxed = true)
    private val service: JobTail

    private val validatedJobAsJson = defaultMapper.writeValueAsString(JobData.job)

    init {
        every { connectionPool.borrowConnection() } returns Pair(0, connection)
        service = JobTail(connectionPool, jobFileService)
    }

    @Test
    fun `submit test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val slurmScheduler = mockk<SlurmScheduler<HibernateSession>>()
                    configureComputeServer(jobFileService, slurmScheduler, service)

                   //every { jobFileService.uploadFile(any(), any(), any(), any(), any()) } just Runs
                },
                test = {
                    run {

                        val response =
                            handleRequest(HttpMethod.Post, "/api/app/compute/abacus/submit")
                            {
                                val boundary = UUID.randomUUID().toString()
                                addHeader(
                                    HttpHeaders.ContentType,
                                    ContentType.MultiPart.FormData.withParameter("boundary",boundary).toString()
                                )
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)

                                fun partName(name: String, type: ContentDisposition = ContentDisposition.Inline): Headers{
                                    return Headers.build {
                                        append(
                                            HttpHeaders.ContentDisposition,
                                            type.withParameter(ContentDisposition.Parameters.Name, name)
                                        )
                                    }
                                }
                                setBody(
                                    boundary, listOf(
                                        PartData.FormItem(
                                            "/home/somewhere/foo",
                                            {},
                                            partName("location")
                                        ),

                                        PartData.FormItem(
                                            SensitivityLevel.CONFIDENTIAL.name,
                                            {},
                                            partName("sensitivity")
                                        ),

                                        PartData.FileItem(
                                            { ByteReadChannel("hello, world").toInputStream().asInput()},
                                            {},
                                            partName(
                                                "upload",
                                                ContentDisposition.File.withParameter(
                                                    ContentDisposition.Parameters.FileName,
                                                    "filename"
                                                )
                                            )
                                        )
                                    )
                                )
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                    }
                }
            )
        }
    }

    @Test
    fun `Verified test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val slurmScheduler = mockk<SlurmScheduler<HibernateSession>>()
                    configureComputeServer(jobFileService, slurmScheduler, service)
                },
                test = {
                    run {

                        val response =
                            handleRequest(
                                HttpMethod.Post,
                                "/api/app/compute/abacus/job-verified"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setBody(validatedJobAsJson)
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                    }
                }
            )
        }
    }

    @Test
    fun `Verified test - unauthorized`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val slurmScheduler = mockk<SlurmScheduler<HibernateSession>>()
                    configureComputeServer(jobFileService, slurmScheduler, service)
                },
                test = {
                    run {

                        val response =
                            handleRequest(
                                HttpMethod.Post,
                                "/api/app/compute/abacus/job-verified"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setBody(validatedJobAsJson)
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.Unauthorized, response.status())
                    }
                }
            )
        }
    }

    @Test
    fun `Prepared test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val slurmScheduler = mockk<SlurmScheduler<HibernateSession>>()
                    configureComputeServer(jobFileService, slurmScheduler, service)

                    coEvery { slurmScheduler.schedule(any()) } just Runs
                },
                test = {
                    run {

                        val response =
                            handleRequest(
                                HttpMethod.Post,
                                "/api/app/compute/abacus/job-prepared"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setBody(validatedJobAsJson)
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                    }
                }
            )
        }
    }

    @Test
    fun `cleanup test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val slurmScheduler = mockk<SlurmScheduler<HibernateSession>>()
                    configureComputeServer(jobFileService, slurmScheduler, service)
                },
                test = {
                    run {

                        val response =
                            handleRequest(
                                HttpMethod.Post,
                                "/api/app/compute/abacus/cleanup"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setBody(validatedJobAsJson)
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                    }
                }
            )
        }
    }

    private val internalfollow = InternalFollowStdStreamsRequest(
        JobData.job,
        0,
        10,
        0,
        10
    )

    @Test
    fun `follow test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val slurmScheduler = mockk<SlurmScheduler<HibernateSession>>()
                    configureComputeServer(jobFileService, slurmScheduler, service)
                },
                test = {
                    run {

                        val response =
                            handleRequest(
                                HttpMethod.Post,
                                "/api/app/compute/abacus/follow"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setBody(defaultMapper.writeValueAsString(internalfollow))
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                    }
                }
            )
        }
    }

    @Test (expected = IllegalArgumentException::class)
    fun `follow test - invalid line input`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val slurmScheduler = mockk<SlurmScheduler<HibernateSession>>()
                    configureComputeServer(jobFileService, slurmScheduler, service)
                },
                test = {
                    run {

                        val response =
                            handleRequest(
                                HttpMethod.Post,
                                "/api/app/compute/abacus/follow"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setBody(defaultMapper.writeValueAsString(internalfollow.copy(stderrLineStart = -10)))
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                    }
                }
            )
        }
    }
}
