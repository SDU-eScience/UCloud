package elastic.management.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.query_dsl.MatchPhraseQuery
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest
import dk.sdu.cloud.service.Loggable
import org.elasticsearch.client.RequestOptions
import org.slf4j.Logger

class ElasticEntryCleanupService(
    private val elastic: ElasticsearchClient
) {
    fun removeEntriesContaining(words: List<String>, containerName: String) {
        val request = DeleteByQueryRequest.Builder()
        val intermediateQuery = QueryBuilders.bool()

        words.forEach { word ->
            intermediateQuery.should(
                MatchQuery.Builder()
                    .field("log")
                    .query(word)
                    .build()._toQuery()
            )
        }
        intermediateQuery.minimumShouldMatch("1")

        val query = intermediateQuery.build()._toQuery()
        request.query(
            QueryBuilders.bool()
                .filter(
                    query
                )
                .filter(
                    MatchPhraseQuery.Builder()
                        .field("kubernetes.container_name")
                        .query(containerName)
                        .build()
                        ._toQuery()
                )
                .build()
                ._toQuery()
        )
        request.index("kubernetes-production-*")

        val response = elastic.deleteByQuery(request.build())
        log.info("Delete done: ${response.deleted()} entires deleted")
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
