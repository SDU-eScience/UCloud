package dk.sdu.cloud.alerting.services

import dk.sdu.cloud.alerting.Configuration
import dk.sdu.cloud.defaultMapper
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
    YELLOW(isError = true, failuresForATrigger = 20),
    GREEN(isError = false, failuresForATrigger = 0)
}

class ElasticAlerting(
    private val elastic: RestHighLevelClient,
    private val alertService: AlertingService,
    private val testMode: Boolean = false
) {
    private var status: Status = Status.GREEN
    private var errorCount: Int = 0
    private var alertSent = false


    fun getStatus(): Status? {
        if (testMode)
            return status
        return null
    }

    fun getErrorCount():Int? {
        if (testMode)
            return errorCount
        return null
    }

    fun setAlertSent(value: Boolean) {
        if (testMode) {
            alertSent = value
        }
    }

    fun setStatus(value: Status) {
        if (testMode) {
            status = value
        }
    }

    fun setErrorCount(value: Int) {
        if (testMode) {
            errorCount = value
        }
    }

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
                delay(if (testMode) 1000 else FIFTEEN_SEC)
            }
        }
        throw ConnectException("Lost connection to Elasticsearch")
    }

    suspend fun alertOnClusterHealth() {
        while (true) {
            if (!status.isError) {
                checkClusterStatus()
                delay(if (testMode) 1000 else THIRTY_SEC)
            } else {
                if (errorCount < status.failuresForATrigger) {
                    errorCount++
                    checkClusterStatus()
                    delay(if (testMode) 1000 else THIRTY_SEC)
                } else {
                    if (!alertSent) {
                        alertService.createAlert(Alert("ALERT: Elastic has status: $status"))
                        alertSent = true
                    }
                    delay(if (testMode) 1000 else THIRTY_SEC)
                    checkClusterStatus()
                }
            }
            if (testMode) {
                return
            }
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
                            fields.last().startsWith("elasticsearch-newdata"))
                ) return@forEach
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

    suspend fun alertOnIndicesCount(lowLevelClient: RestClient, configuration: Configuration) {
        val alertLimit = configuration.limits?.alertWhenNumberOfShardsAvailableIsLessThan ?: 500
        var alertSent = false
        while (true) {
            val healthResponse = lowLevelClient.performRequest(Request("GET", "/_cluster/health"))
            if (healthResponse.statusLine.statusCode != 200) {
                log.warn("Statuscode for health response was not 200")
                delay( if(testMode) 1000 else ONE_HOUR)
                if (!testMode) continue
                else return
            }
            val responseForActiveShards = EntityUtils.toString(healthResponse.entity)
            val numberOfActiveShards = defaultMapper.readTree(responseForActiveShards).findValue("active_shards").asInt()
            val numberOfDataNodes = defaultMapper.readTree(responseForActiveShards).findValue("number_of_data_nodes").asInt()

           log.info("Number of Active shards: $numberOfActiveShards")

            val settingsResponse = lowLevelClient.performRequest(Request("GET", "_cluster/settings?flat_settings=true"))
            if (settingsResponse.statusLine.statusCode != 200) {
                log.warn("Status code for settings response was not 200")
                delay(ONE_HOUR)
                continue
            }
            val responseForMaxShardsPerNode = EntityUtils.toString(settingsResponse.entity)
            val maximumNumberOfShardsPerDataNode = defaultMapper.readTree(responseForMaxShardsPerNode).findValue("cluster.max_shards_per_node").asInt()

            val totalMaximumNumberOfShards = maximumNumberOfShardsPerDataNode * numberOfDataNodes

            log.info("Total maximum number of shards for cluster: $totalMaximumNumberOfShards")
            log.info("Number of shards still available: ${totalMaximumNumberOfShards - numberOfActiveShards}")

            if ((totalMaximumNumberOfShards - numberOfActiveShards) <= alertLimit && !alertSent) {
                val message =
                    "Number of shards remaining before hard limit of $totalMaximumNumberOfShards is reached is " +
                            "below $alertLimit (Used: $numberOfActiveShards). " +
                            "Either reduce number of shards or increase limit."
                alertService.createAlert(Alert(message))
                alertSent = true
            }
            if ((totalMaximumNumberOfShards - numberOfActiveShards) > alertLimit && alertSent) {
                val message = "Number of available shards are acceptable again."
                alertService.createAlert(Alert(message))
                alertSent = false
            }
            if (alertSent)
                delay(THIRTY_MIN)
            else
                delay(HALF_DAY)
        }
    }

    suspend fun alertOnNumberOfDocs(lowLevelClient: RestClient) {
        while(true) {
            val shardsResponse = lowLevelClient.performRequest(Request("GET", "/_cat/shards?h=i,d"))
            if (shardsResponse.statusLine.statusCode != 200) {
                log.warn("Status code for shard response was not 200")
                if (testMode) {
                    return
                }
                delay(ONE_HOUR)
                continue
            }
            val entity = EntityUtils.toString(shardsResponse.entity)
            val lines = entity.split("\n")
            lines.forEach { line ->
                if (line.isNullOrBlank()) return@forEach
                val segments = line.split(" ")
                val index = segments.first()
                val docCountString = segments.last()
                if (!docCountString.isNullOrBlank()) {
                    val docCount = docCountString.toInt()
                    if (docCount > DOC_HIGH_LIMIT) {
                        log.warn("docCount of index: $index is above High limit: $docCount")
                        alertService.createAlert(
                            Alert(
                                "Alert: Doc count of index: $index is to high. Reindex or delete entires. " +
                                        "Doc Count: $docCount out of ${Integer.MAX_VALUE} possible"
                            )
                        )
                        if (testMode) {
                            return
                        }
                    } else if (docCount > DOC_LOW_LIMIT) {
                        log.warn("docCount of index: $index is above low limit: $docCount")
                        alertService.createAlert(
                            Alert(
                                "Alert: Doc count of index: $index has reached low limit. " +
                                        "Be aware of potential need for reindexing index. " +
                                        "Doc Count: $docCount out of ${Integer.MAX_VALUE} possible"
                            )
                        )
                        if (testMode) {
                            return
                        }
                    }
                }
            }
            if (testMode) {
                return
            }
            delay(THIRTY_MIN)
        }
    }

    companion object : Loggable {
        override val log = ElasticAlerting.logger()
    }
}
