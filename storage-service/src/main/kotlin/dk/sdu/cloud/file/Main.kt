package dk.sdu.cloud.file

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.BackgroundScopeFeature
import dk.sdu.cloud.micro.HealthCheckFeature
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.installDefaultFeatures
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.storage.api.StorageServiceDescription

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
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
        install(BackgroundScopeFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    val folder = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()
    val config = micro.configuration.requestChunkAtOrNull("storage") ?: StorageConfiguration()

    Server(
        config,
        folder,
        micro
    ).start()
}
