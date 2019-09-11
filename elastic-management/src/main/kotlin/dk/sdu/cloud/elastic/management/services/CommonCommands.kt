package dk.sdu.cloud.elastic.management.services

import org.apache.http.util.EntityUtils
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeRequest
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest
import org.elasticsearch.client.Request
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.common.settings.Settings

internal fun deleteIndex(index: String, elastic: RestHighLevelClient) {
    elastic.indices().delete(DeleteIndexRequest(index), RequestOptions.DEFAULT)
    ExpiredEntriesDeleteService.log.info("Index: $index deleted")
}

internal fun createIndex(
    newIndexName: String,
    elastic: RestHighLevelClient,
    numberOfShards: Int = 1,
    numberOfReplicas: Int = 1
) {
    val request = CreateIndexRequest()
    request.index(newIndexName)
    request.settings(
        Settings.builder()
            .put("index.number_of_shards", numberOfShards)
            .put("index.number_of_replicas", numberOfReplicas)
    )
    elastic.indices().create(request, RequestOptions.DEFAULT)
}

internal fun indexExists(indexName: String, elastic: RestHighLevelClient): Boolean {
    return elastic.indices().exists(GetIndexRequest(indexName), RequestOptions.DEFAULT)
}

internal fun mergeIndex(elastic: RestHighLevelClient, index: String, maxNumberOfSegments: Int = 1) {
    val request = ForceMergeRequest(index)
    request.maxNumSegments(maxNumberOfSegments)
    elastic.indices().forcemerge(request, RequestOptions.DEFAULT)
}

internal fun getListOfIndices(elastic: RestHighLevelClient, indexRegex: String): List<String> {
    return try {
        elastic.indices().get(GetIndexRequest(indexRegex), RequestOptions.DEFAULT).indices.toList()
    } catch (ex: ElasticsearchStatusException) {
        emptyList()
    }

}

internal fun flushIndex(elastic: RestHighLevelClient, index: String) {
    elastic.indices().flush(FlushRequest(index), RequestOptions.DEFAULT)
}

//returns a list of log names with given prefix up until first occurrence delimiter (default "-").
internal fun getAllLogNamesWithPrefix(
    elastic: RestHighLevelClient,
    prefix: String,
    delimiter: String = "-"
): List<String> {
    val listOfIndices = elastic.indices()
        .get(GetIndexRequest("$prefix*"), RequestOptions.DEFAULT)
        .indices.toList()

    val httpLogNames = mutableSetOf<String>()
    listOfIndices.forEach {
        if (it.indexOf("-") != -1) {
            httpLogNames.add(it.substring(0, it.indexOf(delimiter)))
        }
    }

    return httpLogNames.toList()
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

internal fun getShardCount(index: String, elastic: RestHighLevelClient): Int {
    val request = GetSettingsRequest().indices(index).names("index.number_of_shards")
    return elastic.indices().getSettings(request, RequestOptions.DEFAULT).getSetting(index, "index.number_of_shards")
        .toInt()
}

internal fun getAllEmptyIndices(elastic: RestHighLevelClient, lowClient: RestClient): List<String> {
    val allIndices = elastic.indices().get(GetIndexRequest("*"), RequestOptions.DEFAULT).indices.toList()
    val emptyIndices = mutableListOf<String>()
    try {
        allIndices.forEach {
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
