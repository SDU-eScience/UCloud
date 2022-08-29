package dk.sdu.cloud.file.orchestrator

import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.file.orchestrator.api.FileOrchestratorServiceDescription
import dk.sdu.cloud.service.CommonServer

object FileOrchestratorService : Service {
    override val description = FileOrchestratorServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        micro.install(BackgroundScopeFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    FileOrchestratorService.runAsStandalone(args)
}
