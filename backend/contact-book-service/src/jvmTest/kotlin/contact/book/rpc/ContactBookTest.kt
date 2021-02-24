package dk.sdu.cloud.contact.book.rpc

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.contact.book.api.AllContactsForUserRequest
import dk.sdu.cloud.contact.book.api.AllContactsForUserResponse
import dk.sdu.cloud.contact.book.api.DeleteRequest
import dk.sdu.cloud.contact.book.api.InsertRequest
import dk.sdu.cloud.contact.book.api.QueryContactsRequest
import dk.sdu.cloud.contact.book.api.QueryContactsResponse
import dk.sdu.cloud.contact.book.api.ServiceOrigin
import dk.sdu.cloud.contact.book.services.ContactBookElasticDao
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
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.client.RequestOptions
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class ContactBookTest {

    private fun KtorApplicationTestSetupContext.configureContactServer(
        elasticDao: ContactBookElasticDao
    ): List<Controller> {
        val contactBookService = ContactBookService(elasticDao)
        return listOf(ContactBookController(contactBookService))
    }

    private fun KtorApplicationTestSetupContext.configureContactServerServiceGiven(
        contactBookService: ContactBookService
    ): List<Controller> {
        return listOf(ContactBookController(contactBookService))
    }

    private val fromUser = TestUsers.user.username

    //FULL TEST REQUIRE RUNNING ELASTICSEARCH CLUSTER WITH NO CONTACT BOOK INDEX ALSO DELETES INDEX AFTER
    @Ignore
    @Test
    fun `real test`() {
        withKtorTest(
            setup = {
                micro.install(ElasticFeature)
                val elasticDAO = ContactBookElasticDao(micro.elasticHighLevelClient)
                elasticDAO.createIndex()
                configureContactServer(elasticDAO)
            },
            test = {
                val client = micro.elasticHighLevelClient
                //Insert
                run {
                    val response = sendJson(
                        method = HttpMethod.Put,
                        path = "/api/contactbook",
                        user = TestUsers.service,
                        request = InsertRequest(
                            fromUser,
                            listOf("toUser#12"),
                            ServiceOrigin.SHARE_SERVICE
                        )
                    )
                    response.assertSuccess()
                }
                client.indices().flush(FlushRequest(), RequestOptions.DEFAULT)
                Thread.sleep(1000)
                //Get All
                run {
                    val response = sendJson(
                        method = HttpMethod.Post,
                        path = "/api/contactbook/all",
                        user = TestUsers.user,
                        request = AllContactsForUserRequest(
                            ServiceOrigin.SHARE_SERVICE
                        )
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
                            listOf("toUser#12", "toUser#92", "toUser#44"),
                            ServiceOrigin.SHARE_SERVICE
                        )
                    )
                    response.assertSuccess()
                }
                client.indices().flush(FlushRequest(), RequestOptions.DEFAULT)
                Thread.sleep(1000)
                //Get all
                run {
                    val response = sendJson(
                        method = HttpMethod.Post,
                        path = "/api/contactbook/all",
                        user = TestUsers.user,
                        request = AllContactsForUserRequest(
                            ServiceOrigin.SHARE_SERVICE
                        )
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
                        user = TestUsers.user,
                        request = QueryContactsRequest(
                            "toUser",
                            ServiceOrigin.SHARE_SERVICE
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
                        user = TestUsers.user,
                        request = QueryContactsRequest(
                            "toUser#4",
                            ServiceOrigin.SHARE_SERVICE
                        )
                    )
                    response.assertSuccess()
                    val results = defaultMapper.readValue<QueryContactsResponse>(response.response.content!!)
                    assertEquals(1, results.contacts.size)
                }
                //Delete
                run {
                    sendJson(
                        method = HttpMethod.Delete,
                        path = "api/contactbook",
                        user = TestUsers.service,
                        request = DeleteRequest(
                            fromUser,
                            "toUser#44",
                            ServiceOrigin.SHARE_SERVICE
                        )
                    )
                }
                Thread.sleep(500)

                //Query no hit
                run {
                    val response = sendJson(
                        method = HttpMethod.Post,
                        path = "/api/contactbook",
                        user = TestUsers.user,
                        request = QueryContactsRequest(
                            "toUser#4",
                            ServiceOrigin.SHARE_SERVICE
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

    @Test
    fun `insert`() {
        withKtorTest(
            setup = {
                val contactBookService = mockk<ContactBookService>()
                every { contactBookService.insertContact(any(), any(), any()) } just runs
                configureContactServerServiceGiven(contactBookService)
            },
            test = {
                run {
                    val response = sendJson(
                        method = HttpMethod.Put,
                        path = "/api/contactbook",
                        user = TestUsers.service,
                        request = InsertRequest(
                            fromUser,
                            listOf("toUser#12"),
                            ServiceOrigin.SHARE_SERVICE
                        )
                    )
                    response.assertSuccess()
                }
            }
        )
    }

    @Test
    fun `insert bulk`() {
        withKtorTest(
            setup = {
                val contactBookService = mockk<ContactBookService>()
                every { contactBookService.insertContact(any(), any(), any()) } just runs
                configureContactServerServiceGiven(contactBookService)
            },
            test = {
                run {
                    val response = sendJson(
                        method = HttpMethod.Put,
                        path = "/api/contactbook",
                        user = TestUsers.service,
                        request = InsertRequest(
                            fromUser,
                            listOf("toUser#12", "toUser#14"),
                            ServiceOrigin.SHARE_SERVICE
                        )
                    )
                    response.assertSuccess()
                }
            }
        )
    }

    @Test
    fun `delete test`() {
        withKtorTest(
            setup = {
                val contactBookService = mockk<ContactBookService>()
                every { contactBookService.deleteContact(any(), any(), any()) } just runs
                configureContactServerServiceGiven(contactBookService)
            },
            test = {
                run {
                    val response = sendJson(
                        method = HttpMethod.Delete,
                        path = "api/contactbook",
                        user = TestUsers.service,
                        request = DeleteRequest(
                            fromUser,
                            "toUser#44",
                            ServiceOrigin.SHARE_SERVICE
                        )
                    )
                    response.assertSuccess()
                }
            }
        )
    }

    @Test
    fun `query test`() {
        withKtorTest(
            setup = {
                val contactBookService = mockk<ContactBookService>()
                every { contactBookService.queryUserContacts(any(), any(), any()) } answers {
                    listOf("toUser#4")
                }
                configureContactServerServiceGiven(contactBookService)
            },
            test = {
                run {
                    val response = sendJson(
                        method = HttpMethod.Post,
                        path = "/api/contactbook",
                        user = TestUsers.user,
                        request = QueryContactsRequest(
                            "toUser#4",
                            ServiceOrigin.SHARE_SERVICE
                        )
                    )
                    response.assertSuccess()
                    val results = defaultMapper.readValue<QueryContactsResponse>(response.response.content!!)
                    assertEquals(1, results.contacts.size)
                }
            }
        )
    }

    @Test
    fun `get all test`() {
        withKtorTest(
            setup = {
                val contactBookService = mockk<ContactBookService>()
                every { contactBookService.listAllContactsForUser(any(), any()) } answers {
                    listOf("toUser#4")
                }
                configureContactServerServiceGiven(contactBookService)
            },
            test = {
                run {
                    val response = sendJson(
                        method = HttpMethod.Post,
                        path = "/api/contactbook/all",
                        user = TestUsers.user,
                        request = AllContactsForUserRequest(
                            ServiceOrigin.SHARE_SERVICE
                        )
                    )
                    response.assertSuccess()
                    val results = defaultMapper.readValue<AllContactsForUserResponse>(response.response.content!!)
                    assertEquals(1, results.contacts.size)
                }
            }
        )
    }
}
