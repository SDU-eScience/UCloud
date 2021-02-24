package dk.sdu.cloud.contact.book.services

import dk.sdu.cloud.service.Time
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class ElasticIndexedContactTest {

    @Test
    fun `simple class test `() {
        val time = Time.now()
        val contact = ElasticIndexedContact(
            "user#1234",
            "toUser#1231",
            time,
            "shareService"
        )

        assertEquals(contact.fromUser, "user#1234")
        assertEquals(contact.toUser, "toUser#1231")
        assertEquals(contact.createdAt, time)
        assertEquals(contact.serviceOrigin, "shareService")
    }

}
