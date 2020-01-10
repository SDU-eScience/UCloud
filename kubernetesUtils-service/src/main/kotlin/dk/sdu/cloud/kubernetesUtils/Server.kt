package dk.sdu.cloud.kubernetesUtils

import dk.sdu.cloud.kubernetesUtils.services.AlertingService
import dk.sdu.cloud.kubernetesUtils.services.KubernetesLogChecker
import dk.sdu.cloud.kubernetesUtils.services.SlackNotifier
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*

class Server(
    private  val configuration: Configuration,
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        startServices(false)
        val alertingService = AlertingService(listOf(SlackNotifier(configuration.notifiers.slack?.hook!!)))
        val logCheck = KubernetesLogChecker(alertingService)
        logCheck.checkAndRestartFluentdLoggers()
    }

    override fun stop() {
        super.stop()
    }
}
