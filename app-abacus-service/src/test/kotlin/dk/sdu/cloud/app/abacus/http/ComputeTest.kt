package dk.sdu.cloud.app.abacus.http

import dk.sdu.cloud.Role
import dk.sdu.cloud.app.abacus.api.AbacusComputationDescriptions
import dk.sdu.cloud.app.abacus.service.JobData
import dk.sdu.cloud.app.abacus.services.JobFileService
import dk.sdu.cloud.app.abacus.services.JobTail
import dk.sdu.cloud.app.abacus.services.SlurmScheduler
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnection
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.api.InternalFollowStdStreamsRequest
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

fun TestApplicationRequest.setUser(username: String = "user1", role: Role = Role.USER) {
    addHeader(HttpHeaders.Authorization, "Bearer ${TokenValidationMock.createTokenForUser(username, role)}")
}

private fun configureComputeServer(
    jobFileService: JobFileService,
    slurmScheduler: SlurmScheduler<HibernateSession>,
    jobTail: JobTail
): List<Controller> {
    return listOf(ComputeController(jobFileService, slurmScheduler, jobTail, AbacusComputationDescriptions))
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

    // TODO Submit test

    @Test
    fun `Verified test`() {
        withKtorTest(
            setup = {
                val slurmScheduler = mockk<SlurmScheduler<HibernateSession>>()
                configureComputeServer(jobFileService, slurmScheduler, service)
            },
            test = {
                with(engine) {
                    run {
                        val response =
                            handleRequest(
                                HttpMethod.Post,
                                "/api/app/compute/abacus/job-verified"
                            ) {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setBody(validatedJobAsJson)
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                    }
                }
            }
        )
    }

    @Test
    fun `Verified test - unauthorized`() {
        withKtorTest(
            setup = {
                val slurmScheduler = mockk<SlurmScheduler<HibernateSession>>()
                configureComputeServer(jobFileService, slurmScheduler, service)
            },
            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `Prepared test`() {
        withKtorTest(
            setup = {
                val slurmScheduler = mockk<SlurmScheduler<HibernateSession>>()
                coEvery { slurmScheduler.schedule(any()) } just Runs
                configureComputeServer(jobFileService, slurmScheduler, service)
            },
            test = {
                with(engine) {
                    run {
                        val response = handleRequest(
                            HttpMethod.Post,
                            "/api/app/compute/abacus/job-prepared"
                        ) {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setBody(validatedJobAsJson)
                            setUser(role = Role.ADMIN)
                        }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                    }
                }
            }
        )
    }

    @Test
    fun `cleanup test`() {
        withKtorTest(
            setup = {
                val slurmScheduler = mockk<SlurmScheduler<HibernateSession>>()
                configureComputeServer(jobFileService, slurmScheduler, service)
            },
            test = {
                with(engine) {
                    run {
                        val response =
                            handleRequest(
                                HttpMethod.Post,
                                "/api/app/compute/abacus/cleanup"
                            ) {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setBody(validatedJobAsJson)
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                    }
                }
            }
        )
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
        withKtorTest(
            setup = {
                val slurmScheduler = mockk<SlurmScheduler<HibernateSession>>()
                configureComputeServer(jobFileService, slurmScheduler, service)
            },
            test = {
                with(engine) {
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
            }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `follow test - invalid line input`() {
        withKtorTest(
            setup = {
                val slurmScheduler = mockk<SlurmScheduler<HibernateSession>>()
                configureComputeServer(jobFileService, slurmScheduler, service)
            },
            test = {
                with(engine) {
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
            }
        )
    }
}
