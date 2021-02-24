package dk.sdu.cloud.alerting.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.alerting.Configuration
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.slack.api.SendAlertRequest
import dk.sdu.cloud.slack.api.SlackDescriptions
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
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
    private val client: AuthenticatedClient
) {
    private suspend fun sendAlert(message: String){
        SlackDescriptions.sendAlert.call(
            SendAlertRequest(message),
            client
        ).orThrow()
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
                sendAlert(message)
                alertOnStatus = true
            }
            if (percentage < limit5xxPercentage && alertOnStatus) {
                val message = "OK: 5XX statusCodes percentage back below limit"

                sendAlert(message)
                alertOnStatus = false
            }
            delay(FIVE_MIN)
            numberOfRetries = 0
        }
    }

    @Serializable
    data class LogFileInfo(
        val path: String
    )

    @Serializable
    data class Info(
        val offset: Long,
        val file: LogFileInfo
    )

    @Serializable
    data class Fields(
        val index: String
    )

    @Serializable
    data class Host(
        val name: String
    )

    @Serializable
    data class Agent(
        val ephemeral_id: String,
        val id: String,
        val name: String,
        val type: String,
        val version: String,
        val hostname:String
    )

    @Serializable
    data class LogEntry(
        val log: Info,
        val message: String,
        val fields: Fields,
        val host: Host,
        val agent: Agent
    )

    suspend fun ambassadorResponseAlert(
        elastic: RestHighLevelClient,
        configuration: Configuration
    ) {
        val whitelistedIPs = configuration.omissions?.whiteListedIPs ?: emptyList()
        val limitFor4xx = configuration.limits?.limitFor4xx ?: 500
        val limitFor5xx = configuration.limits?.limitFor5xx ?: 200
        val index = configuration.limits?.indexFor4xx ?: "development_default"
        while (true) {
            val today = LocalDate.now()
            val yesterday = LocalDate.now().minusDays(1)

            val searchRequest = SearchRequest()

            searchRequest.indices("$index-*", "$index-$today*", "$index-$yesterday*")
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
                            QueryBuilders.multiMatchQuery("ambassador")
                        )
                )
                    .size(5000)
            )

            val results = elastic.search(searchRequest, RequestOptions.DEFAULT)
            val numberOfRequestsPerIP = hashMapOf<String, Int>()
            var numberOf5xx = 0
            results.hits.forEach {
                val log = defaultMapper.decodeFromString<LogEntry>(it.sourceAsString)
                if (log.message.contains("ACCESS")) {
                    if (log.message.contains(
                            Regex(
                                """
                            "\s4[0-9]{2}\s
                        """.trimIndent()
                            )
                        )
                    ) {
                        val ips = log.message.split(" ")[14]
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
                    if (log.message.contains(
                            Regex(
                                """
                                    "\s5[0-9]{2}\s
                                """.trimIndent()
                            )
                        )) {
                        numberOf5xx++
                    }
                }
            }
            log.info(numberOfRequestsPerIP.toString())
            val suspectBehaviorIPs = numberOfRequestsPerIP.filter { it.value > limitFor4xx }.map { it.key }
            if (suspectBehaviorIPs.isNotEmpty()) {
                val message = "Following IPs have a high amount of 4xx: ${suspectBehaviorIPs.joinToString()}"
                sendAlert(message)
            }
            log.info("Number of 5xx: $numberOf5xx")
            if (numberOf5xx > limitFor5xx) {
                val message = "Many 5xx in ambassador: $numberOf5xx"
                sendAlert(message)
            }
            delay(FIFTEEN_MIN)
        }
    }

    companion object : Loggable {
        override val log = ElasticAlerting.logger()
    }
}
