package dk.sdu.cloud.elastic.management.services

import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder

class CustomScripts(val client: RestHighLevelClient) {

    fun run(query: String, index: String, field: String){
        val searchResponses = findEntriesContaining(query, index, field)
        val rerun = searchResponses.hits.totalHits?.value?.toInt() == 10000

    }

    fun findEntriesContaining(query: String, index: String, field: String): SearchResponse {
        val request = SearchRequest(index)
        val query = SearchSourceBuilder().query(
            QueryBuilders.matchQuery(
                field, query
            )
        )
        request.source(query)

        return client.search(request, RequestOptions.DEFAULT)
    }

    fun deleteInBulk(index: String, ids: List<String>) {
        val request = BulkRequest()

        ids.forEach { id ->
            val deleteRequest = DeleteRequest(index, id)
            request.add(deleteRequest)
        }

        client.bulk(request, RequestOptions.DEFAULT)

    }

}
