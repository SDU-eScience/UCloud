package dk.sdu.cloud.contact.book.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.contact.book.api.ServiceOrigin
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

val searchHitAsBytesArray = BytesArray(
    """
                    {
                         "fromUser": "fromUser#1234",
                         "toUser": "toUser#123",
                         "createdAt": ${Date().time},
                         "serviceOrigin": "${ServiceOrigin.SHARE_SERVICE.name}"
                    }
                    """.trimIndent())

class ContactBookTest {

    @Test
    fun `insert Test`() {
        val mockDao = mockk<ContactBookElasticDAO>()
        val service = ContactBookService(mockDao)
        every { mockDao.insertContact(any(), any(), any()) } just Runs
        service.insertContact("fromUser#123", listOf("toUser#1487"), ServiceOrigin.SHARE_SERVICE)
    }

    @Test (expected = RPCException::class)
    fun `insert blank Test`() {
        val mockDao = mockk<ContactBookElasticDAO>()
        val service = ContactBookService(mockDao)
        every { mockDao.insertContact(any(), any(), any()) } just Runs
        service.insertContact("fromUser#123", listOf("    "), ServiceOrigin.SHARE_SERVICE)
    }

    @Test (expected = RPCException::class)
    fun `insert empty Test`() {
        val mockDao = mockk<ContactBookElasticDAO>()
        val service = ContactBookService(mockDao)
        every { mockDao.insertContact(any(), any(), any()) } just Runs
        service.insertContact("fromUser#123", emptyList(), ServiceOrigin.SHARE_SERVICE)
    }

    @Test
    fun `insert Bulk Test`() {
        val mockDao = mockk<ContactBookElasticDAO>()
        val service = ContactBookService(mockDao)
        every { mockDao.insertContactsBulk(any(), any(), any()) } just Runs
        service.insertContact("fromUser#123", listOf("toUser#1487", "toUser#4810"), ServiceOrigin.SHARE_SERVICE)
    }

    @Test
    fun `delete test`() {
        val mockDao = mockk<ContactBookElasticDAO>()
        val service = ContactBookService(mockDao)
        every { mockDao.deleteContact(any(), any(), any()) } just Runs
        service.deleteContact("fromUser#123", "toUser#4810", ServiceOrigin.SHARE_SERVICE)
    }

    @Test
    fun `query test`() {
        val mockDao = mockk<ContactBookElasticDAO>()
        val service = ContactBookService(mockDao)
        every { mockDao.queryContacts(any() ,any(), any()) } answers {
            val hits = mockk<SearchHits>()
            val hit = SearchHit(1)
            hit.sourceRef(searchHitAsBytesArray)
            every { hits.hits } answers {
                arrayOf(hit)
            }
            hits
        }
        val results = service.queryUserContacts("fromUser#1234", "toUser#123", ServiceOrigin.SHARE_SERVICE)
        assertEquals("toUser#123", results.first())
    }

    @Test
    fun `get All test`() {
        val mockDao = mockk<ContactBookElasticDAO>()
        val service = ContactBookService(mockDao)
        every { mockDao.getAllContactsForUser(any() ,any()) } answers {
            val hits = mockk<SearchHits>()
            val hit = SearchHit(1)
            hit.sourceRef(searchHitAsBytesArray)
            every { hits.hits } answers {
                arrayOf(hit)
            }
            hits
        }
        val results = service.listAllContactsForUser("fromUser#1234", ServiceOrigin.SHARE_SERVICE)
        assertEquals("toUser#123", results.first())
    }

}
