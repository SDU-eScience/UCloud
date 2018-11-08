package dk.sdu.cloud.accounting.compute.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.CurrentUsageResponse
import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.accounting.compute.services.CompletedJobsHibernateDao
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.accounting.compute.testUtils.withAuthMock
import dk.sdu.cloud.accounting.compute.testUtils.withDatabase
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.service.Page
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
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

private fun io.ktor.application.Application.configureJobsStartedServer(
) {
    configureBaseServer(JobsStartedController())
}
class JobsStartedTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `Testing list Events - no params`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val completeJobsDao = CompletedJobsHibernateDao()
                        val completeJobsService = CompletedJobsService(db, completeJobsDao)
                        configureJobsStartedServer()

                        val events = (0 until 10).map { dummyEvent }
                        completeJobsService.insertBatch(events)
                    },

                    test = {
                        run {
                            val response =
                                handleRequest(
                                    HttpMethod.Get,
                                    "/api/accounting/compute/jobsStarted/events"
                                )
                                {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser(role = Role.ADMIN)
                                }.response

                            assertEquals(HttpStatusCode.OK, response.status())
                            val items = mapper.readValue<Page<AccountingJobCompletedEvent>>(response.content!!)
                            println(response.content)
                            assertEquals(0, items.items.first().nodes)
                            assertEquals(1, items.items.first().totalDuration.hours)
                            assertEquals(0, items.items.first().totalDuration.minutes)
                            assertEquals(0, items.items.first().totalDuration.seconds)
                            assertEquals("user", items.items.first().startedBy)

                        }
                    }
                )
            }
        }
    }

    @Test
    fun `Testing chart`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val completeJobsDao = CompletedJobsHibernateDao()
                        val completeJobsService = CompletedJobsService(db, completeJobsDao)
                        configureJobsStartedServer()

                        val events = (0 until 10).map { dummyEvent }
                        completeJobsService.insertBatch(events)
                    },

                    test = {
                        run {
                            val response =
                                handleRequest(
                                    HttpMethod.Get,
                                    "/api/accounting/compute/jobsStarted/chart"
                                )
                                {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser(role = Role.ADMIN)
                                }.response

                            assertEquals(HttpStatusCode.OK, response.status())
                            //TODO Works but not pretty
                            println(response.content)
                            assertTrue(response.content?.contains("\"xaxisLabel\":\"Time\"")!!)
                            assertTrue(response.content?.contains("\"yaxisLabel\":\"Total usage\"")!!)
                        }
                    }
                )
            }
        }
    }

    @Test
    fun `Testing currentUsage`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val completeJobsDao = CompletedJobsHibernateDao()
                        val completeJobsService = CompletedJobsService(db, completeJobsDao)
                        configureJobsStartedServer()

                        val events = (0 until 10).map { dummyEvent }
                        completeJobsService.insertBatch(events)
                    },

                    test = {
                        run {
                            val response =
                                handleRequest(
                                    HttpMethod.Get,
                                    "/api/accounting/compute/jobsStarted/usage"
                                )
                                {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser(role = Role.ADMIN)
                                }.response

                            assertEquals(HttpStatusCode.OK, response.status())
                            val items = mapper.readValue<CurrentUsageResponse>(response.content!!)
                            assertEquals(3600000, items.usage)
                        }
                    }
                )
            }
        }
    }

}
