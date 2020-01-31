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

    fun deleteSpecificLogsFromOverfullIndices(query: String, docLimit: Int, field: String) {
        locateIndicesAboveXDocs(docLimit).forEach { index ->
            println("Deleting from $index")
            do {
                val response = findEntriesContaining("$query*", index, field)
                println("hits = ${response.hits.totalHits?.value}")
                val ids = response.hits.map { it.id }
                deleteInBulk(index, ids)
            } while (response.hits.totalHits?.value?.toInt() == 10000)
        }

    }

    fun locateIndicesAboveXDocs(numberOfDocs: Int): List<String> {
        val indices = getListOfIndices(client, "*")
        val highCountList = mutableListOf<String>()
        indices.forEach { index ->
            val docCount = getDocumentCountSum(listOf(index), client.lowLevelClient)
            if (docCount >= numberOfDocs) {
                highCountList.add(index)
            }
        }
        highCountList.forEach { println(it) }
        return highCountList
    }

    fun findEntriesContaining(query: String, index: String, field: String): SearchResponse {
        val request = SearchRequest(index)
        val query = SearchSourceBuilder().query(
            QueryBuilders.matchQuery(
                field, query
            )
        ).size(250)
        request.source(query)

        return client.search(request, RequestOptions.DEFAULT)
    }

    fun deleteInBulk(index: String, ids: List<String>) {
        val request = BulkRequest(index)
        println("size of ids: ${ids.size}")

        ids.forEach { id ->
            val deleteRequest = DeleteRequest(index, id)
            request.add(deleteRequest)
        }

        client.bulk(request, RequestOptions.DEFAULT)

    }

}
