package dk.sdu.cloud.elastic.management.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.slack.api.SendAlertRequest
import dk.sdu.cloud.slack.api.SlackDescriptions
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.core.TimeValue
import org.elasticsearch.index.reindex.ReindexRequest
import org.slf4j.Logger
import java.io.IOException
import java.time.LocalDate

class ReindexService(
    private val elastic: RestHighLevelClient
) {

    fun reindexSpecificIndices(fromIndices: List<String>, toIndices: List<String>, lowLevelClient: RestClient) {
        if (fromIndices.isEmpty() || fromIndices.size != toIndices.size) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "From cannot be empty or sizes are not equal.")
        }
        fromIndices.forEachIndexed { index, fromIndex ->
            val toIndex = toIndices[index]
            log.info("Reindexing: $fromIndex to $toIndex")
            if (!indexExists(toIndex, elastic)) {
                createIndex(toIndex, elastic)
            }

            val request = ReindexRequest()
            request.setSourceIndices(fromIndex)
            request.setDestIndex(toIndex)
            request.setSourceBatchSize(2500)
            request.setTimeout(TimeValue.timeValueMinutes(2))

            try {
                elastic.reindex(request, RequestOptions.DEFAULT)
            } catch (ex: Exception) {
                when (ex) {
                    is IOException -> {
                        //Did not finish reindexing in 2 min (timeout)
                        val fromCount = getDocumentCountSum(listOf(fromIndex), lowLevelClient)
                        var toCount = getDocumentCountSum(listOf(toIndex), lowLevelClient)
                        while (fromCount != toCount) {
                            log.info("Waiting for target index to reach count: $fromCount. Currently doc count is: $toCount")
                            Thread.sleep(10000)
                            toCount = getDocumentCountSum(listOf(toIndex), lowLevelClient)
                        }
                    }
                    is ElasticsearchStatusException -> {
                        //This is most likely due to API changes resulting in not same mapping for entire week
                        if (ex.message == "Unable to parse response body") {
                            log.info("status exception")
                            log.info(ex.toString())
                            reindexErrors.add(fromIndex)
                            log.info("Does not delete due to mapping issue - investigate")
                            return@forEachIndexed
                        }
                        else {
                            throw ex
                        }
                    }
                    else -> {
                        log.warn("not known exception")
                        throw ex
                    }
                }
            }
            //Delete old indices
            deleteIndex(fromIndex, elastic)
        }
    }

    fun reindex(fromIndices: List<String>, toIndex: String, lowLevelClient: RestClient) {
        //Should always be lowercase
        val destinationIndex = toIndex.toLowerCase()

        if (fromIndices.isEmpty()) {
            //Nothing to reindex
            return
        }

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
        request.setSlices(10)

        try {
            elastic.reindex(request, RequestOptions.DEFAULT)
        } catch (ex: Exception) {
            when (ex) {
                is IOException -> {
                    //Did not finish reindexing in 2 min (timeout)
                    log.info("Did not finish in time (2 min adding to errors)")
                    reindexErrors.add(fromIndices.joinToString())
                    log.info("Does not delete due to mapping issue - investigate")
                }
                is ElasticsearchStatusException -> {
                    //This is most likely due to API changes resulting in not same mapping
                    if (ex.message == "Unable to parse response body") {
                        log.info("status exception")
                        log.info(ex.toString())
                        reindexErrors.add(fromIndices.joinToString())
                        log.info("Does not delete due to mapping issue - investigate")
                        return
                    }
                    else {
                        throw ex
                    }
                }
                else -> {
                    log.warn("not known exception")
                    throw ex
                }
            }
        }
        //Delete old indices
        fromIndices.forEach {
            deleteIndex(it, elastic)
        }
    }

    val reindexErrors = mutableListOf<String>()

    fun reindexToMonthly (prefix: String, lowLevelClient: RestClient, serviceClient: AuthenticatedClient) {
        val minusDays = 8L
        val date = LocalDate.now().minusDays(minusDays).toString().replace("-",".")
        try {
            getAllEmptyIndicesWithRegex(elastic, lowLevelClient, "http_logs_*$date*").forEach {
                log.info("deleting $it since no docs")
                deleteIndex(it, elastic)
            }
        } catch (ex: ElasticsearchStatusException) {
            if (ex.toString().contains("index_not_found")) {
                // no indices we can finish
                return
            }
            else {
                throw ex
            }
        }
        val logs = getAllLogNamesWithPrefixForDate(elastic, prefix, date)
        logs.forEach { logIndex ->
            val fromIndex = logIndex
            val toIndex = logIndex.substring(0, logIndex.indexOf("-")+1) +
                LocalDate.now().minusDays(minusDays).toString().dropLast(3).replace("-",".")
            reindex(listOf(fromIndex), toIndex, lowLevelClient)
        }
        runBlocking {
            SlackDescriptions.sendAlert.call(
                SendAlertRequest(
                    "Following indices have been attempted merged, but due to issues have stopped. " +
                        "Original indices have been left intact for follow up." +
                        "${reindexErrors.joinToString()}"
                ),
                serviceClient
            )
        }
    }

    companion object : Loggable {
        override val log: Logger = ExpiredEntriesDeleteService.logger()
    }
}
