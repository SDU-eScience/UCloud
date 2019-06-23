package dk.sdu.cloud.accounting.compute.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.accounting.api.UsageResponse
import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.accounting.compute.services.CompletedJobsHibernateDao
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
    micro.install(HibernateFeature)
    val completeJobsDao = CompletedJobsHibernateDao()
    val completeJobsService =
        CompletedJobsService(micro.hibernateDatabase, completeJobsDao, ClientMock.authenticatedClient)

    val events = (0 until 10).map { dummyEvent }
    completeJobsService.insertBatch(events)
    configureComputeTimeServer(completeJobsService)
}

private val dummyEvent = AccountingJobCompletedEvent(
    NameAndVersion("foo", "1.0.0"),
    1,
    SimpleDuration(1, 0, 0),
    TestUsers.user.username,
    "job-id",
    System.currentTimeMillis()
)

private fun KtorApplicationTestSetupContext.configureComputeTimeServer(
    completeJobService: CompletedJobsService<HibernateSession>
): List<Controller> {
    return listOf(ComputeTimeController(completeJobService))
}

class ComputeTimeTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `Testing list Events - no params`() {
        withKtorTest(
            setup,

            test = {
                with(engine) {
                    run {
                        val request = sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/accounting/compute/timeUsed/events",
                            user = TestUsers.user
                        )
                        request.assertSuccess()

                        val response = mapper.readValue<Page<AccountingJobCompletedEvent>>(request.response.content!!)
                        assertEquals(1, response.items.first().nodes)
                        assertEquals(1, response.items.first().totalDuration.hours)
                        assertEquals(0, response.items.first().totalDuration.minutes)
                        assertEquals(0, response.items.first().totalDuration.seconds)
                        assertEquals(TestUsers.user.username, response.items.first().startedBy)

                    }
                }
            }
        )
    }

    @Test
    fun `Testing chart`() {
        withKtorTest(
            setup,

            test = {
                with(engine) {
                    run {
                        val request = sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/accounting/compute/timeUsed/chart",
                            user = TestUsers.user
                        )

                        request.assertSuccess()
                        //TODO Works but not pretty
                        println(request.response.content)
                        assertTrue(
                            request.response.content?.contains(
                                "\"dataTypes\":[\"datetime\",\"duration\"],\"dataTitle\":\"Compute Time Used\""
                            )!!
                        )
                    }
                }
            }
        )
    }

    @Test
    fun `Testing currentUsage`() {
        withKtorTest(
            setup,

            test = {
                with(engine) {
                    run {
                        val request = sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/accounting/compute/timeUsed/usage",
                            user = TestUsers.user
                        )
                        request.assertSuccess()

                        val items = mapper.readValue<UsageResponse>(request.response.content!!)
                        assertEquals(3600000, items.usage)
                    }
                }
            }
        )
    }
}
