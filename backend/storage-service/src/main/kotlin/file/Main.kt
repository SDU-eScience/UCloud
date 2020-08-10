package dk.sdu.cloud.file

import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
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
    val category: String = "cephfs",
    val pricePerGb: Long = 0L,
    val provider: String = UCLOUD_PROVIDER,
    val defaultQuota: Long = NO_QUOTA
)

data class CephConfiguration(
    val cephfsBaseMount: String? = null,
    val subfolder: String = "",
    val useCephDirectoryStats: Boolean = false
)

object StorageService : Service {
    override val description = StorageServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        micro.install(BackgroundScopeFeature)
        val folder = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()
        val config = micro.configuration.requestChunkAtOrNull("storage") ?: StorageConfiguration()

        micro.feature(LogFeature).configureLevels(
            mapOf(
                "com.github.jasync.sql.db" to Level.INFO
            )
        )

        return Server(
            config,
            folder,
            micro
        )
    }
}

fun main(args: Array<String>) {
    StorageService.runAsStandalone(args)
}
