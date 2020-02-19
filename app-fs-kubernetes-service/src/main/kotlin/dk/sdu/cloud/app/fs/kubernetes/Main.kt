package dk.sdu.cloud.app.fs.kubernetes

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.app.fs.kubernetes.api.AppFsKubernetesServiceDescription
import dk.sdu.cloud.micro.*

data class CephConfiguration(
    val subfolder: String = ""
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AppFsKubernetesServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    val folder = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()
    Server(micro, folder).start()
}
