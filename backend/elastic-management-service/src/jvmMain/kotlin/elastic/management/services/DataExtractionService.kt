package elastic.management.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.elastic.management.services.ExpiredEntriesDeleteService
import dk.sdu.cloud.elastic.management.services.indexExists
import dk.sdu.cloud.service.Loggable
import io.ktor.http.*
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchScrollRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import java.io.File

class DataManipulationService(private val elastic: RestHighLevelClient) {
    fun extractToFile(indices: List<String>) {
        indices.forEach { index ->
            if (indexExists(index, elastic)) {
                val file = File("PATH/${index}.json")
                val searchRequest = SearchRequest(index)
                val query = SearchSourceBuilder()
                    .query(
                        QueryBuilders.matchAllQuery()
                    )
                    .size(100)

                searchRequest.source(query)
                searchRequest.scroll(TimeValue.timeValueMinutes(1L))
                val response = elastic.search(searchRequest, RequestOptions.DEFAULT)
                var scrollId = response.scrollId
                var hits = response.hits
                while (hits.hits.isNotEmpty()) {
                    hits.hits.forEach { hit ->
                        file.appendText("${hit.sourceAsString}\n")
                    }

                    val scrollRequest = SearchScrollRequest(scrollId)
                    scrollRequest.scroll(TimeValue.timeValueMinutes(1L))
                    val scrollResponse = elastic.scroll(scrollRequest, RequestOptions.DEFAULT)
                    hits = scrollResponse.hits
                }
            } else {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Index does not exist")
            }
        }
    }

    fun insertIntoES() {
        File("PATH").walk().forEach { file ->
            if (file.isFile) {
                val toIndex = file.absolutePath.substringAfterLast("/").substringBefore("-") +
                    "-" +
                    file.absolutePath.substringAfter("/").substringAfter("-").substring(0,7)
                println(toIndex)
                var bulkRequest = BulkRequest()
                var i = 0
                file.readLines().forEachIndexed { i, line ->
                    if (i.rem(100) == 0 && i != 0) {
                        elastic.bulk(bulkRequest, RequestOptions.DEFAULT)
                        bulkRequest = BulkRequest()
                    }
                    bulkRequest.add(IndexRequest(toIndex).source(line, XContentType.JSON))
                }
                elastic.bulk(bulkRequest, RequestOptions.DEFAULT)
            }
        }
    }


    companion object : Loggable {
        override val log: Logger = ExpiredEntriesDeleteService.logger()
    }
}
