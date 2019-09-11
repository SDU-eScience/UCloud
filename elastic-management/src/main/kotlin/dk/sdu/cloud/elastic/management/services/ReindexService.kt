package dk.sdu.cloud.elastic.management.services

import dk.sdu.cloud.service.Loggable
import mbuhot.eskotlin.query.term.exists
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.reindex.ReindexRequest
import org.slf4j.Logger
import java.io.IOException
import java.time.LocalDate

class ReindexService(
    private val elastic: RestHighLevelClient
) {

    fun reindex(fromIndices: List<String>, toIndex: String, lowLevelClient: RestClient) {
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
        request.setSourceBatchSize(2500)
        request.setTimeout(TimeValue.timeValueMinutes(2))

        try {
            elastic.reindex(request, RequestOptions.DEFAULT)
        } catch (ex: IOException) {
            //Did not finish reindexing in 2 min (timeout)
            val fromCount = getDocumentCountSum(fromIndices, lowLevelClient)
            var toCount = getDocumentCountSum(listOf(toIndex), lowLevelClient)
            while (fromCount != toCount) {
                log.info("Waiting for target index to reach count: $fromCount. Currently doc count is: $toCount")
                Thread.sleep(10000)
                toCount = getDocumentCountSum(listOf(toIndex), lowLevelClient)
            }
        }
        //Delete old indices
        fromIndices.forEach {
            deleteIndex(it, elastic)
        }
    }

    fun reindexLogsWithPrefixAWeekBackFrom(
        daysInPast: Long,
        prefix: String,
        lowLevelClient: RestClient,
        delimiter: String = "-"
    ) {
        getAllLogNamesWithPrefix(elastic, prefix, delimiter).forEach {
            val fromIndices = mutableListOf<String>()
            println(it + delimiter + LocalDate.now().minusDays(daysInPast).toString().replace("-", ".") + "_*")
            for (i in 0..6) {
                val index = it +
                        delimiter +
                        LocalDate.now().minusDays(daysInPast + i).toString().replace("-", ".") +
                        "_*"
                if (indexExists(index, elastic)) {
                    fromIndices.add(index)
                }
            }
            val toIndex = it +
                    delimiter +
                    LocalDate.now().minusDays(daysInPast + 6).toString().replace("-", ".") +
                    "-" +
                    LocalDate.now().minusDays(daysInPast).toString().replace("-", ".")

            //if no entries in last week no need to generate an empty index.
            if (fromIndices.isEmpty()) {
                log.info("No entries in last week. Won't create a weekly index")
                return@forEach
            }

            reindex(fromIndices, toIndex, lowLevelClient)
        }

    }

    fun reduceLastMonth(
        prefix: String,
        delimiter: String = "-",
        lowLevelClient: RestClient
    ) {
        val lastDayOfLastMonth = LocalDate.now().withDayOfMonth(1).minusDays(1)
        println("MONTH: ${lastDayOfLastMonth.month}")
        val numberOfDaysInLastMonth = lastDayOfLastMonth.dayOfMonth
        getAllLogNamesWithPrefix(elastic, prefix, delimiter).forEach {
            val fromIndices = mutableListOf<String>()
            for (i in 1..numberOfDaysInLastMonth) {
                val index = it + delimiter + lastDayOfLastMonth.withDayOfMonth(i).toString().replace("-", ".") + "*"
                if (indexExists(index, elastic)) {
                    fromIndices.add(index)
                }
            }
            val toIndex = it +
                    delimiter +
                    "monthly" +
                    delimiter +
                    lastDayOfLastMonth.withDayOfMonth(1).toString().replace("-", ".") +
                    "-" +
                    lastDayOfLastMonth.withDayOfMonth(numberOfDaysInLastMonth).toString().replace("-", ".")

            //if no entries in last month no need to generate an empty index.
            if (fromIndices.isEmpty()) {
                log.info("No entries in last month. Won't create a weekly index")
                return@forEach
            }

            reindex(fromIndices, toIndex, lowLevelClient)
        }
    }

    fun reduceLastQuarter(
        prefix: String,
        delimiter: String = "-",
        lowLevelClient: RestClient
    ) {

    }

    companion object : Loggable {
        override val log: Logger = ExpiredEntriesDeleteService.logger()
    }
}
