package dk.sdu.cloud.file

import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.withFixedHost
import dk.sdu.cloud.file.api.NO_QUOTA
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.storage.api.StorageServiceDescription
import org.apache.logging.log4j.Level

val SERVICE_USER = "_${StorageServiceDescription.name}"

data class StorageConfiguration(
    val filePermissionAcl: Set<String> = emptySet(),
    val product: ProductConfiguration = ProductConfiguration()
)

data class ProductConfiguration(
    val id: String = "u1-cephfs",
    val category: String = "u1-cephfs",
    val provider: String = UCLOUD_PROVIDER,
    val defaultQuota: Long = 1024 * 1024 * 5
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

data class SynchronizationConfiguration(
    val devices: List<LocalSyncthingDevice> = emptyList()
)

object StorageService : Service {
    override val description = StorageServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        micro.install(BackgroundScopeFeature)
        val folder = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()
        val config = micro.configuration.requestChunkAtOrNull("storage") ?: StorageConfiguration()
        val syncDevices = micro.configuration.requestChunkAtOrNull<List<LocalSyncthingDevice>>("syncthing", "devices") ?: emptyList()
        val syncConfig = micro.configuration.requestChunkAtOrNull("syncthing") ?: SynchronizationConfiguration(syncDevices)


        /*
        micro.feature(LogFeature).configureLevels(
            mapOf(
                "com.github.jasync.sql.db" to Level.INFO
            )
        )
         */

        return Server(
            config,
            folder,
            micro,
            syncConfig
        )
    }
}

fun main(args: Array<String>) {
    StorageService.runAsStandalone(args)
}
