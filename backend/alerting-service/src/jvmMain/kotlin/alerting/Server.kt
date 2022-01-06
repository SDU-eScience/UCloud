package dk.sdu.cloud.alerting

import alerting.services.ScriptController
import dk.sdu.cloud.alerting.services.ElasticAlerting
import dk.sdu.cloud.alerting.services.KubernetesAlerting
import dk.sdu.cloud.alerting.services.NetworkTrafficAlerts
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.slack.api.SendAlertRequest
import dk.sdu.cloud.slack.api.SlackDescriptions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class Server(
    private val config: Configuration,
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        val authenticatedClient = micro.authenticator.authenticateClient(OutgoingHttpCall)

        val hasElastic = micro.featureOrNull(ElasticFeature) != null

        configureControllers(
            ScriptController(micro, authenticatedClient)
        )

        startServices(false)

        if (hasElastic) {
            val elasticHighLevelClient = micro.elasticHighLevelClient
            val elasticLowLevelClient = micro.elasticLowLevelClient

            GlobalScope.launch {
                try {
                    log.info("Alert on shard docs - starting up")
                    ElasticAlerting(elasticHighLevelClient, authenticatedClient).alertOnNumberOfDocs(
                        elasticLowLevelClient
                    )
                } catch (ex: Exception) {
                    log.warn(ex.stackTraceToString())
                    SlackDescriptions.sendAlert.call(
                        SendAlertRequest("WARNING: Alert on shard docs  caught exception: ${ex.stackTraceToString()}."),
                        authenticatedClient
                    ).orThrow()
                    exitProcess(1)
                }
            }

            GlobalScope.launch {
                try {
                    log.info("Alert on clusterhealth - starting up")
                    ElasticAlerting(elasticHighLevelClient, authenticatedClient).alertOnClusterHealth()
                } catch (ex: Exception) {
                    log.warn(ex.stackTraceToString())
                    SlackDescriptions.sendAlert.call(
                        SendAlertRequest("WARNING: Alert on cluster health caught exception: ${ex.stackTraceToString()}."),
                        authenticatedClient
                    ).orThrow()
                    exitProcess(1)
                }
            }

            GlobalScope.launch {
                try {
                    log.info("Alert on 500 statuscodes - starting up")
                    NetworkTrafficAlerts(elasticHighLevelClient, authenticatedClient).alertOnStatusCode(config)
                } catch (ex: Exception) {
                    log.warn("WARNING: Alert on StatusCode caught exception: ${ex.message}.")
                    SlackDescriptions.sendAlert.call(
                        SendAlertRequest("WARNING: Alert on 500 status' caught exception: ${ex.stackTraceToString()}."),
                        authenticatedClient
                    ).orThrow()
                    exitProcess(1)
                }
            }

            GlobalScope.launch {
                try {
                    log.info(
                        "Alert on elastic storage - starting up with limits: " +
                            "low: ${config.limits?.storageInfoLimit ?: "NaN"}%, " +
                            "mid:${config.limits?.storageWarnLimit ?: "NaN"}%, " +
                            "high:${config.limits?.storageCriticalLimit ?: "NaN"}%"
                    )
                    ElasticAlerting(elasticHighLevelClient, authenticatedClient).alertOnStorage(
                        elasticLowLevelClient,
                        config
                    )
                } catch (ex: Exception) {
                    log.warn("WARNING: Alert on elastic storage caught exception: ${ex}.")
                    SlackDescriptions.sendAlert.call(
                        SendAlertRequest("WARNING: Alert on cluster storage caught exception: ${ex.stackTraceToString()}."),
                        authenticatedClient
                    ).orThrow()
                    elasticLowLevelClient.close()
                    exitProcess(1)
                }
            }

            GlobalScope.launch {
                try {
                    log.info("Alert on elastic indices count - starting up")
                    ElasticAlerting(elasticHighLevelClient, authenticatedClient).alertOnIndicesCount(config)
                } catch (ex: Exception) {
                    log.warn("WARNING: Alert on elastic indices count caught exception: ${ex}.")
                    SlackDescriptions.sendAlert.call(
                        SendAlertRequest("WARNING: Alert on elastic indices caught exception: ${ex.stackTraceToString()}."),
                        authenticatedClient
                    ).orThrow()
                    exitProcess(1)
                }
            }

            GlobalScope.launch {
                try {
                    log.info("Alert on many 4xx and 5xx through ambassador - starting up")
                    NetworkTrafficAlerts(elasticHighLevelClient, authenticatedClient)
                        .ambassadorResponseAlert(elasticHighLevelClient, config)
                } catch (ex: Exception) {
                    log.warn("WARNING: Alert on many 4xx and 5xx through ambassador caught exception: ${ex}.")
                    SlackDescriptions.sendAlert.call(
                        SendAlertRequest("WARNING: Alert on many 4xx and 5xx through ambassador caught exception: ${ex.stackTraceToString()}."),
                        authenticatedClient
                    ).orThrow()
                    exitProcess(1)
                }
            }
        }

        if (!micro.developmentModeEnabled) {
            GlobalScope.launch {
                try {
                    log.info("Alert on nodes - starting up")
                    KubernetesAlerting(authenticatedClient).nodeStatus()
                } catch (ex: Exception) {
                    log.warn(ex.stackTraceToString())
                    SlackDescriptions.sendAlert.call(
                        SendAlertRequest("WARNING: Alert on nodes caught exception: ${ex.stackTraceToString()}."),
                        authenticatedClient
                    ).orThrow()
                    exitProcess(1)
                }
            }

            GlobalScope.launch {
                try {
                    log.info("Alert on crashLoop started")
                    KubernetesAlerting(authenticatedClient).crashLoopAndFailedDetection()
                } catch (ex: Exception) {
                    log.warn("WARNING: Alert on crashLoop caught exception: ${ex}.")
                    SlackDescriptions.sendAlert.call(
                        SendAlertRequest("WARNING: Alert on crash loop caught exception: ${ex.stackTraceToString()}."),
                        authenticatedClient
                    ).orThrow()
                    exitProcess(1)
                }
            }
        }
    }
}
