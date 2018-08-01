import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.service.*
import dk.sdu.cloud.metadata.utils.withAuthMock
import dk.sdu.cloud.notification.http.NotificationController
import dk.sdu.cloud.notification.services.InMemoryNotificationDAO
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun TestApplicationRequest.setUser(username: String = "user", role: Role = dk.sdu.cloud.auth.api.Role.USER) {
    addHeader(io.ktor.http.HttpHeaders.Authorization, "Bearer $username/$role")
}

fun Application.configureBaseServer(vararg controllers: Controller) {
    installDefaultFeatures(
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        requireJobId = true
    )

    install(JWTProtection)

    routing {
        protect()
        configureControllers(*controllers)
    }
}

private fun Application.configureNotificationServer(notificationDao: InMemoryNotificationDAO) {
    configureBaseServer(NotificationController(mockk(relaxed = true), notificationDao))
}

class NotificationTest {
    private val mapper = jacksonObjectMapper()

    private fun getID(response: String): String {
        val splittetList = response.split('"')
        val idIndex = splittetList.indexOf("id")
        return splittetList[idIndex + 2]
    }

    @Test
    fun `Create, mark, list and delete test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val notificationDao = InMemoryNotificationDAO()
                    configureNotificationServer(notificationDao)
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Put, "/api/notifications") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                            setBody(
                                """
                                {
                                    "user":"user",
                                    "notification":{
                                        "type":"type",
                                        "message":"You Got MAIL!!!"
                                    }
                                }
                            """.trimIndent()
                            )
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 =
                        handleRequest(HttpMethod.Get, "/api/notifications") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.OK, response2.status())

                    assertTrue(
                        response2.content.toString().contains(
                            "\"message\":\"You Got MAIL!!!\""
                        )
                    )
                    assertTrue(
                        response2.content.toString().contains(
                            "\"read\":false"
                        )
                    )

                    val id = getID(response2.content.toString())

                    val response3 =
                        handleRequest(HttpMethod.Post, "/api/notifications/read/${id}") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.OK, response3.status())

                    val response4 =
                        handleRequest(HttpMethod.Get, "/api/notifications") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.OK, response4.status())

                    assertTrue(
                        response4.content.toString().contains(
                            "\"read\":true"
                        )
                    )

                    val response5 =
                        handleRequest(HttpMethod.Delete, "/api/notifications/${id}") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.OK, response5.status())

                    val response6 =
                        handleRequest(HttpMethod.Get, "/api/notifications") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.OK, response6.status())
                    val results = mapper.readTree(response6.content)
                    assertEquals("0", results["itemsInTotal"].toString())
                }
            )
        }
    }

    @Test
    fun `create - not admin - test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val notificationDao = InMemoryNotificationDAO()
                    configureNotificationServer(notificationDao)
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Put, "/api/notifications") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.USER)
                            setBody(
                                """
                                {
                                    "user":"user",
                                    "notification":{
                                        "type":"type",
                                        "message":"You Got MAIL!!!"
                                    }
                                }
                            """.trimIndent()
                            )
                        }.response

                    assertEquals(HttpStatusCode.Unauthorized, response.status())

                }
            )
        }
    }

    @Test
    fun `Delete unknown id`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val notificationDao = InMemoryNotificationDAO()
                    configureNotificationServer(notificationDao)
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Delete, "/api/notifications/2") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.BadRequest, response.status())

                }
            )
        }
    }

    @Test
    fun `Mark unknown id`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val notificationDao = InMemoryNotificationDAO()
                    configureNotificationServer(notificationDao)
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Post, "/api/notifications/read/2") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.BadRequest, response.status())

                }
            )
        }
    }
}