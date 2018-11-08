package dk.sdu.cloud.accounting.compute.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.InvoiceReport
import dk.sdu.cloud.accounting.api.ListResourceResponse
import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.accounting.compute.services.CompletedJobsHibernateDao
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun TestApplicationRequest.setUser(username: String = "user1", role: Role = Role.USER) {
    addHeader(HttpHeaders.Authorization, "Bearer ${TokenValidationMock.createTokenForUser(username, role)}")
}

private val dummyEvent = AccountingJobCompletedEvent(
    NameAndVersion("foo", "1.0.0"),
    1,
    SimpleDuration(1, 0, 0),
    "user1",
    "job-id",
    System.currentTimeMillis()
)

private fun KtorApplicationTestSetupContext.configureComputeAccoutingServer(
    completeJobService: CompletedJobsService<HibernateSession>
): List<Controller> {
    return listOf(ComputeAccountingController(completeJobService))
}

class ComputeAccountingTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `Testing accounting`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val completeJobsDao = CompletedJobsHibernateDao()
                val completeJobsService = CompletedJobsService(micro.hibernateDatabase, completeJobsDao)

                val events = (0 until 10).map { dummyEvent }
                completeJobsService.insertBatch(events)
                configureComputeAccoutingServer(completeJobsService)
            },

            test = {
                with(engine) {
                    val now = System.currentTimeMillis()
                    run {
                        val report =
                            handleRequest(
                                HttpMethod.Post,
                                "/api/accounting/compute/buildReport"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setBody(
                                    """
                                        {
                                    "periodStartMs": 1,
                                    "periodEndMs": $now
                                    }
                                """.trimIndent()
                                )
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, report.status())
                        val items = mapper.readValue<InvoiceReport>(report.content!!)
                        assertEquals(60, items.items.first().units)
                    }
                }
            }
        )
    }

    @Test
    fun `Testing accounting - Unauthorized`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val completeJobsDao = CompletedJobsHibernateDao()
                val completeJobsService = CompletedJobsService(micro.hibernateDatabase, completeJobsDao)

                val events = (0 until 10).map { dummyEvent }
                completeJobsService.insertBatch(events)
                configureComputeAccoutingServer(completeJobsService)
            },

            test = {
                with(engine) {
                    val now = System.currentTimeMillis()
                    run {
                        val report =
                            handleRequest(
                                HttpMethod.Post,
                                "/api/accounting/compute/buildReport"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setBody(
                                    """
                                        {
                                    "periodStartMs": 1,
                                    "periodEndMs": $now
                                    }
                                """.trimIndent()
                                )
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.Unauthorized, report.status())
                    }
                }
            }
        )
    }

    @Test
    fun `Testing list resources`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val completeJobsDao = CompletedJobsHibernateDao()
                val completeJobsService = CompletedJobsService(micro.hibernateDatabase, completeJobsDao)

                val events = (0 until 10).map { dummyEvent }
                completeJobsService.insertBatch(events)
                configureComputeAccoutingServer(completeJobsService)
            },

            test = {
                with(engine) {
                    run {
                        val result =
                            handleRequest(
                                HttpMethod.Get,
                                "/api/accounting/compute/list"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, result.status())
                        val items = mapper.readValue<ListResourceResponse>(result.content!!)
                        var counter = 0
                        items.resources.forEach { resource ->
                            if (counter == 0) assertEquals("timeUsed", resource.name)
                            if (counter == 1) assertEquals("jobsStarted", resource.name)
                            if (counter > 1) assertTrue(false)
                            counter++
                        }
                    }
                }
            }
        )
    }
}
