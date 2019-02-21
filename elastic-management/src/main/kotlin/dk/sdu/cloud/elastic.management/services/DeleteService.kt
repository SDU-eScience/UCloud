package dk.sdu.cloud.elastic.management.services

import dk.sdu.cloud.service.Loggable
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.get.GetIndexRequest
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import java.util.*

class DeleteService(
    private val elastic: RestHighLevelClient
){

    fun findExpired(index: String) {
        val date = Date().time
        val request = SearchRequest(index)
        val query = SearchSourceBuilder().query(
                QueryBuilders.rangeQuery("expiry")
                    .lte(date)
            )
        request.source(query)

        val results = elastic.search(request, RequestOptions.DEFAULT)
        val expiredDocumentsIdsList = results.hits.map { it.id }.toList()

        if (expiredDocumentsIdsList.isEmpty()) {
            log.info("No documents to be deleted from index: $index")
            return
        }

        deleteDocuments(index, expiredDocumentsIdsList)
    }

    private fun deleteFullIndex(index: String){
        log.info("Deleting entire index: $index")
        elastic.indices().delete(DeleteIndexRequest(index), RequestOptions.DEFAULT)
    }

    private fun getSizeOfIndex(index: String): Long {
        val request = SearchRequest(index)
        request.source(
            SearchSourceBuilder().query(
                QueryBuilders.matchAllQuery()
            )
        )
        return elastic.search(request, RequestOptions.DEFAULT).hits.totalHits
    }

    private fun deleteDocuments(index: String, listOfIds: List<String>) {
        if (getSizeOfIndex(index) == listOfIds.size.toLong()) {
            deleteFullIndex(index)
        } else {
            log.info("Deleting ${listOfIds.size} documents from index: $index")
            val bulkRequest = BulkRequest()
            listOfIds.forEach {
                bulkRequest.add(DeleteRequest(index, DOC_TYPE, it))
            }
            elastic.bulk(bulkRequest, RequestOptions.DEFAULT)
        }
    }

    fun cleanUp() {
        val list = elastic.indices().get(GetIndexRequest().indices("*"), RequestOptions.DEFAULT).indices
        list.forEach {
            findExpired(it)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()

        internal const val DOC_TYPE = "doc"
    }
}
