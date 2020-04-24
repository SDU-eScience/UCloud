package dk.sdu.cloud.indexing

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.indexing.api.IndexingServiceDescription
import dk.sdu.cloud.micro.ElasticFeature
import dk.sdu.cloud.micro.HealthCheckFeature
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler

data class CephConfiguration(
    val subfolder: String = "",
    val useCephDirectoryStats: Boolean = false
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(IndexingServiceDescription, args)
        install(RefreshingJWTCloudFeature)
        install(ElasticFeature)
        install(HibernateFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    val cephConfig = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()
    Server(micro, cephConfig).start()
}
