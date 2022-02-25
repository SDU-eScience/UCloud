package dk.sdu.cloud.sync.mounter

import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EmptyServer
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.sync.mounter.api.SyncMounterServiceDescription

data class SyncMounterConfiguration(
    val cephfsBaseMount: String = "/mnt/cephfs",
    val syncBaseMount: String = "/mnt/sync",
    val deviceId: String
)

object SyncMounterService : Service, Loggable {
    override val description = SyncMounterServiceDescription
    override val log = logger()
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        val providerId = micro.configuration.requestChunkAtOrNull<String>("files", "ucloud", "providerId")
            ?: "ucloud"
        val sharedSecret = micro.configuration.requestChunkAtOrNull<String>("syncthing", "sharedSecret")
        val config = micro.configuration.requestChunkAtOrNull<SyncMounterConfiguration>("syncMount")
        if (config == null) {
            log.info("No configuration for sync-mounter found at 'syncMount'. Syncthing support is disabled.")
            return EmptyServer
        }
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

        return Server(micro, normalizedConfig, sharedSecret, providerId)
    }
}

fun main(args: Array<String>) {
    SyncMounterService.runAsStandalone(args)
}
