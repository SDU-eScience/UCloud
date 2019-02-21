package dk.sdu.cloud.elastic.management.services

import org.apache.http.HttpHost
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import org.junit.Test
import java.util.*

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

    private fun createDocuments(index: String, numberOfDocuments: Int){
        val date = Date().time
        val bulkRequest = BulkRequest()
        for (i in 0 until numberOfDocuments) {
            val request = IndexRequest("${index}-2019.02.20", "_doc")
            val jsonString = """
                {
                    "user":"kimchy",
                    "postDate": "$i",
                    "message": "This is message $i!",
                    "expiry": $date
                }
            """.trimIndent()
            request.source(jsonString, XContentType.JSON)
            bulkRequest.add(request)
        }
        elastic.bulk(bulkRequest, RequestOptions.DEFAULT)
    }


    @Test
    fun `full shrink procedure`() {
        //createDocuments("hello", 30000)
        val shrinkService = DeleteService(elastic)
        shrinkService.findExpired("hello")
        //shrinkService.cleanUp()
    }

}
