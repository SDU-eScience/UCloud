package dk.sdu.cloud.contact.book.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.micro.ElasticFeature
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.test.initializeMicro
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.runs
import org.apache.lucene.search.TotalHits
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.action.admin.indices.flush.FlushResponse
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.MultiSearchRequest
import org.elasticsearch.action.search.MultiSearchResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.CreateIndexResponse
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class ContactBookElasticDAOTest{

    //FULL TEST REQUIRE RUNNING ELASTICSEARCH CLUSTER WITH NO CONTACT BOOK INDEX ALSO DELETES INDEX AFTER
    @Ignore
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

    @Test
    fun `insert single - already exists - test`() {
        val client = mockk<RestHighLevelClient>()
        val elasticDao = ContactBookElasticDAO(client)
        every { client.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                val hits = mockk<SearchHits>()
                every { hits.totalHits } returns TotalHits(1, mockk())
                every { hits.hits } answers {
                    val hit = SearchHit(1)
                    hit.sourceRef(searchHitAsBytesArray)
                    arrayOf(hit)
                }
                hits
            }
            response
        }
        every { client.indices().exists(any<GetIndexRequest>(), any()) } answers {
            true
        }
        elasticDao.insertContact("fromUser", "toUser", "shareService")
    }

    @Test
    fun `insert single test`() {
        val client = mockk<RestHighLevelClient>()
        val elasticDao = ContactBookElasticDAO(client)
        every { client.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                val hits = mockk<SearchHits>()
                every { hits.totalHits } returns TotalHits(0, mockk())
                every { hits.hits } answers {
                    emptyArray()
                }
                hits
            }
            response
        }

        every { client.index(any(), any()) } answers {
            val response = mockk<IndexResponse>()
            response
        }

        every { client.indices().exists(any<GetIndexRequest>(), any()) } answers {
            true
        }

        elasticDao.insertContact("fromUser", "toUser", "shareService")
    }

    @Test (expected = RPCException::class)
    fun `insert - duplicate error - test`() {
        val client = mockk<RestHighLevelClient>()
        val elasticDao = ContactBookElasticDAO(client)
        every { client.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                val hits = mockk<SearchHits>()
                every { hits.totalHits } returns TotalHits(2, mockk())
                every { hits.hits } answers {
                    emptyArray()
                }
                hits
            }
            response
        }

        every { client.index(any(), any()) } answers {
            val response = mockk<IndexResponse>()
            response
        }

        every { client.indices().exists(any<GetIndexRequest>(), any()) } answers {
            true
        }
        elasticDao.insertContact("fromUser", "toUser", "shareService")
    }

    private fun mockedSearchResponse():SearchResponse {
        val response = mockk<SearchResponse>()
        every { response.hits } answers {
            val hits = mockk<SearchHits>()
            every { hits.totalHits } returns TotalHits(0, mockk())
            every { hits.hits } answers {
                emptyArray()
            }
            hits
        }
        return response
    }

    @Test
    fun `insert bulk test`() {
        val client = mockk<RestHighLevelClient>()
        val elasticDao = ContactBookElasticDAO(client)
        every { client.msearch(any(), any()) } answers {
            val response = mockk<MultiSearchResponse>()
            every { response.responses } answers {
                arrayOf(
                    MultiSearchResponse.Item(mockedSearchResponse(), null),
                    MultiSearchResponse.Item(mockedSearchResponse(), null)
                )
            }
            response
        }

        every { client.bulk(any(), any()) } answers {
            val response = mockk<BulkResponse>()
            response
        }

        elasticDao.insertContactsBulk("fromUser", listOf("toUser", "toUser2"), "shareService")
    }

    @Test
    fun `create index dummy test`() {
        val client = mockk<RestHighLevelClient>()
        val elasticDAO = ContactBookElasticDAO(client)
        every { client.indices().create(any<CreateIndexRequest>(), any()) } answers {
            val response = mockk<CreateIndexResponse>()
            response
        }
        every { client.indices().flush(any(), any()) } answers {
            val response = mockk<FlushResponse>()
            response
        }
        every { client.exists(any(), any()) } answers {
            false
        }

        every { client.indices().exists(any<GetIndexRequest>(), any()) } answers {
            false
        }

        elasticDAO.createIndex()
    }

    @Test
    fun `delete test`() {
        val client = mockk<RestHighLevelClient>()
        val elasticDao = ContactBookElasticDAO(client)
        every { client.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                val hits = mockk<SearchHits>()
                every { hits.totalHits } returns TotalHits(1, mockk())
                every { hits.hits } answers {
                    val hit = SearchHit(1)
                    hit.sourceRef(searchHitAsBytesArray)
                    arrayOf(hit)
                }
                hits
            }
            response
        }

        every { client.delete(any(), any()) } answers {
            val response = mockk<DeleteResponse>()
            response
        }

        elasticDao.deleteContact("fromUser", "toUser", "shareService")
    }

    @Test (expected = RPCException::class)
    fun `delete - not found - test`() {
        val client = mockk<RestHighLevelClient>()
        val elasticDao = ContactBookElasticDAO(client)
        every { client.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                val hits = mockk<SearchHits>()
                every { hits.totalHits } returns TotalHits(0, mockk())
                every { hits.hits } answers {
                    emptyArray()
                }
                hits
            }
            response
        }

        elasticDao.deleteContact("fromUser", "toUser", "shareService")
    }

    @Test
    fun `query test`() {
        val client = mockk<RestHighLevelClient>()
        val elasticDao = ContactBookElasticDAO(client)
        every { client.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                val hits = mockk<SearchHits>()
                every { hits.totalHits } returns TotalHits(1, mockk())
                every { hits.hits } answers {
                    val hit = SearchHit(1)
                    hit.sourceRef(searchHitAsBytesArray)
                    arrayOf(hit)
                }
                hits
            }
            response
        }

        elasticDao.queryContacts("fromUser", "toUser", "shareService")
    }


    @Test
    fun `get all test`() {
        val client = mockk<RestHighLevelClient>()
        val elasticDao = ContactBookElasticDAO(client)
        every { client.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                val hits = mockk<SearchHits>()
                every { hits.totalHits } returns TotalHits(1, mockk())
                every { hits.hits } answers {
                    val hit = SearchHit(1)
                    hit.sourceRef(searchHitAsBytesArray)
                    arrayOf(hit)
                }
                hits
            }
            response
        }

        elasticDao.getAllContactsForUser("fromUser", "shareService")
    }
}
