package dk.sdu.cloud.elastic.management.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
import co.elastic.clients.elasticsearch._types.query_dsl.QueryVariant
import co.elastic.clients.elasticsearch.core.CountRequest
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest
import co.elastic.clients.elasticsearch.indices.ExistsRequest
import co.elastic.clients.elasticsearch.indices.FlushRequest
import co.elastic.clients.elasticsearch.indices.ForcemergeRequest
import co.elastic.clients.elasticsearch.indices.GetIndexRequest
import co.elastic.clients.elasticsearch.indices.GetIndicesSettingsRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import org.apache.http.util.EntityUtils
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.client.Request
import org.elasticsearch.client.RestClient

internal fun deleteIndex(index: String, elastic: ElasticsearchClient) {
    elastic.indices().delete(
        DeleteIndexRequest.Builder()
            .index(index)
            .build()
    )
    ExpiredEntriesDeleteService.log.info("Index: $index deleted")
}

internal fun createIndex(
    newIndexName: String,
    elastic: ElasticsearchClient,
    numberOfShards: Int = 1,
    numberOfReplicas: Int = 1
) {
    elastic.indices().create(
        CreateIndexRequest.Builder()
            .index(newIndexName)
            .settings { setting ->
                setting.numberOfShards(numberOfShards.toString())
                setting.numberOfReplicas(numberOfReplicas.toString())
            }
            .build()
    )
}

internal fun indexExists(indexName: String, elastic: ElasticsearchClient): Boolean {
    val request = ExistsRequest.Builder().index(indexName).build()
    return elastic.indices().exists(request).value()
}

internal fun mergeIndex(elastic: ElasticsearchClient, index: String, maxNumberOfSegments: Long = 1) {
    elastic.indices().forcemerge(
        ForcemergeRequest.Builder()
            .index(index)
            .maxNumSegments(maxNumberOfSegments)
            .build()
    )
}

internal fun getListOfIndices(elastic: ElasticsearchClient, indexRegex: String): List<String> {
    return try {
        elastic.indices().get(
            GetIndexRequest.Builder()
                .index(indexRegex)
                .build()
        ).result().keys.toList()
    } catch (ex: ElasticsearchStatusException) {
        emptyList()
    }
}

internal fun flushIndex(elastic: ElasticsearchClient, index: String) {
    elastic.indices().flush(
        FlushRequest.Builder()
            .index(index)
            .build()
    )
}

//returns a list of log names with given prefix up until first occurrence delimiter (default "-").
internal fun getAllLogNamesWithPrefix(
    elastic: ElasticsearchClient,
    prefix: String,
    delimiter: String = "-"
): List<String> {
    return elastic.indices().get(
        GetIndexRequest.Builder()
            .index("$prefix*")
            .build()
    ).result().keys.toList()
        .map { index ->
            if (!index.contains(delimiter)) {
                throw RPCException.fromStatusCode(
                    HttpStatusCode.InternalServerError, "Weird formatting missing: '$delimiter'")
            }
            index.substring(0, index.indexOf(delimiter))
        }.distinct()
}

internal fun getAllLogNamesWithPrefixForDate(
    elastic: ElasticsearchClient,
    prefix: String,
    dateInStringFormat: String,
): List<String> {
    return elastic.indices().get(
        GetIndexRequest.Builder()
            .index("$prefix*${dateInStringFormat}*")
            .build()
    ).result().keys.toList()

}

internal fun getDocumentCountSum(indices: List<String>, lowClient: RestClient): Int {
    var count = 0
    try {
        indices.forEach {
            val response = lowClient.performRequest(Request("GET", "/_cat/count/$it"))
            val responseInfo = EntityUtils.toString(response.entity).split(" ").filter { it != "" }
            count += responseInfo[2].trim().toInt()
        }
    } catch (ex: Exception) {
        lowClient.close()
        throw ex
    }
    return count
}

internal fun getShardCount(index: String, elastic: ElasticsearchClient): Int {
    return elastic.indices().getSettings(
        GetIndicesSettingsRequest.Builder()
            .index(index)
            .build()
    )[index]?.settings()?.numberOfShards()?.toInt() ?: throw IllegalStateException("No setting found for number of shards for index $index")
}

internal fun getAllEmptyIndicesWithRegex(elastic: ElasticsearchClient, lowClient: RestClient, regex: String): List<String> {
    val allLogIndices = elastic.indices().get(
        GetIndexRequest.Builder()
            .index(regex)
            .build()
    ).result().keys.toList()

    val emptyIndices = mutableListOf<String>()
    try {
        allLogIndices.forEach {
            val response = lowClient.performRequest(Request("GET", "/_cat/count/$it"))
            val responseInfo = EntityUtils.toString(response.entity).split(" ").filter { it != "" }
            val size = responseInfo[2].trim().toInt()
            if (size == 0) {
                emptyIndices.add(it)
            }
        }
    } catch (ex: Exception) {
        lowClient.close()
        throw ex
    }
    return emptyIndices
}

internal fun isSameSize(index: String, otherIndex: String, elastic: ElasticsearchClient): Boolean {
    val sourceCount = elastic.count(
        CountRequest.Builder()
            .index(otherIndex)
            .query(
                Query.Builder()
                    .apply {
                        MatchAllQuery.Builder().build()
                    }.build()
            )
            .build()
    ).count()

    val targetCount =  elastic.count(
        CountRequest.Builder()
            .index(index)
            .query(
                Query.Builder()
                    .apply {
                        MatchAllQuery.Builder().build()
                    }.build()
            )
            .build()
    ).count()
    return sourceCount == targetCount
}
