package dk.sdu.cloud.sync.mounter

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.sync.mounter.api.SyncMounterServiceDescription

object SyncMounterService : Service {
    override val description = SyncMounterServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    SyncMounterService.runAsStandalone(args)
}
