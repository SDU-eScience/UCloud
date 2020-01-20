package dk.sdu.cloud.contact.book.rpc

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.contact.book.api.AllContactsForUserResponse
import dk.sdu.cloud.contact.book.api.DeleteRequest
import dk.sdu.cloud.contact.book.api.InsertRequest
import dk.sdu.cloud.contact.book.api.QueryContactsRequest
import dk.sdu.cloud.contact.book.api.QueryContactsResponse
import dk.sdu.cloud.contact.book.services.ContactBookElasticDAO
import dk.sdu.cloud.contact.book.services.ContactBookService
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.ElasticFeature
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.client.RequestOptions
import org.junit.Test
import kotlin.test.assertEquals

class ContactBookTest {

    private fun KtorApplicationTestSetupContext.configureContactServer(
        elasticDAO: ContactBookElasticDAO
    ): List<Controller> {
        val contactBookService = ContactBookService(elasticDAO)
        return listOf(ContactBookController(contactBookService))
    }

    //FULL TEST REQUIRE RUNNING ELASTICSEARCH CLUSTER WITH NO CONTACT BOOK INDEX ALSO DELETES INDEX AFTER
    @Test
    fun `real test`() {
        withKtorTest(
            setup = {
                micro.install(ElasticFeature)
                val elasticDAO = ContactBookElasticDAO(micro.elasticHighLevelClient)
                elasticDAO.createIndex()
                configureContactServer(elasticDAO)
            },
            test = {
                val client = micro.elasticHighLevelClient

                //Insert
                val fromUser = "UserName#41"
                run {
                    val response = sendJson(
                        method = HttpMethod.Put,
                        path = "/api/contactbook",
                        user = TestUsers.service,
                        request = InsertRequest(
                            fromUser,
                            listOf("toUser#12")
                        )
                    )
                    response.assertSuccess()
                }
                client.indices().flush(FlushRequest(), RequestOptions.DEFAULT)
                Thread.sleep(1000)
                //Get All
                run {
                    val response = sendRequest(
                        method = HttpMethod.Get,
                        path = "/api/contactbook/all/UserName#41",
                        user = TestUsers.service
                    )
                    response.assertSuccess()
                    val results = defaultMapper.readValue<AllContactsForUserResponse>(response.response.content!!)
                    assertEquals(1, results.contacts.size)
                }
                //Insert Bulk (single duplicate)
                run {
                    val response = sendJson(
                        method = HttpMethod.Put,
                        path = "/api/contactbook",
                        user = TestUsers.service,
                        request = InsertRequest(
                            fromUser,
                            listOf("toUser#12", "toUser#92", "toUser#44")
                        )
                    )
                    response.assertSuccess()
                }
                client.indices().flush(FlushRequest(), RequestOptions.DEFAULT)
                Thread.sleep(1000)
                //Get all
                run {
                    val response = sendRequest(
                        method = HttpMethod.Get,
                        path = "/api/contactbook/all/$fromUser",
                        user = TestUsers.service
                    )
                    response.assertSuccess()
                    val results = defaultMapper.readValue<AllContactsForUserResponse>(response.response.content!!)
                    assertEquals(3, results.contacts.size)
                }
                //Query multi hit
                run {
                    val response = sendJson(
                        method = HttpMethod.Post,
                        path = "/api/contactbook",
                        user = TestUsers.service,
                        request = QueryContactsRequest(
                            fromUser,
                            "toUser"
                        )
                    )
                    response.assertSuccess()
                    val results = defaultMapper.readValue<QueryContactsResponse>(response.response.content!!)
                    assertEquals(3, results.contacts.size)
                }
                //Query single hit
                run {
                    val response = sendJson(
                        method = HttpMethod.Post,
                        path = "/api/contactbook",
                        user = TestUsers.service,
                        request = QueryContactsRequest(
                            fromUser,
                            "toUser#4"
                        )
                    )
                    response.assertSuccess()
                    val results = defaultMapper.readValue<QueryContactsResponse>(response.response.content!!)
                    assertEquals(1, results.contacts.size)
                }
                //Delete
                run {
                    val response = sendJson(
                        method = HttpMethod.Delete,
                        path = "api/contactbook",
                        user = TestUsers.service,
                        request = DeleteRequest(
                            fromUser,
                            "toUser#44"
                        )
                    )
                }
                Thread.sleep(500)

                //Query no hit
                run {
                    val response = sendJson(
                        method = HttpMethod.Post,
                        path = "/api/contactbook",
                        user = TestUsers.service,
                        request = QueryContactsRequest(
                            fromUser,
                            "toUser#4"
                        )
                    )
                    response.assertSuccess()
                    val results = defaultMapper.readValue<QueryContactsResponse>(response.response.content!!)
                    assertEquals(0, results.contacts.size)
                }


                client.indices().delete(DeleteIndexRequest("contactbook"), RequestOptions.DEFAULT)
            }
        )
    }

}
