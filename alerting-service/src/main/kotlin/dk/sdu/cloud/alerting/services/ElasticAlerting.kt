package dk.sdu.cloud.alerting.services

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.time.LocalDate
import java.util.*

private const val THIRTY_SEC = 30*1000L
private const val FIVE_MIN = 5*60*1000L
private const val FIFTHTEEN_MIN = 15*60*1000L
private const val LIMIT_5XX_PERCENTAGE = 1

class ElasticAlerting(
    private val elastic: RestHighLevelClient,
    private val alertService: AlertingService
) {

    private var errorYellow: Boolean = false
    private var errorRed: Boolean = false
    private var yellow: Int = 0
    private var red: Int = 0
    private var alertSent = false

    private fun checkClusterStatus() {
        val clusterResponse = elastic.cluster().health(ClusterHealthRequest(), RequestOptions.DEFAULT)
        println("errorRed: $errorRed, errorYellow: $errorYellow, red = $red, yellow = $yellow")
        if (clusterResponse.status.toString() == "RED") {
            errorRed = true
            return
        }
        if (clusterResponse.status.toString() == "YELLOW") {
            errorYellow = true
            return
        }
        if (clusterResponse.status.toString() == "GREEN") {
            errorYellow = false
            errorRed = false
            yellow = 0
            red = 0
            if (alertSent) {
                runBlocking {
                    alertService.createAlert(Alert("OK: Elastic is in GREEN again"))
                }
                alertSent = false
            }
        }
    }

    suspend fun alertOnClusterHealth() {
        while (true) {
            if (!errorRed && !errorYellow) {
                checkClusterStatus()
                delay(THIRTY_SEC)
            }
            else {
                if (errorRed) {
                    if (red < 2) {
                        red++
                        delay(THIRTY_SEC)
                        checkClusterStatus()
                        continue
                    }
                    else {
                        if (!alertSent) {
                            alertService.createAlert(Alert("ALERT: Elastic has status RED"))
                            alertSent = true
                        }
                        delay(THIRTY_SEC)
                        checkClusterStatus()
                    }
                }
                if (errorYellow) {
                    if (yellow < 10) {
                        yellow++
                        delay(THIRTY_SEC)
                        checkClusterStatus()
                        continue
                    }
                    else {
                        if (!alertSent) {
                            alertService.createAlert(Alert("ALERT: Elastic has status YELLOW"))
                            alertSent = true
                        }
                        delay(THIRTY_SEC)
                        checkClusterStatus()
                    }
                }
            }
        }
    }

    suspend fun alertOnStatusCode() {
        var alertOnStatus = false
        while (true) {
            val yesterday = LocalDate.now().minusDays(1).toString().replace("-", ".")
            val today = LocalDate.now().toString().replace("-", ".")
            val request = SearchRequest()

            request.indices("http_logs_*$yesterday", "http_logs_*$today")
            request.source(
                SearchSourceBuilder().query(
                    QueryBuilders.boolQuery()
                        .must(
                            QueryBuilders.matchAllQuery()
                        )
                        .filter(
                            QueryBuilders.rangeQuery("@timestamp")
                                .gte(Date(Date().time - FIFTHTEEN_MIN))
                                .lt(Date())
                        )
                        .filter(
                            QueryBuilders.rangeQuery("responseCode")
                                .gte(500)
                        )
                )
            )
            val numberOf5XXStatusCodes = elastic.search(request, RequestOptions.DEFAULT).hits.totalHits.toDouble()


            val TotalNumberOfEntriesRequest = SearchRequest()

            TotalNumberOfEntriesRequest.indices("http_logs_*$yesterday", "http_logs_*$today")
            TotalNumberOfEntriesRequest.source(
                SearchSourceBuilder().query(
                    QueryBuilders.boolQuery()
                        .must(
                            QueryBuilders.matchAllQuery()
                        )
                        .filter(
                            QueryBuilders.rangeQuery("@timestamp")
                                .gte(Date(Date().time - FIFTHTEEN_MIN))
                                .lt(Date())
                        )
                )
            )

            val totalNumberOfEntries =
                elastic.search(TotalNumberOfEntriesRequest, RequestOptions.DEFAULT).hits.totalHits.toDouble()
            val percentage = numberOf5XXStatusCodes / totalNumberOfEntries * 100
            println(percentage)
            if (percentage > LIMIT_5XX_PERCENTAGE && !alertOnStatus) {
                val message = "ALERT: To many 5XX status codes\n" +
                        "Entries last 15 min: $totalNumberOfEntries \n" +
                        "Number of 5XX status codes: $numberOf5XXStatusCodes \n" +
                        "Percentage: ${percentage}% (Limit is $LIMIT_5XX_PERCENTAGE %)"
                alertService.createAlert(Alert(message))
                alertOnStatus = true
            }
            if (percentage < LIMIT_5XX_PERCENTAGE && alertOnStatus) {
                val message = "OK: 5XX statusCodes percentage back below limit"
                alertService.createAlert(Alert(message))
                alertOnStatus = false
            }
            delay(FIVE_MIN)
        }
    }

}
