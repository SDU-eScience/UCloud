package dk.sdu.cloud.elastic.management.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch.core.CountRequest
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest
import co.elastic.clients.json.JsonData
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import org.elasticsearch.client.RestClient
import org.slf4j.Logger
import java.time.LocalDate

const val DAYS_TO_KEEP_DATA = 180L
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
        val removedate = currentDate.minusDays(DAYS_TO_KEEP_DATA).toString().replace("-","." )

        val indicesToDelete = if (indexExists("development_default-*", elastic))
            listOf("development_default-${removedate}", "development_default-${removedate}_small")
        else
            listOf("kubernetes-production-${removedate}", "kubernetes-production-${removedate}_small")

        val existingIndices = indicesToDelete.mapNotNull {
            if (!indexExists(it, elastic)) {
                log.info("no index with the name $it")
                null
            } else it
        }
        if (existingIndices.isEmpty()) return
        deleteIndices(existingIndices, elastic)
    }

    fun deleteOldFileBeatLogs() {
        val datePeriodFormat = LocalDate.now().minusDays(DAYS_TO_KEEP_DATA).toString().replace("-","." )

        val indicesToDelete = listOf("filebeat-${datePeriodFormat}","filebeat-${datePeriodFormat}_small")

        val existingIndices = indicesToDelete.mapNotNull {
            if (!indexExists(it, elastic)) {
                log.info("no index with the name $it")
                null
            } else it
        }
        if (existingIndices.isEmpty()) return
        deleteIndices(existingIndices, elastic)
    }

    fun deleteOldInfrastructureLogs() {
        val datePeriodFormat = LocalDate.now().minusDays(DAYS_TO_KEEP_DATA).toString().replace("-","." )

        val indicesToDelete = listOf("infrastructure-${datePeriodFormat}","infrastructure-${datePeriodFormat}_small")

        val existingIndices = indicesToDelete.mapNotNull {
            if (!indexExists(it, elastic)) {
                log.info("no index with the name $it")
                null
            } else it
        }
        if (existingIndices.isEmpty()) return
        deleteIndices(existingIndices, elastic)
    }

    fun deleteExpiredAllIndices() {
        val list = getListOfIndices(elastic, null)
        list.forEach {
            log.info("Finding expired entries in $it")
            deleteExpired(it)
        }
    }

    fun deleteAllEmptyIndices(lowLevelRestClient: RestClient) {
        val list = getListOfIndices(elastic, null)
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
