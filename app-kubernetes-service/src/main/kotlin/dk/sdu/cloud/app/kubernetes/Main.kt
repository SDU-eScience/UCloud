package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.kubernetes.api.AppKubernetesServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.BackgroundScopeFeature
import dk.sdu.cloud.micro.HealthCheckFeature
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler

data class TolerationKeyAndValue(val key: String, val value: String)

data class Configuration(
    val cookieName: String = "appRefreshToken",
    val prefix: String = "app-",
    val domain: String = "cloud.sdu.dk",
    val performAuthentication: Boolean = true,
    val toleration: TolerationKeyAndValue? = null,
    val hostTemporaryStorage: String = "/mnt/ofs"
)

data class CephConfiguration(
    val subfolder: String = ""
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AppKubernetesServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
        install(BackgroundScopeFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    val configuration = micro.configuration.requestChunkAtOrNull("app", "kubernetes") ?: Configuration()
    val cephConfig = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()

    Server(micro, configuration, cephConfig).start()
}
