package dk.sdu.cloud.activity.http

import dk.sdu.cloud.activity.api.*
import dk.sdu.cloud.activity.services.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.test.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import org.apache.logging.log4j.core.config.*
import org.junit.Test
import java.util.*
import kotlin.test.*

class ActivityControllerTest {
    init {
        ConfigurationFactory.setConfigurationFactory(Log4j2ConfigFactory)
    }

    private fun TestApplicationRequest.addJobID() {
        addHeader("Job-Id", UUID.randomUUID().toString())
    }

    @Test
    fun `test listByPath authenticated`() {
        val activityService = mockk<ActivityService>()
        val controller = ActivityController(activityService)

        withKtorTest(
            setup = { listOf(controller) },
            test = {
                val path = "/file"
                val event = ActivityEvent.Deleted(
                    TestUsers.user.username,
                    0L,
                    "path"
                )
                val expectedResult = listOf(
                    ActivityForFrontend(
                        ActivityEventType.deleted,
                        0L,
                        event
                    )
                )

                coEvery {
                    activityService.findEventsForPath(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                    )
                } returns Page(1, 10, 0, expectedResult)

                val response = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/activity/by-path",
                    user = TestUsers.user,
                    params = mapOf("path" to path)
                ) { addHeader("Job-Id", UUID.randomUUID().toString()) }

                response.assertSuccess()

                assertTrue(response.response.content?.contains("\"itemsInTotal\":1")!!)

                coVerify(exactly = 1) { activityService.findEventsForPath(any(), path, any(), any(), any()) }
            }
        )
    }

    @Test
    fun `test listByPath not authenticated`() {
        val activityService: ActivityService = mockk()
        val controller = ActivityController(activityService)

        withKtorTest(
            setup = { listOf(controller) },
            test = {
                with(engine) {
                    val path = "/file"
                    val response = handleRequest(HttpMethod.Get, "/api/activity/by-path?path=$path") {
                        addJobID()
                    }

                    assertEquals(HttpStatusCode.Unauthorized, response.response.status())
                }
            }
        )
    }

    @Test
    fun `test browse`() {
        val activityService = mockk<ActivityService>()
        val controller = ActivityController(activityService)

        withKtorTest(
            setup = { listOf(controller) },
            test = {
                val user = TestUsers.user.username

                coEvery { activityService.browseActivity(any(), any(), any(), any()) } answers {
                    val items = emptyList<ActivityForFrontend>()
                    ScrollResult(items, 0, true)
                }

                val response = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/activity/browse",
                    user = TestUsers.user,
                    params = mapOf(
                        "user" to user,
                        "scrollSize" to 250)
                ) { addHeader("Job-Id", UUID.randomUUID().toString()) }

                response.assertSuccess()
            }

        )
    }
}
