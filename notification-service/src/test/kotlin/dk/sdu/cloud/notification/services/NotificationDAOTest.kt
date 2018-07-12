package dk.sdu.cloud.notification.services

import dk.sdu.cloud.notification.api.Notification
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationDAOTest{

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
    fun `Create, find, mark, delete notifications test`() {
        val notification = InMemoryNotificationDAO()
        notification.create(Any(), user, notificationInstance)
        val id = notification.create(Any(), user, notificationInstance2)

        val result = notification.findNotifications(Any(), user)

        assertEquals(2, result.items.size)
        assertEquals("You got mail again!", result.items[0].message )
        assertEquals("You got mail!", result.items[1].message )

        assertFalse(result.items[0].read)

        assertTrue(notification.markAsRead(Any(), user, id))
        assertFalse(notification.markAsRead(Any(), user, 22))

        //Is put in the back of the list when marked as read
        assertFalse(result.items[1].read)

        assertTrue(notification.delete(Any(), id))
        assertFalse(notification.delete(Any(), id))

        val result2 = notification.findNotifications(Any(), user)
        assertEquals(1, result2.items.size)
        assertEquals("You got mail!", result2.items[0].message )



    }
}