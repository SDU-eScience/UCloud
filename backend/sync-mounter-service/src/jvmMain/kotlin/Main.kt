package dk.sdu.cloud.sync.mounter

import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.sync.mounter.api.SyncMounterServiceDescription

data class SyncMounterConfiguration(
    val cephfsBaseMount: String = "/mnt/cephfs",
    val syncBaseMount: String = "/mnt/sync",
    val deviceId: String
)

object SyncMounterService : Service {
    override val description = SyncMounterServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        val sharedSecret = micro.configuration.requestChunkAtOrNull<String>("syncthing", "sharedSecret")
        val config = micro.configuration.requestChunkAt<SyncMounterConfiguration>("syncMount")
        val normalizedConfig = config.copy(
            cephfsBaseMount = if (config.cephfsBaseMount.endsWith("/")) {
                    config.cephfsBaseMount
                } else {
                    config.cephfsBaseMount + "/"
                },
            syncBaseMount = if (config.syncBaseMount.endsWith("/")) {
                config.syncBaseMount
            } else {
                config.syncBaseMount + "/"
            }
        )

        return Server(micro, normalizedConfig, sharedSecret)
    }
}

fun main(args: Array<String>) {
    SyncMounterService.runAsStandalone(args)
}
