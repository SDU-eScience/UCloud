package dk.sdu.cloud.alerting.services

import dk.sdu.cloud.alerting.Configuration
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.delay
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.net.ConnectException
import java.time.LocalDate
import java.util.*

class NetworkTrafficAlerts(
    private val elastic: RestHighLevelClient,
    private val alertService: AlertingService
) {
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
                                .gte(Date(Time.now() - FIFTEEN_MIN))
                                .lt(Date(Time.now()))
                        )
                        .filter(
                            QueryBuilders.rangeQuery("responseCode")
                                .gte(500)
                        )
                )
            )
            val numberOf5XXStatusCodes = try {
                elastic.search(requestFor5xxCodes, RequestOptions.DEFAULT).hits.totalHits.value.toDouble()
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
                                .gte(Date(Time.now() - FIFTEEN_MIN))
                                .lt(Date(Time.now()))
                        )
                )
            )

            val totalNumberOfEntries = try {
                elastic.search(totalNumberOfEntriesRequest, RequestOptions.DEFAULT).hits.totalHits.value.toDouble()
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
            ElasticAlerting.log.debug(
                "Current percentage is: $percentage, with limit: $limit5xxPercentage." +
                        " Number of entries: $totalNumberOfEntries"
            )
            if (percentage > limit5xxPercentage && !alertOnStatus) {
                val message = "ALERT: Too many 5XX status codes\n" +
                        "Entries last 15 min: $totalNumberOfEntries \n" +
                        "Number of 5XX status codes: $numberOf5XXStatusCodes \n" +
                        "Percentage: $percentage % (Limit is $limit5xxPercentage %)"
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

    suspend fun ambassador4xxAlert(
        elastic: RestHighLevelClient,
        configuration: Configuration
    ) {
        val whitelistedIPs = configuration.omissions?.whiteListedIPs ?: emptyList()
        val limitFor4xx = configuration.limits?.limitFor4xx ?: 500
        val index = configuration.limits?.indexFor4xx ?: "development_default"
        while (true) {
            val today = LocalDate.now()
            val yesterday = LocalDate.now().minusDays(1)

            val searchRequest = SearchRequest()

            searchRequest.indices("$index-$today*", "$index-$yesterday*")
            searchRequest.source(
                SearchSourceBuilder().query(
                    QueryBuilders.boolQuery()
                        .must(
                            QueryBuilders.matchAllQuery()
                        )
                        .filter(
                            QueryBuilders.rangeQuery("@timestamp")
                                .gte(Date(Time.now() - THIRTY_MIN))
                                .lt(Date(Time.now()))
                        )
                        .filter(
                            QueryBuilders.queryStringQuery("4??").analyzeWildcard(true).field("log")
                        )
                        .filter(
                            QueryBuilders.matchPhraseQuery("kubernetes.container_name", "ambassador")
                        )
                )
                    .size(5000)
                    .fetchSource("log", null)
            )

            val results = elastic.search(searchRequest, RequestOptions.DEFAULT)
            log.info(results.hits.totalHits.value.toString())
            val numberOfRequestsPerIP = hashMapOf<String, Int>()
            results.hits.hits.forEach {
                val ambassadorLogSplitted = it.sourceAsMap["log"].toString().split(" ")
                if (ambassadorLogSplitted.first() == "ACCESS") {
                    val responseCode = ambassadorLogSplitted[5].toInt()
                    if (responseCode in 399..500) {
                        val ips = ambassadorLogSplitted[11]
                        val ip = ips.dropLast(1).drop(1).split(",").first()
                        if (ip in whitelistedIPs) {
                            return@forEach
                        } else {
                            if (numberOfRequestsPerIP[ip] == null) {
                                numberOfRequestsPerIP[ip] = 1
                            } else {
                                val i = numberOfRequestsPerIP[ip]
                                val k = i!! + 1
                                numberOfRequestsPerIP[ip] = k
                            }
                        }
                    }
                }
            }
            log.info(numberOfRequestsPerIP.toString())
            val suspectBehaviorIPs = numberOfRequestsPerIP.filter { it.value > limitFor4xx }.map { it.key }
            if (suspectBehaviorIPs.isNotEmpty()) {
                val message = "Following IPs have a high amount of 4xx: ${suspectBehaviorIPs.joinToString()}"
                alertService.createAlert(Alert(message))
            }
            delay(FIFTEEN_MIN)

        }
    }

    companion object : Loggable {
        override val log = ElasticAlerting.logger()
    }
}
