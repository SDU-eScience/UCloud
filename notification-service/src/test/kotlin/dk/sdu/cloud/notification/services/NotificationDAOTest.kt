package dk.sdu.cloud.notification.services

import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.withTransaction
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun withDatabase(closure: (HibernateSessionFactory) -> Unit) {
    HibernateSessionFactory.create(H2_TEST_CONFIG.copy(showSQLInStdout = true)).use(closure)
}

class NotificationDAOTest {

    private val user = "user"
    private val notificationInstance = Notification(
        "type",
        "You got mail!"
    )

    private val notificationInstance2 = Notification(
        "type",
        "You got mail again!"
    )

    @Test
    fun `create , find, mark, delete test`() {
        withDatabase { db ->
            db.withTransaction {
                val dao = NotificationHibernateDAO()

                val findResult1 = dao.findNotifications(it, user)
                assertEquals(0, findResult1.itemsInTotal)

                val createResult = dao.create(it, user, notificationInstance)
                assertEquals(1, createResult)
                val entity = NotificationEntity[it, 1]?.modifiedAt?.time

                val findResult2 = dao.findNotifications(it, user)
                assertEquals(1, findResult2.itemsInTotal)
                assertFalse(findResult2.items.first().read)

                Thread.sleep(2000)

                val createResult2 = dao.create(it, user, notificationInstance2)
                assertEquals(2, createResult2)

                val findResult3 = dao.findNotifications(it, user)
                assertEquals(2, findResult3.itemsInTotal)
                assertTrue(findResult3.items[0].ts > findResult3.items[1].ts)
                assertFalse(findResult3.items[1].read)

                assertTrue(dao.markAsRead(it, user, 1))

                val findResult4 = dao.findNotifications(it, user)
                println(findResult4)
                assertEquals(2, findResult4.itemsInTotal)
                assertTrue(findResult4.items[1].read)

                val entity2 = NotificationEntity[it, 1]?.modifiedAt?.time
                assertTrue(entity2!! > entity!!)

                assertTrue(dao.delete(it, 1))

                val findResult5 = dao.findNotifications(it, user)
                assertEquals(1, findResult5.itemsInTotal)
                assertEquals(2, findResult5.items.first().id)

            }
        }
    }

    @Test
    fun `Delete non existing`() {
        withDatabase { db ->
            db.withTransaction {
                val dao = NotificationHibernateDAO()
                assertFalse(dao.delete(it, 292929))
            }
        }
    }

    @Test
    fun `Mark non existing`() {
        withDatabase { db ->
            db.withTransaction {
                val dao = NotificationHibernateDAO()
                assertFalse(dao.markAsRead(it, user, 292929))
            }
        }
    }

    @Test
    fun `Mark not correct user`() {
        withDatabase { db ->
            db.withTransaction {
                val dao = NotificationHibernateDAO()
                dao.create(it, user, notificationInstance)
                assertFalse(dao.markAsRead(it, "notMe", 1))
            }
        }
    }

    private val notificationInstance3 = Notification(
        "anotherType",
        "You got mail once more!"
    )

    @Test
    fun `Find on type`() {
        withDatabase { db ->
            db.withTransaction {
                val dao = NotificationHibernateDAO()
                dao.create(it, user, notificationInstance)
                dao.create(it, user, notificationInstance3)
                val results = dao.findNotifications(it, user, "anotherType")
                assertEquals(1, results.itemsInTotal)
                assertEquals(2, results.items.first().id)
                assertEquals("You got mail once more!", results.items.first().message)
            }
        }
    }

    @Test
    fun `Find on time`() {
        withDatabase { db ->
            db.withTransaction {
                val dao = NotificationHibernateDAO()
                dao.create(it, user, notificationInstance)
                Thread.sleep(1000)
                val date = Date()
                Thread.sleep(1000)
                dao.create(it, user, notificationInstance2)
                val results = dao.findNotifications(it, user, null, date.time)
                assertEquals(1, results.itemsInTotal)
                assertEquals(2, results.items.first().id)
                assertEquals("You got mail again!", results.items.first().message)
            }
        }
    }
}
