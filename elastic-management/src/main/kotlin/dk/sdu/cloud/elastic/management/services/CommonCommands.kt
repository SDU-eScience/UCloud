package dk.sdu.cloud.elastic.management.services

import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeRequest
import org.elasticsearch.action.admin.indices.get.GetIndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.settings.Settings

internal fun deleteIndex(index: String, elastic: RestHighLevelClient){
    elastic.indices().delete(DeleteIndexRequest(index), RequestOptions.DEFAULT)
    ExpiredEntriesDeleteService.log.info("Index: $index deleted")
}

internal fun createIndex(newIndexName: String, elastic: RestHighLevelClient, numberOfShards: Int = 1, numberOfReplicas: Int = 1) {
    val request = CreateIndexRequest()
    request.index(newIndexName)
    request.settings(Settings.builder()
        .put("index.number_of_shards", numberOfShards)
        .put("index.number_of_replicas", numberOfReplicas)
    )
    elastic.indices().create(request, RequestOptions.DEFAULT)
}

internal fun indexExists(indexName: String, elastic: RestHighLevelClient): Boolean{
    return elastic.indices().exists(GetIndexRequest().indices(indexName), RequestOptions.DEFAULT)
}

internal fun mergeIndex(elastic: RestHighLevelClient, index: String, maxNumberOfSegments: Int = 1) {
    val request = ForceMergeRequest(index)
    request.maxNumSegments(maxNumberOfSegments)
    elastic.indices().forcemerge(request, RequestOptions.DEFAULT)
}

internal fun getListOfIndices(elastic: RestHighLevelClient, indexRegex: String): List<String> {
    return elastic.indices().get(GetIndexRequest().indices(indexRegex), RequestOptions.DEFAULT).indices.toList()
}

internal fun flushIndex(elastic: RestHighLevelClient, index: String) {
    elastic.indices().flush(FlushRequest(index), RequestOptions.DEFAULT)
}

//returns a list of log names with given prefix up until first occurrence delimiter (default "-").
internal fun getAllLogNamesWithPrefix(elastic: RestHighLevelClient, prefix: String, delimiter: String = "-"): List<String>{
    val listOfIndices = elastic.indices()
        .get(GetIndexRequest().indices("$prefix*"), RequestOptions.DEFAULT)
        .indices.toList()

    val httpLogNames = mutableSetOf<String>()
    listOfIndices.forEach {
        if (it.indexOf("-") != 0) {
            httpLogNames.add(it.substring(0, it.indexOf(delimiter)))
        }
    }

    return httpLogNames.toList()
}
