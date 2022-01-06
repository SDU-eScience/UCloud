package dk.sdu.cloud.file.ucloud

import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.withFixedHost
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.file.ucloud.api.FileUcloudServiceDescription
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EmptyServer

data class Configuration(
    val providerRefreshToken: String? = null,
    val ucloudCertificate: String? = null,
)

data class CephConfiguration(
    val cephfsBaseMount: String? = null,
    val subfolder: String = "",
    val useCephDirectoryStats: Boolean = false
)

data class LocalSyncthingDevice(
    val name: String = "UCloud",
    val hostname: String = "",
    val apiKey: String = "",
    val id: String = "",
    val port: Int = 80,
    val username: String = "",
    val password: String = "",
    val rescanIntervalSeconds: Int = 3600,
    val doNotChangeHostNameForMounter: Boolean = false,
)

fun AuthenticatedClient.withMounterInfo(device: LocalSyncthingDevice): AuthenticatedClient {
    return if (device.doNotChangeHostNameForMounter) {
        this
    } else {
        withFixedHost(HostInfo(device.hostname, port = 8080))
    }
}

data class SyncConfiguration(
    val devices: List<LocalSyncthingDevice> = emptyList()
)

object FileUcloudService : Service {
    override val description = FileUcloudServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        micro.install(BackgroundScopeFeature)
        micro.install(ElasticFeature)

        val sharedSecret = micro.configuration.requestChunkAtOrNull<String>("syncthing", "sharedSecret")
        val configuration = micro.configuration.requestChunkAtOrNull("files", "ucloud") ?: Configuration()
        val cephConfig = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()
        val syncDevices = micro.configuration.requestChunkAtOrNull<List<LocalSyncthingDevice>>("syncthing", "devices") ?: emptyList()
        val syncConfig = micro.configuration.requestChunkAtOrNull("syncthing") ?: SyncConfiguration(syncDevices)

        if (micro.configuration.requestChunkAtOrNull<Boolean>("postInstalling") == true) {
            return EmptyServer
        }

        return Server(micro, configuration, cephConfig, syncConfig, sharedSecret)
    }
}

fun main(args: Array<String>) {
    FileUcloudService.runAsStandalone(args)
}
