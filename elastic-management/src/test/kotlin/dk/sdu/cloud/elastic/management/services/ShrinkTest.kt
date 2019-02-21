package dk.sdu.cloud.elastic.management.services

import org.apache.http.HttpHost
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import org.junit.Test

class ShrinkTest {

    private val elastic = RestHighLevelClient(
        RestClient.builder(
            HttpHost(
                "localhost",
                9200,
                "http"
            )
        )
    )

    fun createDocuments(index: String, numberOfDocuments: Int){
        val bulkRequest = BulkRequest()
        for (i in 0 until numberOfDocuments) {
            val request = IndexRequest("${index}-2019.02.20", "_doc")
            val jsonString = """
                {
                    "user":"kimchy",
                    "postDate": "$i",
                    "message": "This is message $i!"
                }
            """.trimIndent()
            request.source(jsonString, XContentType.JSON)
            bulkRequest.add(request)
        }
        elastic.bulk(bulkRequest, RequestOptions.DEFAULT)
    }

    @Test
    fun `test settings`() {
        val shrinkService = ShrinkService(elastic)
        shrinkService.prepareSourceIndex("hello-2019.02.20")
    }

    @Test
    fun `test shrink`() {
        val shrinkService = ShrinkService(elastic)
        shrinkService.shrinkIndex("hello")
    }

    @Test
    fun `test delete`() {
        val shrinkService = ShrinkService(elastic)
        shrinkService.deleteIndex("hello")
    }

    @Test
    fun `full shrink procedure`() {
        createDocuments("hello", 30000)
        createDocuments("ahello", 30000)
        println("Done Creating")
        val shrinkService = ShrinkService(elastic)
        shrinkService.shrink()
    }

}
