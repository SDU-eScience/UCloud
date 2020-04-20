package dk.sdu.cloud.activity.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.api.ActivityEventType
import dk.sdu.cloud.activity.api.ActivityForFrontend
import dk.sdu.cloud.activity.api.ListActivityByPathResponse
import dk.sdu.cloud.activity.services.ActivityEventElasticDao
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.ElasticFeature
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.ScrollResult
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityControllerTest {
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
                    path = "/api/activity/browse/user",
                    user = TestUsers.user,
                    params = mapOf("scrollSize" to 250)
                ) { addHeader("Job-Id", UUID.randomUUID().toString()) }

                response.assertSuccess()
            }

        )
    }
}
