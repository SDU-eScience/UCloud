package dk.sdu.cloud.file

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.calls.server.FrontendOverrides
import dk.sdu.cloud.micro.BackgroundScopeFeature
import dk.sdu.cloud.micro.ClientFeature
import dk.sdu.cloud.micro.ConfigurationFeature
import dk.sdu.cloud.micro.DatabaseConfigurationFeature
import dk.sdu.cloud.micro.DeinitFeature
import dk.sdu.cloud.micro.DevelopmentOverrides
import dk.sdu.cloud.micro.FlywayFeature
import dk.sdu.cloud.micro.HealthCheckFeature
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.KtorServerProviderFeature
import dk.sdu.cloud.micro.LogFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.RedisFeature
import dk.sdu.cloud.micro.ScriptFeature
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.ServiceDiscoveryOverrides
import dk.sdu.cloud.micro.ServiceInstanceFeature
import dk.sdu.cloud.micro.TokenValidationFeature
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.installDefaultFeatures
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.service.DistributedLockBestEffortFactory
import dk.sdu.cloud.storage.api.StorageServiceDescription
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

val SERVICE_USER = "_${StorageServiceDescription.name}"

data class StorageConfiguration(
    val filePermissionAcl: Set<String> = emptySet()
)

data class CephConfiguration(
    val subfolder: String = ""
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        if (args.contains("--workspace-queue")) {
            init(StorageServiceDescription, args)
            install(DeinitFeature)
            install(ScriptFeature)
            install(ConfigurationFeature)
            install(ServiceDiscoveryOverrides)
            install(ServiceInstanceFeature)
            install(DevelopmentOverrides)
            install(LogFeature)
            install(KtorServerProviderFeature)
            install(RedisFeature)
            install(ClientFeature)
            install(TokenValidationFeature)
            install(FrontendOverrides)
            install(DatabaseConfigurationFeature)
            install(FlywayFeature)
            install(HibernateFeature)
            install(RefreshingJWTCloudFeature)
            install(BackgroundScopeFeature)
            install(HealthCheckFeature)
        } else {
            init(StorageServiceDescription, args)
            installDefaultFeatures()
            install(HibernateFeature)
            install(RefreshingJWTCloudFeature)
            install(BackgroundScopeFeature)
            install(HealthCheckFeature)
        }
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
