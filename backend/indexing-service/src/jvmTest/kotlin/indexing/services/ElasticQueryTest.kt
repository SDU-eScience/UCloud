package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.indexing.utils.fileQuery
import dk.sdu.cloud.indexing.utils.minimumStatisticRequest
import dk.sdu.cloud.service.NormalizedPaginationRequest
import io.mockk.every
import io.mockk.mockk
import org.apache.lucene.search.TotalHits
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.elasticsearch.search.aggregations.metrics.ValueCount
import org.junit.Test
import kotlin.test.assertEquals

private const val NUMBER_OF_HITS = 20

class ElasticQueryTest {
    private val client = mockk<RestHighLevelClient>()
    private val elastic = ElasticQueryService(client, null, "/mnt/cephfs/")

    private fun mockElasticSearchReponseTwentyHits(client: RestHighLevelClient) {
        every { client.search(any(), RequestOptions.DEFAULT) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                val hits = SearchHits(
                    Array(NUMBER_OF_HITS) { _ -> SearchHit(2) },
                    TotalHits(NUMBER_OF_HITS.toLong(), TotalHits.Relation.EQUAL_TO),
                    1.9f
                )
                hits
            }

            every { response.aggregations.get<ValueCount>("completeCount") } returns object : ValueCount {
                override fun getName(): String = "completeCount"
                override fun getType(): String = ""
                override fun value(): Double = 20.0

                override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder =
                    mockk()

                override fun getMetaData(): MutableMap<String, Any> = mutableMapOf()
                override fun getValue(): Long = 20
                override fun getValueAsString(): String = ""
            }
            response
        }
    }

    @Test
    fun `Simple Query Test`() {
        mockElasticSearchReponseTwentyHits(client)
        val queryResults = elastic.query(fileQuery, NormalizedPaginationRequest(25, 0))
        assertEquals(NUMBER_OF_HITS, queryResults.itemsInTotal)
    }

    @Test
    fun `Simple Query Test - empty string`() {
        mockElasticSearchReponseTwentyHits(client)
        val queryResults = elastic.query(fileQuery.copy(fileNameQuery = listOf("")), NormalizedPaginationRequest(25, 0))
        assertEquals(NUMBER_OF_HITS, queryResults.itemsInTotal)
    }

    @Test
    fun `statistics minimal query test`() {
        mockElasticSearchReponseTwentyHits(client)
        val statisticResults = elastic.statisticsQuery(minimumStatisticRequest)
        assertEquals(NUMBER_OF_HITS, statisticResults.count.toInt())
    }
}
