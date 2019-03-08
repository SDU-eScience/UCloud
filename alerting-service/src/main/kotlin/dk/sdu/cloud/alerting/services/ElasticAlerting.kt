package dk.sdu.cloud.alerting.services

import dk.sdu.cloud.alerting.Configuration
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.delay
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.net.ConnectException
import java.time.LocalDate
import java.util.*

private const val FIFTEEN_SEC = 15 * 1000L
private const val THIRTY_SEC = 30 * 1000L
private const val FIVE_MIN = 5 * 60 * 1000L
private const val FIFTEEN_MIN = 15 * 60 * 1000L

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
            log.debug("Current percentage is: $percentage, with limit: $limit5xxPercentage." +
                    " NUmber of entries: $totalNumberOfEntries")
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

    companion object : Loggable {
        override val log = ElasticAlerting.logger()
    }
}
