package dk.sdu.cloud.kubernetes.monitor

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.kubernetes.monitor.services.KubernetesLogChecker
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.startServices

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    val authenticatedClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
    override fun start() {
        startServices(false)

        val logCheck = KubernetesLogChecker(authenticatedClient)
        logCheck.checkAndRestartFluentdLoggers()
    }

    override fun stop() {
        super.stop()
    }
}
