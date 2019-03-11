package dk.sdu.cloud.activity.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.Role
import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.api.ListActivityByIdResponse
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.activity.util.setUser
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.paginate
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class ActivityControllerTest {
    private fun TestApplicationRequest.addJobID() {
        addHeader("Job-Id", UUID.randomUUID().toString())
    }

    @Test
    fun `test listByFileId authenticated`() {
        val activityService: ActivityService<Unit> = mockk()
        val controller = ActivityController(activityService)

        withKtorTest(
            setup = { listOf(controller) },
            test = {
                with(engine) {
                    val fileId = "file"
                    val paginationRequest = PaginationRequest().normalize()
                    val expectedResult =
                        listOf(ActivityEvent.Download("user1", 0L, fileId, "file", null)).paginate(paginationRequest)

                    every { activityService.findEventsForFileId(any(), any()) } returns expectedResult

                    val response = handleRequest(HttpMethod.Get, "/api/activity/by-file-id?id=$fileId") {
                        setUser(role = Role.ADMIN)
                        addJobID()
                    }

                    assertEquals(HttpStatusCode.OK, response.response.status())

                    val parsedResult = defaultMapper.readValue<ListActivityByIdResponse>(response.response.content!!)
                    assertEquals(expectedResult, parsedResult)

                    verify(exactly = 1) {
                        activityService.findEventsForFileId(
                            match { it.itemsPerPage == paginationRequest.itemsPerPage && it.page == paginationRequest.page },
                            fileId
                        )
                    }
                }
            }
        )
    }

    @Test
    fun `test listByFileId not authenticated`() {
        val activityService: ActivityService<Unit> = mockk()
        val controller = ActivityController(activityService)

        withKtorTest(
            setup = { listOf(controller) },
            test = {
                with(engine) {
                    val fileId = "file"
                    val response = handleRequest(HttpMethod.Get, "/api/activity/by-file-id?id=$fileId") {
                        setUser(role = Role.USER)
                        addJobID()
                    }

                    assertEquals(HttpStatusCode.Unauthorized, response.response.status())
                }
            }
        )
    }

    @Test
    fun `test listByPath authenticated`() {
        val activityService: ActivityService<Unit> = mockk()
        val controller = ActivityController(activityService)

        withKtorTest(
            setup = { listOf(controller) },
            test = {
                with(engine) {
                    val path = "/file"
                    val paginationRequest = PaginationRequest().normalize()
                    val expectedResult =
                        listOf(ActivityEvent.Download("user1", 0L, "123", "file", null)).paginate(paginationRequest)

                    coEvery {
                        activityService.findEventsForPath(
                            any(),
                            path,
                            any(),
                            any()
                        )
                    } returns expectedResult

                    val response = handleRequest(HttpMethod.Get, "/api/activity/by-path?path=$path") {
                        setUser()
                        addJobID()
                    }

                    assertEquals(HttpStatusCode.OK, response.response.status())

                    val parsedResult =
                        defaultMapper.readValue<ListActivityByIdResponse>(response.response.content!!)
                    assertEquals(expectedResult, parsedResult)

                    coVerify(exactly = 1) { activityService.findEventsForPath(any(), path, any(), any()) }
                }
            }
        )
    }

    @Test
    fun `test listByPath not authenticated`() {
        val activityService: ActivityService<Unit> = mockk()
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
}
