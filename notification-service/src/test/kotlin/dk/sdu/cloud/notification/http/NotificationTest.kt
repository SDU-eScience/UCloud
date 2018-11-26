package dk.sdu.cloud.notification.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.DeleteResponse
import dk.sdu.cloud.notification.api.MarkResponse
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationServiceDescription
import dk.sdu.cloud.notification.services.NotificationHibernateDAO
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.KtorApplicationTestContext
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.parseSuccessful
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationCall
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationTest {
    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        micro.install(HibernateFeature)
        listOf(NotificationController(micro.hibernateDatabase, NotificationHibernateDAO()))
    }

    private fun KtorApplicationTestContext.listPage(user: SecurityPrincipal): Page<Notification> {
        return sendRequest(
            method = HttpMethod.Get,
            path = "/api/notifications",
            user = user
        ).parseSuccessful()
    }

    private fun KtorApplicationTestContext.createNotification(
        createdNotification: Notification,
        createdBy: SecurityPrincipal,
        createdFor: String = "user"
    ): TestApplicationCall {
        return sendJson(
            method = HttpMethod.Put,
            path = "/api/notifications",
            user = createdBy,
            request = CreateNotification(createdFor, createdNotification)
        )
    }

    @Test
    fun `test create, mark, and list`() {
        withKtorTest(
            setup,
            test = {
                val createdNotification = Notification(
                    type = "type",
                    message = "You Got MAIL!!!",
                    meta = mapOf("foo" to 42)
                )

                val createdFor = TestUsers.user
                createNotification(createdNotification, TestUsers.admin, createdFor.username).assertSuccess()

                val id = run {
                    val resp = listPage(createdFor)

                    assertEquals(1, resp.itemsInTotal)
                    assertEquals(1, resp.items.size)
                    val firstNotification = resp.items.first()
                    assert(createdNotification.contentEquals(firstNotification))
                    assertFalse(firstNotification.read)

                    firstNotification.id
                }

                sendRequest(
                    method = HttpMethod.Post,
                    path = "/api/notifications/read/$id",
                    user = createdFor
                ).assertSuccess()


                run {
                    val resp = listPage(createdFor)
                    assertEquals(1, resp.itemsInTotal)
                    assertEquals(1, resp.items.size)
                    val firstNotification = resp.items.first()
                    assert(createdNotification.contentEquals(firstNotification))
                    assertTrue(firstNotification.read)
                }
            }
        )
    }

    private fun Notification.contentEquals(other: Notification): Boolean {
        return this.type == other.type &&
                this.message == other.message &&
                this.meta == other.meta
    }

    @Test
    fun `create - not admin - test`() {
        withKtorTest(
            setup,
            test = {
                createNotification(
                    Notification("type", "message"),
                    TestUsers.user
                ).assertStatus(HttpStatusCode.Unauthorized)
            }
        )
    }

    @Test
    fun `Delete unknown id`() {
        withKtorTest(
            setup,
            test = {
                sendRequest(
                    method = HttpMethod.Delete,
                    path = "/api/notifications/123124",
                    user = TestUsers.admin
                ).assertStatus(HttpStatusCode.NotFound)
            }
        )
    }

    @Test
    fun `Delete unknown and unknown id`() {
        withKtorTest(
            setup,
            test = {
                val createdNotification = Notification(
                    type = "type",
                    message = "You Got MAIL!!!",
                    meta = mapOf("foo" to 42)
                )

                val createdFor = TestUsers.user
                createNotification(createdNotification, TestUsers.admin, createdFor.username).assertSuccess()

                val id = run {
                    val resp = listPage(createdFor)

                    assertEquals(1, resp.itemsInTotal)
                    assertEquals(1, resp.items.size)
                    val firstNotification = resp.items.first()
                    assert(createdNotification.contentEquals(firstNotification))
                    assertFalse(firstNotification.read)

                    firstNotification.id
                }

                val requestResponse = sendRequest(
                    method = HttpMethod.Delete,
                    path = "/api/notifications/123124,$id,123456",
                    user = TestUsers.admin
                )
                requestResponse.assertStatus(HttpStatusCode.OK)

                val responseList = defaultMapper.readValue<DeleteResponse>(requestResponse.response.content!!)

                assertEquals(123124, responseList.failures.first())
                assertEquals(123456, responseList.failures.last())
                assertTrue(!responseList.failures.contains(id))

            }
        )
    }

    @Test
    fun `Mark unknown id`() {
        withKtorTest(
            setup,
            test = {
                sendRequest(
                    method = HttpMethod.Post,
                    path = "/api/notifications/read/123456,1222",
                    user = TestUsers.user
                ).assertStatus(HttpStatusCode.NotFound)
            }
        )
    }

    @Test
    fun `Mark known and unknown id`() {
        withKtorTest(
            setup,
            test = {
                val createdNotification = Notification(
                    type = "type",
                    message = "You Got MAIL!!!",
                    meta = mapOf("foo" to 42)
                )

                val createdFor = TestUsers.user
                createNotification(createdNotification, TestUsers.admin, createdFor.username).assertSuccess()

                val id = run {
                    val resp = listPage(createdFor)

                    assertEquals(1, resp.itemsInTotal)
                    assertEquals(1, resp.items.size)
                    val firstNotification = resp.items.first()
                    assert(createdNotification.contentEquals(firstNotification))
                    assertFalse(firstNotification.read)

                    firstNotification.id
                }

                val requestResponse = sendRequest(
                    method = HttpMethod.Post,
                    path = "/api/notifications/read/123456,$id,1222",
                    user = TestUsers.user
                )

                requestResponse.assertStatus(HttpStatusCode.OK)
                val responseList = defaultMapper.readValue<MarkResponse>(requestResponse.response.content!!)

                assertEquals(123456, responseList.failures.first())
                assertEquals(1222, responseList.failures.last())
                assertTrue(!responseList.failures.contains(id))

                run {
                    val resp = listPage(createdFor)
                    assertEquals(1, resp.itemsInTotal)
                    assertEquals(1, resp.items.size)
                    val firstNotification = resp.items.first()
                    assert(createdNotification.contentEquals(firstNotification))
                    assertTrue(firstNotification.read)
                }
            }
        )
    }

    @Test
    fun `Simple NotificationServiceDescription for CC`() {
        val nsd = NotificationServiceDescription
        assertEquals(nsd.version, NotificationServiceDescription.version)
        assertEquals(nsd.name, NotificationServiceDescription.name)
    }
}
