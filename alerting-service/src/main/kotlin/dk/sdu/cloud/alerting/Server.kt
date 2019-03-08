package dk.sdu.cloud.alerting

import dk.sdu.cloud.alerting.services.Alert
import dk.sdu.cloud.alerting.services.AlertingService
import dk.sdu.cloud.alerting.services.ElasticAlerting
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
                exitProcess(1)
            }
        }
    }
}
