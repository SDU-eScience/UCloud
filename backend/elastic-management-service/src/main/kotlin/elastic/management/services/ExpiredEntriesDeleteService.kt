package dk.sdu.cloud.elastic.management.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch.core.CountRequest
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest
import co.elastic.clients.json.JsonData
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import org.elasticsearch.client.RestClient
import org.slf4j.Logger
import java.time.LocalDate
import java.util.*

class ExpiredEntriesDeleteService(
    private val elastic: ElasticsearchClient
) {

    private fun deleteExpired(index: String) {
        val date = Time.now()

        val expiredCount = elastic.count(
            CountRequest.Builder()
                .index(index)
                .query(
                    RangeQuery.Builder()
                        .field("expiry")
                        .lte(JsonData.of(date))
                        .build()
                        ._toQuery()
                )
                .build()
        ).count()

        val sizeOfIndex = elastic.count(
            CountRequest.Builder()
                .index(index)
                .query(
                    MatchAllQuery.Builder().build()._toQuery()
                )
                .build()
        ).count()

        if (expiredCount == 0L) {
            log.info("Nothing expired in index - moving on")
            return
        }

        if (sizeOfIndex == expiredCount) {
            log.info("All doc expired - faster to delete index")
            deleteIndex(index, elastic)
        } else {
            val request = DeleteByQueryRequest.Builder()
                .index(index)
                .query(
                    RangeQuery.Builder()
                        .field("expiry")
                        .lte(JsonData.of(date))
                        .build()._toQuery()
                )
                .build()

            elastic.deleteByQuery(request)
            flushIndex(elastic, index)
        }
    }

    fun deleteOldRancherLogs() {
        val currentDate = LocalDate.now()
        val daysToSave = 180

        val indexToDelete = if (indexExists("development_default-*", elastic))
            "development_default-${currentDate.minusDays(daysToSave.toLong())}*"
        else
            "kubernetes-production-${currentDate.minusDays(daysToSave.toLong())}*"

        if (!indexExists(indexToDelete, elastic)) {
            log.info("no index with the name $indexToDelete")
            return
        }
        deleteIndex(indexToDelete, elastic)
    }

    fun deleteOldFileBeatLogs() {
        val datePeriodFormat = LocalDate.now().minusDays(180).toString().replace("-","." )

        val indexToDelete = "filebeat-${datePeriodFormat}*"

        if (!indexExists(indexToDelete, elastic)) {
            log.info("no index with the name $indexToDelete")
            return
        }
        deleteIndex(indexToDelete, elastic)
    }

    fun deleteOldInfrastructureLogs() {
        val datePeriodFormat = LocalDate.now().minusDays(180).toString().replace("-","." )

        val indexToDelete = "infrastructure-${datePeriodFormat}*"

        if (!indexExists(indexToDelete, elastic)) {
            log.info("no index with the name $indexToDelete")
            return
        }
        deleteIndex(indexToDelete, elastic)
    }

    fun deleteExpiredAllIndices() {
        val list = getListOfIndices(elastic, "*")
        list.forEach {
            log.info("Finding expired entries in $it")
            deleteExpired(it)
        }
    }

    fun deleteAllEmptyIndices(lowLevelRestClient: RestClient) {
        val list = getListOfIndices(elastic, "*")
        list.forEach {
            if (getDocumentCountSum(listOf(it), lowLevelRestClient) == 0) {
                deleteIndex(it, elastic)
            }
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
