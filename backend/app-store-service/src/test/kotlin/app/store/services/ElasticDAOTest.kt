package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.app.store.util.normAppDesc
import dk.sdu.cloud.calls.RPCException
import io.mockk.every
import io.mockk.mockk
import org.apache.lucene.search.TotalHits
import org.elasticsearch.action.OriginalIndices
import org.elasticsearch.action.admin.indices.flush.FlushResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.update.UpdateResponse
import org.elasticsearch.client.IndicesClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.common.document.DocumentField
import org.elasticsearch.common.text.Text
import org.elasticsearch.index.Index
import org.elasticsearch.index.shard.ShardId
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.elasticsearch.search.SearchShardTarget
import org.elasticsearch.search.internal.InternalSearchResponse
import org.junit.Test
import kotlin.test.assertEquals

class ElasticDAOTest {

    @Test
    fun testSearch() {
        val elasticHighLevelClient = mockk<RestHighLevelClient>()
        val elasticDAO = ElasticDAO(elasticHighLevelClient)

        every { elasticHighLevelClient.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                SearchHits(
                    arrayOf(
                        SearchHit(1),
                        SearchHit(2)
                    ),
                    null,
                    2.44f
                )
            }
            response
        }

        val results = elasticDAO.search(listOf("String1", "app", "another"), listOf("tag1", "tag2"))
        assertEquals(2, results.hits.hits.size)
    }

    @Test
    fun `Search test with title`() {
        val elasticHighLevelClient = mockk<RestHighLevelClient>()
        val elasticDAO = ElasticDAO(elasticHighLevelClient)

        every { elasticHighLevelClient.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                SearchHits(
                    arrayOf(
                        SearchHit(1),
                        SearchHit(2)
                    ),
                    null,
                    2.44f
                )
            }
            response
        }

        val results = elasticDAO.search(listOf("String1", "app", "another"), listOf("tag1", "tag2"))
        assertEquals(2, results.hits.hits.size)
    }

    @Test (expected = RPCException::class)
    fun `Create test - multiple entries`() {
        val elasticHighLevelClient = mockk<RestHighLevelClient>()
        val elasticDAO = ElasticDAO(elasticHighLevelClient)

        every { elasticHighLevelClient.indices() } answers {
            val client = mockk<IndicesClient>()
            every { client.exists(any<GetIndexRequest>(), any()) } returns true
            client
        }

        every { elasticHighLevelClient.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                SearchHits(
                    arrayOf(
                        SearchHit(1),
                        SearchHit(2)
                    ),
                    null,
                    2.44f
                )
            }
            response
        }
        elasticDAO.createApplicationInElastic(normAppDesc)
    }

    @Test
    fun `Create test`() {
        val elasticHighLevelClient = mockk<RestHighLevelClient>()
        val elasticDAO = ElasticDAO(elasticHighLevelClient)

        every { elasticHighLevelClient.indices() } answers {
            val client = mockk<IndicesClient>()
            every { client.exists(any<GetIndexRequest>(), any()) } returns true
            client
        }

        every { elasticHighLevelClient.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                SearchHits(
                    arrayOf(
                        SearchHit(1)
                    ),
                    null,
                    2.44f
                )
            }
            response
        }
        elasticDAO.createApplicationInElastic(normAppDesc)
    }


    @Test
    fun `Create No index test`() {
        val elasticHighLevelClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticDAO = ElasticDAO(elasticHighLevelClient)

        elasticDAO.createApplicationInElastic(normAppDesc)
    }

    @Test
    fun `Update Description test`() {
        val elasticHighLevelClient = mockk<RestHighLevelClient>()
        val elasticDAO = ElasticDAO(elasticHighLevelClient)

        every { elasticHighLevelClient.indices() } answers {
            val client = mockk<IndicesClient>()
            every { client.exists(any<GetIndexRequest>(), any()) } returns true
            client
        }

        every { elasticHighLevelClient.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                SearchHits(
                    arrayOf(SearchHit(1)),
                    null,
                    2.55f
                )
            }
            response
        }

        every { elasticHighLevelClient.update(any(),any()) } answers {
            val response = mockk<UpdateResponse>()
            response
        }

        every { elasticHighLevelClient.indices().flush(any(),any()) } answers {
            val response = mockk<FlushResponse>()
            response
        }

        every { elasticHighLevelClient.indices().exists(any<GetIndexRequest>(),any())} returns true

        elasticDAO.updateApplicationDescriptionInElastic(
            normAppDesc.metadata.name,
            normAppDesc.metadata.version,
            "This is another test"
        )
    }

    @Test (expected = RPCException::class)
    fun `Update Description test - not found`() {
        val elasticHighLevelClient = mockk<RestHighLevelClient>()
        val elasticDAO = ElasticDAO(elasticHighLevelClient)

        every { elasticHighLevelClient.indices() } answers {
            val client = mockk<IndicesClient>()
            every { client.exists(any<GetIndexRequest>(), any()) } returns true
            client
        }

        every { elasticHighLevelClient.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                SearchHits(
                    emptyArray(),
                    null,
                    2.55f
                )
            }
            response
        }

        elasticDAO.updateApplicationDescriptionInElastic(
            "NotApplication",
            normAppDesc.metadata.version,
            "This is another test"
        )
    }

    @Test (expected = RPCException::class)
    fun `Update Description test - multiple entries`() {
        val elasticHighLevelClient = mockk<RestHighLevelClient>()
        val elasticDAO = ElasticDAO(elasticHighLevelClient)

        every { elasticHighLevelClient.indices() } answers {
            val client = mockk<IndicesClient>()
            every { client.exists(any<GetIndexRequest>(), any()) } returns true
            client
        }

        every { elasticHighLevelClient.search(any(), any()) } answers {
            val response = mockk<SearchResponse>()
            every {response.hits } answers {
                SearchHits(
                    arrayOf(mockk(relaxed = true), mockk(relaxed = true)),
                    null,
                    2.55f
                )
            }
            response
        }

        elasticDAO.updateApplicationDescriptionInElastic(
            normAppDesc.metadata.name,
            normAppDesc.metadata.version,
            "This is another test"
        )
    }
}
