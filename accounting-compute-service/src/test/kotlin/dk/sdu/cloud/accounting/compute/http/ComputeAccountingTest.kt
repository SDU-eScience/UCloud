package dk.sdu.cloud.accounting.compute.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.accounting.api.BuildReportRequest
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
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
    micro.install(HibernateFeature)
    val completeJobsDao = CompletedJobsHibernateDao()
    val completeJobsService = CompletedJobsService(micro.hibernateDatabase, completeJobsDao)

    val events = (0 until 10).map { dummyEvent }
    completeJobsService.insertBatch(events)
    configureComputeAccountingServer(completeJobsService)
}

private val dummyEvent = AccountingJobCompletedEvent(
    NameAndVersion("foo", "1.0.0"),
    1,
    SimpleDuration(1, 0, 0),
    TestUsers.user.username,
    "job-id",
    System.currentTimeMillis()
)

private fun KtorApplicationTestSetupContext.configureComputeAccountingServer(
    completeJobService: CompletedJobsService<HibernateSession>
): List<Controller> {
    return listOf(ComputeAccountingController(completeJobService))
}

class ComputeAccountingTest {

    private val mapper = jacksonObjectMapper()
    private val now = System.currentTimeMillis()
    private val buildReportRequest = BuildReportRequest( TestUsers.user.username, 1, now)

    @Test
    fun `Testing accounting`() {
        withKtorTest(
            setup,

            test = {
                with(engine) {
                    run {
                        val request = sendJson(
                            method = HttpMethod.Post,
                            path = "api/accounting/compute/buildReport",
                            user = TestUsers.service.copy(username = "_accounting"),
                            request = buildReportRequest
                        )
                        request.assertSuccess()

                        val response = mapper.readValue<InvoiceReport>(request.response.content!!)
                        assertEquals(60, response.items.first().units)
                        val totalPrice =
                            response.items.first().unitPrice.amountAsDecimal.multiply(
                                    response.items.first().units.toBigDecimal()
                                )

                        assertEquals(0.006, totalPrice.toDouble())
                    }

                    // No usage test
                    run {
                        val request = sendJson(
                            method = HttpMethod.Post,
                            path = "/api/accounting/compute/buildReport",
                            user = TestUsers.service.copy(username = "_accounting"),
                            request = buildReportRequest.copy(user = "user2")
                        )
                        request.assertSuccess()
                        val response = mapper.readValue<InvoiceReport>(request.response.content!!)
                        assertEquals(0, response.items.first().units)
                    }
                }
            }
        )
    }

    @Test
    fun `Testing accounting - Unauthorized`() {
        withKtorTest(
            setup,

            test = {
                with(engine) {
                    run {
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/api/accounting/compute/buildReport",
                            user = TestUsers.user,
                            request = buildReportRequest
                        ).assertStatus(HttpStatusCode.Unauthorized)
                    }
                }
            }
        )
    }

    @Test
    fun `Testing list resources`() {
        withKtorTest(
            setup,

            test = {
                with(engine) {
                    run {
                        val request = sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/accounting/compute/list",
                            user = TestUsers.user
                        )
                        request.assertSuccess()

                        val items = mapper.readValue<ListResourceResponse>(request.response.content!!)
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
