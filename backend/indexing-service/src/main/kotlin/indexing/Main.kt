package dk.sdu.cloud.indexing

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.indexing.api.IndexingServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

data class CephConfiguration(
    val cephfsBaseMount: String? = null,
    val subfolder: String = "",
    val useCephDirectoryStats: Boolean = false
)

object IndexingService : Service {
    override val description = IndexingServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        micro.install(ElasticFeature)
        val cephConfig = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()
        return Server(micro, cephConfig)
    }
}

fun main(args: Array<String>) {
    IndexingService.runAsStandalone(args)
}
