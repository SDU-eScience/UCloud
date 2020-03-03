package dk.sdu.cloud.file

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.BackgroundScopeFeature
import dk.sdu.cloud.micro.HealthCheckFeature
import dk.sdu.cloud.micro.LogFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.installDefaultFeatures
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.storage.api.StorageServiceDescription
import org.apache.logging.log4j.Level

val SERVICE_USER = "_${StorageServiceDescription.name}"

data class StorageConfiguration(
    val filePermissionAcl: Set<String> = emptySet()
)

data class CephConfiguration(
    val subfolder: String = ""
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        init(StorageServiceDescription, args)
        installDefaultFeatures()
        install(RefreshingJWTCloudFeature)
        install(BackgroundScopeFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    val folder = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()
    val config = micro.configuration.requestChunkAtOrNull("storage") ?: StorageConfiguration()


    micro.feature(LogFeature).configureLevels(
        mapOf(
            "com.github.jasync.sql.db" to Level.INFO
        )
    )

    Server(
        config,
        folder,
        micro
    ).start()
}
