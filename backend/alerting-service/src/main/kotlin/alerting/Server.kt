package dk.sdu.cloud.alerting

import dk.sdu.cloud.alerting.services.Alert
import dk.sdu.cloud.alerting.services.AlertingService
import dk.sdu.cloud.alerting.services.ElasticAlerting
import dk.sdu.cloud.alerting.services.KubernetesAlerting
import dk.sdu.cloud.alerting.services.NetworkTrafficAlerts
import dk.sdu.cloud.alerting.services.SlackNotifier
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.elasticLowLevelClient
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
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

        startServices(false)

        GlobalScope.launch {
            try {
                log.info("Alert on nodes - starting up")
                KubernetesAlerting().nodeStatus(alertService)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                alertService.createAlert(
                    Alert("WARNING: Alert on nodes caught exception: ${ex.stackTraceToString()}.")
                )
                exitProcess(1)
            }
        }

        GlobalScope.launch {
            try {
                log.info("Alert on shard docs - starting up")
                ElasticAlerting(elasticHighLevelClient, alertService).alertOnNumberOfDocs(elasticLowLevelClient)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                alertService.createAlert(
                    Alert("WARNING: Alert on shard docs  caught exception: ${ex.stackTraceToString()}.")
                )
                exitProcess(1)
            }
        }

        GlobalScope.launch {
            try {
                log.info("Alert on clusterhealth - starting up")
                ElasticAlerting(elasticHighLevelClient, alertService).alertOnClusterHealth()
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                alertService.createAlert(
                    Alert("WARNING: Alert on cluster health caught exception: ${ex.stackTraceToString()}.")
                )
                exitProcess(1)
            }
        }

        GlobalScope.launch {
            try {
                log.info("Alert on 500 statuscodes - starting up")
                NetworkTrafficAlerts(elasticHighLevelClient, alertService).alertOnStatusCode(config)
            } catch (ex: Exception) {
                log.warn("WARNING: Alert on StatusCode caught exception: ${ex.message}.")
                alertService.createAlert(
                    Alert("WARNING: Alert on 500 status' caught exception: ${ex.stackTraceToString()}.")
                )
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
                alertService.createAlert(
                    Alert("WARNING: Alert on cluster storage caught exception: ${ex.stackTraceToString()}.")
                )
                elasticLowLevelClient.close()
                exitProcess(1)
            }
        }

        GlobalScope.launch {
            try {
                log.info("Alert on crashLoop started")
                KubernetesAlerting().crashLoopAndFailedDetection(alertService)
            } catch (ex: Exception) {
                log.warn("WARNING: Alert on crashLoop caught exception: ${ex}.")
                alertService.createAlert(
                    Alert("WARNING: Alert on crash loop caught exception: ${ex.stackTraceToString()}.")
                )
                exitProcess(1)
            }
        }

        GlobalScope.launch {
            try {
                log.info("Alert on elastic indices count - starting up")
                ElasticAlerting(elasticHighLevelClient, alertService).alertOnIndicesCount(config)
            } catch (ex: Exception) {
                log.warn("WARNING: Alert on elastic indices count caught exception: ${ex}.")
                alertService.createAlert(
                    Alert("WARNING: Alert on elastic indices caught exception: ${ex.stackTraceToString()}.")
                )
                exitProcess(1)
            }
        }

        GlobalScope.launch {
            try {
                log.info("Alert on many 4xx through ambassador - starting up")
                NetworkTrafficAlerts(elasticHighLevelClient, alertService)
                    .ambassador4xxAlert(elasticHighLevelClient, config)
            } catch (ex: Exception) {
                log.warn("WARNING: Alert on many 4xx through ambassador caught exception: ${ex}.")
                alertService.createAlert(
                    Alert("WARNING: Alert on many 4xx through ambassador caught exception: ${ex.stackTraceToString()}.")
                )
                exitProcess(1)
            }
        }
    }
}
