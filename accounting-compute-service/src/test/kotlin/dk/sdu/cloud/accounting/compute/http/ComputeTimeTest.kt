package dk.sdu.cloud.accounting.compute.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.CurrentUsageResponse
import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.accounting.compute.services.CompletedJobsHibernateDao
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val dummyEvent = AccountingJobCompletedEvent(
    NameAndVersion("foo", "1.0.0"),
    1,
    SimpleDuration(1, 0, 0),
    "user1",
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
            setup = {
                micro.install(HibernateFeature)
                val completeJobsDao = CompletedJobsHibernateDao()
                val completeJobsService = CompletedJobsService(micro.hibernateDatabase, completeJobsDao)

                val events = (0 until 10).map { dummyEvent }
                completeJobsService.insertBatch(events)
                configureComputeTimeServer(completeJobsService)
            },

            test = {
                with(engine) {
                    run {
                        val response =
                            handleRequest(
                                HttpMethod.Get,
                                "/api/accounting/compute/timeUsed/events?user=user1"
                            ) {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        val items = mapper.readValue<Page<AccountingJobCompletedEvent>>(response.content!!)
                        assertEquals(1, items.items.first().nodes)
                        assertEquals(1, items.items.first().totalDuration.hours)
                        assertEquals(0, items.items.first().totalDuration.minutes)
                        assertEquals(0, items.items.first().totalDuration.seconds)
                        assertEquals("user1", items.items.first().startedBy)

                    }
                }
            }
        )
    }

    @Test
    fun `Testing chart`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val completeJobsDao = CompletedJobsHibernateDao()
                val completeJobsService = CompletedJobsService(micro.hibernateDatabase, completeJobsDao)

                val events = (0 until 10).map { dummyEvent }
                completeJobsService.insertBatch(events)
                configureComputeTimeServer(completeJobsService)
            },

            test = {
                with(engine) {
                    run {
                        val response =
                            handleRequest(
                                HttpMethod.Get,
                                "/api/accounting/compute/timeUsed/chart?user=user1"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        //TODO Works but not pretty
                        assertTrue(response.content?.contains("\"xaxisLabel\":\"Time\"")!!)
                        assertTrue(response.content?.contains("\"yaxisLabel\":\"Compute time used\"")!!)
                    }
                }
            }
        )
    }

    @Test
    fun `Testing currentUsage`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val completeJobsDao = CompletedJobsHibernateDao()
                val completeJobsService = CompletedJobsService(micro.hibernateDatabase, completeJobsDao)

                val events = (0 until 10).map { dummyEvent }
                completeJobsService.insertBatch(events)
                configureComputeTimeServer(completeJobsService)
            },

            test = {
                with(engine) {
                    run {
                        val response =
                            handleRequest(
                                HttpMethod.Get,
                                "/api/accounting/compute/timeUsed/usage?user=user1"
                            ) {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        val items = mapper.readValue<CurrentUsageResponse>(response.content!!)
                        assertEquals(3600000, items.usage)
                    }
                }
            }
        )
    }
}
