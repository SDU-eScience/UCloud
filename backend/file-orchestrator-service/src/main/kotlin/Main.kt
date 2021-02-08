package dk.sdu.cloud.file.orchestrator

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.file.orchestrator.api.FileOrchestratorServiceDescription

object FileOrchestratorService : Service {
    override val description = FileOrchestratorServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    FileOrchestratorService.runAsStandalone(args)
}
