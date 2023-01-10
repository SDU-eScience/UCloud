package dk.sdu.cloud.elastic.management.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation
import co.elastic.clients.elasticsearch.core.search.HitsMetadata
import dk.sdu.cloud.calls.CallDescription
import org.elasticsearch.client.RestClient


class CustomScripts(val client: ElasticsearchClient, val lowLevelClient: RestClient) {

    fun deleteSpecificLogsFromOverfullIndices(query: String, docLimit: Int, field: String) {
        locateIndicesAboveXDocs(docLimit).forEach { index ->
            do {
                val response = findEntriesContaining("$query*", index, field)
                val ids = response.hits().map { it.id() }
                deleteInBulk(index, ids)
            } while (response.total()?.value()?.toInt() == 10000)
        }

    }

    fun locateIndicesAboveXDocs(numberOfDocs: Int): List<String> {
        val indices = getListOfIndices(client, "*")
        val highCountList = mutableListOf<String>()
        indices.forEach { index ->
            val docCount = getDocumentCountSum(listOf(index), lowLevelClient)
            if (docCount >= numberOfDocs) {
                highCountList.add(index)
            }
        }
        return highCountList
    }

    fun findEntriesContaining(query: String, index: String, field: String): HitsMetadata<CallDescription<*,*,*>> {
        val request = SearchRequest.Builder()
            .index(index)
            .query(
                MatchQuery.Builder()
                    .field(field)
                    .query(query)
                    .build()._toQuery()
            ).size(250)
            .build()

        return client.search(request, CallDescription::class.java).hits()
    }

    fun deleteInBulk(index: String, ids: List<String>) {
        val request = BulkRequest.Builder()
        val operations = mutableListOf<BulkOperation>()
        ids.forEach { id ->
            val deleteRequest =
                BulkOperation.Builder()
                    .delete(
                        DeleteOperation.Builder().index(index).id(id).build()
                    )
                    .build()

            operations.add(deleteRequest)
        }

        request.operations(operations)
        client.bulk(request.build())

    }

}
