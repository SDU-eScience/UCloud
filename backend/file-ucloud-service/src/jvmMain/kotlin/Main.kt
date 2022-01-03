package dk.sdu.cloud.file.ucloud

import dk.sdu.cloud.auth.api.AuthenticatorFeature
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


object FileUcloudService : Service {
    override val description = FileUcloudServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        micro.install(BackgroundScopeFeature)
        micro.install(ElasticFeature)

        val configuration = micro.configuration.requestChunkAtOrNull("files", "ucloud") ?: Configuration()
        val cephConfig = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()

        if (micro.configuration.requestChunkAtOrNull<Boolean>("postInstalling") == true) {
            return EmptyServer
        }

        return Server(micro, configuration, cephConfig)
    }
}

fun main(args: Array<String>) {
    FileUcloudService.runAsStandalone(args)
}
