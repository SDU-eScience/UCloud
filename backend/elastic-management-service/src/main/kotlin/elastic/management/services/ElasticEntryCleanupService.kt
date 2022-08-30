package elastic.management.services

import dk.sdu.cloud.service.Loggable
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.DeleteByQueryRequest
import org.slf4j.Logger

class ElasticEntryCleanupService(
    private val elastic: RestHighLevelClient
) {
    fun removeEntriesContaining(words: List<String>, containerName: String) {
        val request = DeleteByQueryRequest("kubernetes-production-*")
        val query = QueryBuilders.boolQuery()
        words.forEach { word ->
            query.should(
                QueryBuilders.matchQuery(
                    "log",
                    word
                )
            )
        }
        query.minimumShouldMatch(1)

        request.setQuery(
            QueryBuilders.boolQuery()
                .filter(
                    query
                )
                .filter(
                    QueryBuilders.matchPhraseQuery(
                        "kubernetes.container_name",
                        containerName
                    )
                )
        )

        val response = elastic.deleteByQuery(request, RequestOptions.DEFAULT)
        log.info("Delete done: ${response.deleted} entires deleted")
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
