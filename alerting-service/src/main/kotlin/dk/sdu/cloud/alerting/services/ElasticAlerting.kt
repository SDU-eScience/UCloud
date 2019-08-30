package dk.sdu.cloud.alerting.services

import dk.sdu.cloud.alerting.Configuration
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.delay
import org.apache.http.util.EntityUtils
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.Request
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.net.ConnectException
import java.time.LocalDate
import java.util.*

enum class Status(val isError: Boolean, val failuresForATrigger: Int) {
    RED(isError = true, failuresForATrigger = 2),
    YELLOW(isError = true, failuresForATrigger = 10),
    GREEN(isError = false, failuresForATrigger = 0)
}

class ElasticAlerting(
    private val elastic: RestHighLevelClient,
    private val alertService: AlertingService
) {
    private var status: Status = Status.GREEN
    private var errorCount: Int = 0
    private var alertSent = false

    private suspend fun checkClusterStatus() {
        for (i in 1..3) {
            try {
                val clusterResponse = elastic.cluster().health(ClusterHealthRequest(), RequestOptions.DEFAULT)
                log.debug("Current Status: $status, errorCount = $errorCount")
                if (clusterResponse.status.toString() == Status.RED.name) {
                    status = Status.RED
                    return
                }
                if (clusterResponse.status.toString() == Status.YELLOW.name) {
                    status = Status.YELLOW
                    return
                }
                if (clusterResponse.status.toString() == Status.GREEN.name) {
                    status = Status.GREEN
                    errorCount = 0
                    if (alertSent) {
                        alertService.createAlert(Alert("OK: Elastic is in GREEN again"))
                        alertSent = false
                    }
                    return
                }
            } catch (ex: ConnectException) {
                delay(FIFTEEN_SEC)
            }
        }
        throw ConnectException("Lost connection to Elasticsearch")
    }

    suspend fun alertOnClusterHealth() {
        while (true) {
            if (!status.isError) {
                checkClusterStatus()
                delay(THIRTY_SEC)
            } else {
                if (errorCount < status.failuresForATrigger) {
                    errorCount++
                    checkClusterStatus()
                    delay(THIRTY_SEC)
                } else {
                    if (!alertSent) {
                        alertService.createAlert(Alert("ALERT: Elastic has status: $status"))
                        alertSent = true
                    }
                    delay(THIRTY_SEC)
                    checkClusterStatus()
                }
            }
        }
    }

    suspend fun alertOnStatusCode(configuration: Configuration) {
        var alertOnStatus = false
        val limit5xxPercentage = configuration.limits?.percentLimit500Status ?: 10.0
        var numberOfRetries = 0
        while (true) {
            if (numberOfRetries == 3) {
                throw ConnectException("Lost connection to Elasticsearch")
            }
            //Period Format = YYYY.MM.dd
            val yesterdayPeriodFormat = LocalDate.now().minusDays(1).toString().replace("-", ".")
            val todayPeriodFormat = LocalDate.now().toString().replace("-", ".")
            val requestFor5xxCodes = SearchRequest()

            requestFor5xxCodes.indices("http_logs_*$yesterdayPeriodFormat*", "http_logs_*$todayPeriodFormat*")
            requestFor5xxCodes.source(
                SearchSourceBuilder().query(
                    QueryBuilders.boolQuery()
                        .must(
                            QueryBuilders.matchAllQuery()
                        )
                        .filter(
                            QueryBuilders.rangeQuery("@timestamp")
                                .gte(Date(Date().time - FIFTEEN_MIN))
                                .lt(Date())
                        )
                        .filter(
                            QueryBuilders.rangeQuery("responseCode")
                                .gte(500)
                        )
                )
            )
            val numberOf5XXStatusCodes = try {
                elastic.search(requestFor5xxCodes, RequestOptions.DEFAULT).hits.totalHits.toDouble()
            } catch (ex: ConnectException) {
                numberOfRetries++
                delay(FIFTEEN_SEC)
                continue
            }

            val totalNumberOfEntriesRequest = SearchRequest()

            totalNumberOfEntriesRequest.indices("http_logs_*$yesterdayPeriodFormat*", "http_logs_*$todayPeriodFormat*")
            totalNumberOfEntriesRequest.source(
                SearchSourceBuilder().query(
                    QueryBuilders.boolQuery()
                        .must(
                            QueryBuilders.matchAllQuery()
                        )
                        .filter(
                            QueryBuilders.rangeQuery("@timestamp")
                                .gte(Date(Date().time - FIFTEEN_MIN))
                                .lt(Date())
                        )
                )
            )

            val totalNumberOfEntries = try {
                elastic.search(totalNumberOfEntriesRequest, RequestOptions.DEFAULT).hits.totalHits.toDouble()
            } catch (ex: ConnectException) {
                numberOfRetries++
                delay(FIFTEEN_SEC)
                continue
            }
            //If no indices that is by name http_logs_*date
            if (totalNumberOfEntries.toInt() == 0) {
                delay(FIVE_MIN)
                continue
            }
            val percentage = numberOf5XXStatusCodes / totalNumberOfEntries * 100
            log.debug(
                "Current percentage is: $percentage, with limit: $limit5xxPercentage." +
                        " Number of entries: $totalNumberOfEntries"
            )
            if (percentage > limit5xxPercentage && !alertOnStatus) {
                val message = "ALERT: Too many 5XX status codes\n" +
                        "Entries last 15 min: $totalNumberOfEntries \n" +
                        "Number of 5XX status codes: $numberOf5XXStatusCodes \n" +
                        "Percentage: ${percentage}% (Limit is $limit5xxPercentage %)"
                alertService.createAlert(Alert(message))
                alertOnStatus = true
            }
            if (percentage < limit5xxPercentage && alertOnStatus) {
                val message = "OK: 5XX statusCodes percentage back below limit"
                println(message)

                alertService.createAlert(Alert(message))
                alertOnStatus = false
            }
            delay(FIVE_MIN)
            numberOfRetries = 0
        }
    }


    suspend fun alertOnStorage(lowLevelClient: RestClient, configuration: Configuration) {
        var alertSent = false
        var alertCounter = 0
        val lowLimitPercentage = configuration.limits?.storageInfoLimit ?: 50.0
        val midLimitPercentage = configuration.limits?.storageWarnLimit ?: 80.0
        val highLimitPercentage = configuration.limits?.storageCriticalLimit ?: 90.0

        while (true) {
            val response = lowLevelClient.performRequest(Request("GET", "/_cat/nodes?h=dup,n"))
            if (response.statusLine.statusCode != 200) {
                log.warn("Statuscode was not 200")
                delay(ONE_HOUR)
                continue
            }
            val dataNodes = EntityUtils.toString(response.entity).split("\n").filter { it != "" }
            dataNodes.forEach { line ->
                //Catches case of unassigend node which has no values
                val fields = line.split(" ").filter { it != "" }
                if (
                    !(fields.last().startsWith("elasticsearch-data") ||
                    fields.last().startsWith("elasticsearch-newdata"))) return@forEach
                val usedStoragePercentage = fields.first().toDouble()
                log.info("${fields.last()} is using $usedStoragePercentage% of storage.")
                when {
                    usedStoragePercentage > highLimitPercentage && alertCounter == 2 -> {
                        val message =
                            "ALERT: Available storage of ${fields.last()} is below ${highLimitPercentage * 100}"
                        alertService.createAlert(Alert(message))
                        alertCounter++
                    }
                    usedStoragePercentage > midLimitPercentage && alertCounter == 1 -> {
                        val message = "WARNING: storage of ${fields.last()} is below ${midLimitPercentage * 100}%"
                        alertService.createAlert(Alert(message))
                        alertCounter++
                    }
                    usedStoragePercentage > lowLimitPercentage && !alertSent -> {
                        val message =
                            "INFO: Available storage of ${fields.last()} is below ${lowLimitPercentage * 100}%"
                        alertService.createAlert(Alert(message))
                        alertCounter++
                        alertSent = true
                    }
                    else -> {
                        alertCounter = 0
                        alertSent = false
                    }
                }
            }
            delay(ONE_HOUR)
        }
    }

    companion object : Loggable {
        override val log = ElasticAlerting.logger()
    }
}
