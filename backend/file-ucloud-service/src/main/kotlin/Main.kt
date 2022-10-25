package dk.sdu.cloud.file.ucloud

import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.withFixedHost
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.file.ucloud.api.FileUcloudServiceDescription
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EmptyServer

data class Configuration(
    val enabled: Boolean = true,
    val providerId: String = "ucloud",
    val providerRefreshToken: String? = null,
    val ucloudCertificate: String? = null,
    val productCategory: String = "u1-cephfs",
    val accounting: Accounting = Accounting(),
    val indexing: Indexing = Indexing(),
) {
    data class Accounting(
        val enabled: Boolean = true
    )

    data class Indexing(
        val enabled: Boolean = true
    )
}

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

