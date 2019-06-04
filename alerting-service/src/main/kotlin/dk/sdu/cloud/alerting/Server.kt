package dk.sdu.cloud.alerting

import dk.sdu.cloud.alerting.services.Alert
import dk.sdu.cloud.alerting.services.AlertingService
import dk.sdu.cloud.alerting.services.ElasticAlerting
import dk.sdu.cloud.alerting.services.KubernetesAlerting
import dk.sdu.cloud.alerting.services.SlackNotifier
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.stackTraceToString
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import kotlin.system.exitProcess

class Server(
    private val elasticHostAndPort: ElasticHostAndPort,
    private val config: Configuration,
    override val micro: Micro
) : CommonServer {
    private lateinit var elastic: RestHighLevelClient

    override val log = logger()

    override fun start() {

        elastic = RestHighLevelClient(
            RestClient.builder(
                HttpHost(
                    elasticHostAndPort.host,
                    elasticHostAndPort.port,
                    "http"
                )
            )
        )

        val alertService = AlertingService(listOf(SlackNotifier(config.notifiers.slack?.hook!!)))

        GlobalScope.launch {
            try {
                log.info("Alert on clusterheath - starting up")
                ElasticAlerting(elastic, alertService).alertOnClusterHealth()
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                alertService.createAlert(Alert("WARNING: Alert on cluster health caught exception: ${ex.message}."))
                exitProcess(1)
            }
        }

        GlobalScope.launch {
            try {
                log.info("Alert on 500 statuscodes - starting up")
                ElasticAlerting(elastic, alertService).alertOnStatusCode(config)
            } catch (ex: Exception) {
                log.warn("WARNING: Alert on StatusCode caught exception: ${ex.message}.")
                alertService.createAlert(Alert("WARNING: Alert on 500 status' caught exception: ${ex.message}."))
                exitProcess(1)
            }
        }

        GlobalScope.launch {
            val client = RestClient.builder(HttpHost(elasticHostAndPort.host, elasticHostAndPort.port)).build()
            try {
                log.info("Alert on elastic storage - starting up with limits: " +
                        "low: ${config.limits?.storageInfoLimit ?: "NaN"}%, " +
                        "mid:${config.limits?.storageWarnLimit ?: "NaN"}%, " +
                        "high:${config.limits?.storageCriticalLimit ?: "NaN"}%"
                )
                ElasticAlerting(elastic, alertService).alertOnStorage(client, config)
            } catch (ex: Exception) {
                log.warn("WARNING: Alert on elastic storage caught exception: ${ex}.")
                alertService.createAlert(Alert("WARNING: Alert on cluster storage caught exception: ${ex.message}."))
                client.close()
            }
        }

        GlobalScope.launch {
            try {
                log.info("Alert on crashLoop started")
                KubernetesAlerting().crashLoopAndFailedDetection(alertService)
            } catch (ex: Exception) {
                log.warn("WARNING: Alert on crashLoop caught exception: ${ex}.")
                alertService.createAlert(Alert("WARNING: Alert on crash loop caught exception: ${ex.message}."))
                exitProcess(1)
            }
        }
    }
}
