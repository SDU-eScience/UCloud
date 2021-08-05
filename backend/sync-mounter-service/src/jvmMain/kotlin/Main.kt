package dk.sdu.cloud.sync.mounter

import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.sync.mounter.api.SyncMounterServiceDescription

data class SyncMounterConfiguration(
    val cephfsBaseMount: String? = null,
    val syncBaseMount: String? = null,
    val deviceId: String? = null
)

object SyncMounterService : Service {
    override val description = SyncMounterServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        val config = micro.configuration.requestChunkAtOrNull("syncMount") ?: SyncMounterConfiguration()
        return Server(micro, config)
    }
}

fun main(args: Array<String>) {
    SyncMounterService.runAsStandalone(args)
}
