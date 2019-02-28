package dk.sdu.cloud.alerting

import dk.sdu.cloud.alerting.services.Alert
import dk.sdu.cloud.alerting.services.AlertingService
import dk.sdu.cloud.alerting.services.ElasticAlerting
import dk.sdu.cloud.alerting.services.SlackNotifier
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.CommonServer
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

        /*GlobalScope.launch {
            try {
                ElasticAlerting(elastic, alertService).alertOnClusterHealth()
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                alertService.createAlert(Alert("WARNING: Alert on cluster health caught exception: ${ex.message}."))
                exitProcess(1)
            }
        }*/
/*
        GlobalScope.launch {
            try {
                ElasticAlerting(elastic, alertService).alertOnStatusCode()
            } catch (ex: Exception) {
                log.warn("WARNING: Alert on StatusCode caught exception: ${ex.message}.")
                exitProcess(1)
            }
        }*/

        GlobalScope.launch {
            try {
                ElasticAlerting(elastic, alertService).storageAlert()
            } catch (ex: Exception) {
                log.warn("WARNING: Alert on liveness check caught exception: ${ex.message}.")
                exitProcess(1)
            }
        }
    }
}
