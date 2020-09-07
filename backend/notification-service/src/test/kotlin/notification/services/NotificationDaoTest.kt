package dk.sdu.cloud.notification.services

import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationServiceDescription
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationDaoTest {
    private val user = "user"
    private val notificationInstance = Notification(
        "type",
        "You got mail!"
    )

    private val notificationInstance2 = Notification(
        "type",
        "You got mail again!"
    )

    private lateinit var db: AsyncDBSessionFactory
    private lateinit var embDB: EmbeddedPostgres

    @BeforeTest
    fun setup() {
        val (db,embDB) = TestDB.from(NotificationServiceDescription)
        this.db = db
        this.embDB = embDB
    }

    @AfterTest
    fun close() {
        runBlocking {
            db.close()
        }
        embDB.close()
    }

    @Test
    fun `create , find, mark, delete test`() {
        val dao = NotificationDao()
        runBlocking {
            db.withSession { session ->
                val id1 = dao.create(session, TestUsers.user.username, notificationInstance)
                val id2 = dao.create(session, TestUsers.user.username, notificationInstance2)

                val results = dao.findNotifications(session, TestUsers.user.username)
                println(results)
                assertEquals(2, results.itemsInTotal)
                assertTrue(results.items.first().ts >= results.items.last().ts)

                dao.delete(session, id1)
                val resultsAfterDelete = dao.findNotifications(session, TestUsers.user.username)
                assertEquals(1, resultsAfterDelete.itemsInTotal)
                assertEquals(resultsAfterDelete.items.first().id, id2)

                assertFalse(resultsAfterDelete.items.first().read)
                dao.markAsRead(session, TestUsers.user.username, id2)

                val resultsAfterRead = dao.findNotifications(session, TestUsers.user.username)
                assertTrue(resultsAfterRead.items.first().read)

                dao.delete(session, id2)

                dao.create(session, TestUsers.user.username, notificationInstance)
                dao.create(session, TestUsers.user.username, notificationInstance2)
                dao.create(session, TestUsers.user2.username, notificationInstance2)

                val resultsNewInsertsUser = dao.findNotifications(session, TestUsers.user.username)

                assertEquals(2, resultsNewInsertsUser.itemsInTotal)
                assertFalse(resultsNewInsertsUser.items.first().read)
                assertFalse(resultsNewInsertsUser.items.last().read)

                val resultsNewInsertsUser2 = dao.findNotifications(session, TestUsers.user2.username)
                assertEquals(1, resultsNewInsertsUser2.itemsInTotal)
                assertFalse(resultsNewInsertsUser2.items.first().read)

                dao.markAllAsRead(session, TestUsers.user.username)

                val resultsNewInsertsUserAfterRead = dao.findNotifications(session, TestUsers.user.username)

                assertEquals(2, resultsNewInsertsUserAfterRead.itemsInTotal)
                assertTrue(resultsNewInsertsUserAfterRead.items.first().read)
                assertTrue(resultsNewInsertsUserAfterRead.items.last().read)

                val resultsNewInsertsUser2AfterRead = dao.findNotifications(session, TestUsers.user2.username)
                assertEquals(1, resultsNewInsertsUser2AfterRead.itemsInTotal)
                assertFalse(resultsNewInsertsUser2AfterRead.items.first().read)
            }
        }
    }

    @Test
    fun `Delete non existing`() {
        runBlocking {
            db.withSession { session ->
                val dao = NotificationDao()
                assertFalse(dao.delete(session, 292929))
            }
        }

    }

    @Test
    fun `Mark non existing`() {
        runBlocking {
            db.withSession { session ->
                val dao = NotificationDao()
                assertFalse(dao.markAsRead(session, TestUsers.user.username, 292929))
            }
        }
    }

    @Test
    fun `Mark not correct user`() {
        runBlocking {
            db.withSession { session ->
                val dao = NotificationDao()
                dao.create(session, TestUsers.user.username, notificationInstance)
                assertFalse(dao.markAsRead(session, "notMe", 1))
            }
        }
    }

    private val notificationInstance3 = Notification(
        "anotherType",
        "You got mail once more!"
    )

    @Test
    fun `Find on type`() {

        runBlocking {
            db.withSession { session ->
                val dao = NotificationDao()
                dao.create(session, user, notificationInstance)
                dao.create(session, user, notificationInstance3)
                val results = dao.findNotifications(session, user, "anotherType")
                assertEquals(1, results.itemsInTotal)
                assertEquals(2, results.items.first().id)
                assertEquals("You got mail once more!", results.items.first().message)
            }
        }
    }

    @Test
    fun `Find on time`() {
        runBlocking {
            db.withSession { session ->
                val dao = NotificationDao()
                dao.create(session, user, notificationInstance)
                Thread.sleep(1000)
                val date = Time.now()
                Thread.sleep(1000)
                dao.create(session, user, notificationInstance2)
                println(dao.findNotifications(session, user))
                val results = dao.findNotifications(session, user, since = date)
                println(results)
                assertEquals(1, results.itemsInTotal)
                assertEquals(2, results.items.first().id)
                assertEquals("You got mail again!", results.items.first().message)
            }
        }
    }
}
