package dk.sdu.cloud.contact.book.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.micro.ElasticFeature
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.test.initializeMicro
import io.ktor.http.HttpStatusCode
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.junit.Test
import kotlin.test.assertEquals

class ContactBookElasticDAOTest{

    //FULL TEST REQUIRE RUNNING ELASTICSEARCH CLUSTER WITH NO CONTACT BOOK INDEX ALSO DELETES INDEX AFTER
    @Test
    fun `real test`() {
        val micro = initializeMicro()
        micro.install(ElasticFeature)

        val elasticClient = micro.elasticHighLevelClient

        val dao = ContactBookElasticDAO(elasticClient)
        try {
            dao.createIndex()

            Thread.sleep(1000)
            dao.insertContact("User#1", "OtherUser#22", "shareService")
            dao.insertContact("User#2", "OtherUser#44", "shareService")
            dao.insertContact("User#3", "OtherUser#22", "shareService")
            dao.insertContact("User#1", "OtherUser#24", "shareService")


            Thread.sleep(1000)

            assertEquals(2, dao.getAllContactsForUser("User#1", "shareService").totalHits?.value)
            assertEquals(1, dao.getAllContactsForUser("User#2", "shareService").totalHits?.value)
            assertEquals(1, dao.getAllContactsForUser("User#3", "shareService").totalHits?.value)

            dao.insertContactsBulk("User#2", listOf("OtherUser#22", "OtherUser#42"), "shareService")

            Thread.sleep(1000)

            assertEquals(3, dao.getAllContactsForUser("User#2", "shareService").totalHits?.value)

            //Test to see if duplicates are handled
            dao.insertContactsBulk("User#2", listOf("OtherUser#22", "OtherUser#42"), "shareService")
            dao.insertContact("User#1", "OtherUser#22", "shareService")

            Thread.sleep(1000)
            assertEquals(3, dao.getAllContactsForUser("User#2", "shareService").totalHits?.value)
            assertEquals(2, dao.getAllContactsForUser("User#1", "shareService").totalHits?.value)

            assertEquals(3, dao.queryContacts("User#2", "Other", "shareService").totalHits?.value)
            assertEquals(1, dao.queryContacts("User#2", "OtherUser#2", "shareService").totalHits?.value)
            assertEquals(2, dao.queryContacts("User#2", "OtherUser#4", "shareService").totalHits?.value)

            try {
                dao.deleteContact("User#2", "NotThere", "shareService")
            } catch (ex: RPCException) {
                assertEquals(RPCException.fromStatusCode(HttpStatusCode.NotFound).httpStatusCode, ex.httpStatusCode)
            }

            dao.deleteContact("User#2", "OtherUser#44", "shareService")

            Thread.sleep(1000)

            assertEquals(2, dao.getAllContactsForUser("User#2", "shareService").totalHits?.value)

        } catch (ex: Exception) {
            throw ex
        } finally {
            //Clean up
            val deleteRequest = DeleteIndexRequest("contactbook")
            elasticClient.indices().delete(deleteRequest, RequestOptions.DEFAULT)
        }
    }

}
