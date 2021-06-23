package dk.sdu.cloud.file.synchronization

import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.file.synchronization.Server
import dk.sdu.cloud.file.synchronization.api.FileSynchronizationServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.CommonServer

data class LocalSyncthingDevice(
    val name: String = "UCloud",
    val hostname: String = "",
    val apiKey: String = "",
    val id: String = ""
)

data class SynchronizationConfiguration(
    val devices: List<LocalSyncthingDevice> = emptyList()
)

data class CephConfiguration(
    val cephfsBaseMount: String? = null,
    val subfolder: String = "",
    val useCephDirectoryStats: Boolean = false
)

object FileSynchronizationService : Service {
    override val description = FileSynchronizationServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        micro.install(BackgroundScopeFeature)

        val syncDevices = micro.configuration.requestChunkAtOrNull<List<LocalSyncthingDevice>>("syncthing", "devices") ?: emptyList()
        val syncConfig = micro.configuration.requestChunkAtOrNull("syncthing") ?: SynchronizationConfiguration(syncDevices)

        val cephConfig = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()

        return Server(micro, syncConfig, cephConfig)
    }
}

fun main(args: Array<String>) {
    FileSynchronizationService.runAsStandalone(args)
}
