package dk.sdu.cloud.alerting

import dk.sdu.cloud.alerting.services.Alert
import dk.sdu.cloud.alerting.services.AlertingService
import dk.sdu.cloud.alerting.services.ElasticAlerting
import dk.sdu.cloud.alerting.services.KubernetesAlerting
import dk.sdu.cloud.alerting.services.SlackNotifier
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.elasticLowLevelClient
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.stackTraceToString
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class Server(
    private val config: Configuration,
    override val micro: Micro
) : CommonServer {

    override val log = logger()

    override fun start() {

        val elasticHighLevelClient = micro.elasticHighLevelClient
        val elasticLowLevelClient = micro.elasticLowLevelClient
        val alertService = AlertingService(listOf(SlackNotifier(config.notifiers.slack?.hook!!)))

        GlobalScope.launch {
            try {
                log.info("Alert on clusterheath - starting up")
                ElasticAlerting(elasticHighLevelClient, alertService).alertOnClusterHealth()
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                alertService.createAlert(Alert("WARNING: Alert on cluster health caught exception: ${ex.message}."))
                exitProcess(1)
            }
        }

        GlobalScope.launch {
            try {
                log.info("Alert on 500 statuscodes - starting up")
                ElasticAlerting(elasticHighLevelClient, alertService).alertOnStatusCode(config)
            } catch (ex: Exception) {
                log.warn("WARNING: Alert on StatusCode caught exception: ${ex.message}.")
                alertService.createAlert(Alert("WARNING: Alert on 500 status' caught exception: ${ex.message}."))
                exitProcess(1)
            }
        }

        GlobalScope.launch {
            try {
                log.info("Alert on elastic storage - starting up with limits: " +
                        "low: ${config.limits?.storageInfoLimit ?: "NaN"}%, " +
                        "mid:${config.limits?.storageWarnLimit ?: "NaN"}%, " +
                        "high:${config.limits?.storageCriticalLimit ?: "NaN"}%"
                )
                ElasticAlerting(elasticHighLevelClient, alertService).alertOnStorage(elasticLowLevelClient, config)
            } catch (ex: Exception) {
                log.warn("WARNING: Alert on elastic storage caught exception: ${ex}.")
                alertService.createAlert(Alert("WARNING: Alert on cluster storage caught exception: ${ex.message}."))
                elasticLowLevelClient.close()
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
