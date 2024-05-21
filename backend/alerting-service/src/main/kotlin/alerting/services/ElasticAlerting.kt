package dk.sdu.cloud.alerting.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.cluster.GetClusterSettingsRequest
import co.elastic.clients.elasticsearch.cluster.HealthRequest
import dk.sdu.cloud.alerting.Configuration
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.slack.api.SendAlertRequest
import dk.sdu.cloud.slack.api.SlackDescriptions
import kotlinx.coroutines.delay
import org.apache.http.util.EntityUtils
import org.elasticsearch.client.Request
import org.elasticsearch.client.RestClient
import java.net.ConnectException

enum class Status(val isError: Boolean, val failuresForATrigger: Int) {
    RED(isError = true, failuresForATrigger = 2),
    YELLOW(isError = true, failuresForATrigger = 40),
    GREEN(isError = false, failuresForATrigger = 0)
}

class ElasticAlerting(
    private val elastic: ElasticsearchClient,
    private val client: AuthenticatedClient,
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

    private suspend fun sendAlert(message: String){
        SlackDescriptions.sendAlert.call(
            SendAlertRequest(message),
            client
        ).orThrow()
    }

    private suspend fun checkClusterStatus() {
        for (i in 1..3) {
            try {
                val clusterResponse = elastic.cluster().health(HealthRequest.Builder().build())
                log.debug("Current Status: $status, errorCount = $errorCount")
                if (clusterResponse.status().toString().uppercase() == Status.RED.name) {
                    status = Status.RED
                    return
                }
                if (clusterResponse.status().toString().uppercase() == Status.YELLOW.name) {
                    status = Status.YELLOW
                    return
                }
                if (clusterResponse.status().toString().uppercase() == Status.GREEN.name) {
                    status = Status.GREEN
                    errorCount = 0
                    if (alertSent) {
                        sendAlert("OK: Elastic is in GREEN again")
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
                        sendAlert("ALERT: Elastic has status: $status")
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
                            "ALERT: Available storage of ${fields.last()} is below ${highLimitPercentage}"
                        sendAlert(message)
                        alertCounter++
                    }
                    usedStoragePercentage > midLimitPercentage && alertCounter == 1 -> {
                        val message = "WARNING: storage of ${fields.last()} is below ${midLimitPercentage}%"
                        sendAlert(message)
                        alertCounter++
                    }
                    usedStoragePercentage > lowLimitPercentage && !alertSent -> {
                        val message =
                            "INFO: Available storage of ${fields.last()} is below ${lowLimitPercentage}%"
                        sendAlert(message)
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

    suspend fun alertOnIndicesCount(configuration: Configuration) {
        val alertLimit = configuration.limits?.alertWhenNumberOfShardsAvailableIsLessThan ?: 500
        var alertSent = false
        while (true) {
            val healthResponse = elastic.cluster().health(HealthRequest.Builder().build())

            val numberOfActiveShards = healthResponse.activeShards()
            val numberOfDataNodes = healthResponse.numberOfDataNodes()

            log.info("Number of Active shards: $numberOfActiveShards")

            val settingsResponse = elastic.cluster().getSettings(GetClusterSettingsRequest.Builder().build())
            val maximumNumberOfShardsPerDataNode = settingsResponse.persistent()["max_shards_per_node"]?.toString() ?: "10000"

            val totalMaximumNumberOfShards = maximumNumberOfShardsPerDataNode.toInt() * numberOfDataNodes

            log.info("Total maximum number of shards for cluster: $totalMaximumNumberOfShards")
            log.info("Number of shards still available: ${totalMaximumNumberOfShards - numberOfActiveShards}")

            if ((totalMaximumNumberOfShards - numberOfActiveShards) <= alertLimit && !alertSent) {
                val message =
                    "Number of shards remaining before hard limit of $totalMaximumNumberOfShards is reached is " +
                            "below $alertLimit (Used: $numberOfActiveShards). " +
                            "Either reduce number of shards or increase limit."
                sendAlert(message)
                alertSent = true
            }
            if ((totalMaximumNumberOfShards - numberOfActiveShards) > alertLimit && alertSent) {
                val message = "Number of available shards are acceptable again."
                sendAlert(message)
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
                        sendAlert(
                            "Alert: Doc count of index: $index is to high. Reindex or delete entires. " +
                            "Doc Count: $docCount out of ${Integer.MAX_VALUE} possible"
                        )
                        if (testMode) {
                            return
                        }
                    } else if (docCount > DOC_LOW_LIMIT) {
                        log.warn("docCount of index: $index is above low limit: $docCount")
                        sendAlert(
                            "Alert: Doc count of index: $index has reached low limit. " +
                            "Be aware of potential need for reindexing index. " +
                            "Doc Count: $docCount out of ${Integer.MAX_VALUE} possible"

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
