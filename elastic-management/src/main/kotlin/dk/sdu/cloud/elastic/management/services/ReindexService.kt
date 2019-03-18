package dk.sdu.cloud.elastic.management.services

import dk.sdu.cloud.service.Loggable
import org.elasticsearch.action.admin.indices.get.GetIndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.reindex.ReindexRequest
import org.slf4j.Logger
import java.lang.IllegalArgumentException
import java.time.LocalDate

class ReindexService(
    private val elastic: RestHighLevelClient
) {

    fun reindex(fromIndices: List<String>, toIndex: String) {
        //Should always be lowercase
        val destinationIndex = toIndex.toLowerCase()

        var error = false
        fromIndices.forEach {
            if (!indexExists(it, elastic)) {
                log.warn("Index: $it, does not exist. Check spelling and that all is lowercase.")
                error = true
            }
        }
        if (error) {
            log.info("Quiting due to missing indices in request")
            throw IllegalArgumentException()
        }

        if (!indexExists(destinationIndex, elastic)) {
            createIndex(destinationIndex, elastic)
        }

        val request = ReindexRequest()

        request.setSourceIndices(*fromIndices.toTypedArray())
        request.setDestIndex(destinationIndex)

        elastic.reindex(request, RequestOptions.DEFAULT)

        //Delete old indices
        fromIndices.forEach {
            deleteIndex(it, elastic)
        }
    }

    fun reindexLogsWithPrefixAWeekBackFrom(
        daysInPast: Long,
        prefix: String,
        delimiter: String = "-"
    ) {
        getAllLogNamesWithPrefix(elastic, prefix, delimiter).forEach {
            val fromIndices = mutableListOf<String>()
            for (i in 0..6) {
                val index = it +
                        delimiter +
                        LocalDate.now().minusDays(daysInPast+i).toString().replace("-","." ) +
                        "_*"
                if (indexExists(index, elastic)) {
                    fromIndices.add(index)
                }
            }
            val toIndex = it +
                    delimiter +
                    LocalDate.now().minusDays(daysInPast+6).toString().replace("-","." ) +
                    "-" +
                    LocalDate.now().minusDays(daysInPast).toString().replace("-","." )

            reindex(fromIndices, toIndex)
        }

    }

    companion object : Loggable {
        override val log: Logger = ExpiredEntriesDeleteService.logger()
    }
}
