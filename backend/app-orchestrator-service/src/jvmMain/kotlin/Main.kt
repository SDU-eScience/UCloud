package dk.sdu.cloud.app.orchestrator

import dk.sdu.cloud.app.orchestrator.api.AppOrchestratorServiceDescription
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object AppOrchestratorService : Service {
    override val description = AppOrchestratorServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(BackgroundScopeFeature)
        micro.install(AuthenticatorFeature)

        return Server(micro)
    }
}

fun main(args: Array<String>) {
    AppOrchestratorService.runAsStandalone(args)
}
