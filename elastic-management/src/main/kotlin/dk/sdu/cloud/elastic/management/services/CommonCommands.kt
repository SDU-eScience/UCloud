package dk.sdu.cloud.elastic.management.services

import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.get.GetIndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient

internal fun deleteIndex(index: String, elastic: RestHighLevelClient){
    elastic.indices().delete(DeleteIndexRequest(index), RequestOptions.DEFAULT)
    ExpiredEntriesDeleteService.log.info("Index: $index deleted")
}

internal fun createIndex(newIndexName: String, elastic: RestHighLevelClient) {
    val request = CreateIndexRequest()
    request.index(newIndexName)
    elastic.indices().create(request, RequestOptions.DEFAULT)
}

internal fun indexExists(indexName: String, elastic: RestHighLevelClient): Boolean{
    return elastic.indices().exists(GetIndexRequest().indices(indexName), RequestOptions.DEFAULT)
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
