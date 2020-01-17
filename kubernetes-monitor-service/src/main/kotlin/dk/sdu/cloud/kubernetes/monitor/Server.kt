package dk.sdu.cloud.kubernetes.monitor

import dk.sdu.cloud.kubernetes.monitor.services.AlertingService
import dk.sdu.cloud.kubernetes.monitor.services.KubernetesLogChecker
import dk.sdu.cloud.kubernetes.monitor.services.SlackNotifier
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.startServices

class Server(
    private  val configuration: Configuration,
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        startServices(false)
        val alertingService = AlertingService(
            listOf(
                SlackNotifier(configuration.notifiers.slack.hook)
            )
        )
        val logCheck = KubernetesLogChecker(alertingService)
        logCheck.checkAndRestartFluentdLoggers()
    }

    override fun stop() {
        super.stop()
    }
}
