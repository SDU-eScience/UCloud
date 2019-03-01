package dk.sdu.cloud.elastic.management.services

import dk.sdu.cloud.service.Loggable
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.action.admin.indices.get.GetIndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.DeleteByQueryRequest
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import java.util.*

class ExpiredEntriesDeleteService(
    private val elastic: RestHighLevelClient
){

    private fun deleteExpired(index: String) {
        val date = Date().time

        val expiredCount = elastic.count(CountRequest().source(
                SearchSourceBuilder().query(
                    QueryBuilders.rangeQuery("expiry")
                        .lte(date)
                )
            )
            .indices(index),
            RequestOptions.DEFAULT
        ).count

        val sizeOfIndex = elastic.count(CountRequest().source(
                SearchSourceBuilder().query(
                    QueryBuilders.matchAllQuery()
                )
            ).indices(index),
            RequestOptions.DEFAULT
        ).count

        if (expiredCount == 0L) {
            log.info("Nothing expired in index - moving on")
            return
        }

        if (sizeOfIndex == expiredCount) {
            log.info("All doc expired - faster to delete index")
            deleteFullIndex(index)
        } else {
            val request = DeleteByQueryRequest(index)
            request.setQuery(
                QueryBuilders.rangeQuery("expiry")
                    .lte(date)
            )

            elastic.deleteByQuery(request, RequestOptions.DEFAULT)
            elastic.indices().flush(FlushRequest(index), RequestOptions.DEFAULT)
        }
    }

    private fun deleteFullIndex(index: String){
        elastic.indices().delete(DeleteIndexRequest(index), RequestOptions.DEFAULT)
        log.info("Index: $index deleted")
    }

    fun cleanUp() {
        val list = elastic.indices().get(GetIndexRequest().indices("*"), RequestOptions.DEFAULT).indices
        list.forEach {
            log.info("Finding expired entries in $it")
            deleteExpired(it)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()

        internal const val DOC_TYPE = "doc"
    }
}
