package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.kubernetes.api.AppKubernetesServiceDescription
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EmptyServer
import dk.sdu.cloud.service.Loggable

data class TolerationKeyAndValue(val key: String, val value: String)

data class ProductReferenceWithoutProvider(
    val id: String,
    val category: String,
)

data class Configuration(
    val providerId: String = "ucloud",
    val cookieName: String = "appRefreshToken",
    val prefix: String = "app-",
    val domain: String = "cloud.sdu.dk",
    val toleration: TolerationKeyAndValue? = null,
    val kubernetesConfig: String? = null,
    val disableMasterElection: Boolean = false,
    val useSmallReservation: Boolean = false,
    val networkInterface: String? = null,
    val networkGatewayCidr: String? = null,
    val providerRefreshToken: String? = null,
    val ucloudCertificate: String? = null,
    val enabled: Boolean = true,
    val useMachineSelector: Boolean? = null,
    val nodes: NodeConfiguration? = null,
    val ingress: Ingress = Ingress(),
    val networkIp: NetworkIP = NetworkIP(),
    val categoryToNodeSelector: Map<String, String> = emptyMap()
) {
    data class Ingress(
        val enabled: Boolean = true,
        val product: ProductReferenceWithoutProvider = ProductReferenceWithoutProvider(
            "u1-publiclink",
            "u1-publiclink"
        ),
    )

    data class NetworkIP(
        val enabled: Boolean = true,
        val product: ProductReferenceWithoutProvider = ProductReferenceWithoutProvider(
            "public-ip",
            "public-ip"
        ),
    )
}

data class NodeConfiguration(
    val systemReservedCpuMillis: Int,
    val systemReservedMemMegabytes: Int,
    val types: Map<String, NodeType>
)

data class NodeType(
    val cpuMillis: Int,
    val memMegabytes: Int,
    val gpus: Int,
)

data class CephConfiguration(
    val cephfsBaseMount: String? = null,
    val subfolder: String = ""
)

object AppKubernetesService : Service {
    override val description = AppKubernetesServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        micro.install(BackgroundScopeFeature)
        val configuration = micro.configuration.requestChunkAtOrNull("app", "kubernetes") ?: Configuration()
        val cephConfig = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()

        if (micro.configuration.requestChunkAtOrNull<Boolean>("postInstalling") == true) {
            return EmptyServer
        }

        return Server(micro, configuration, cephConfig)
    }
}

fun main(args: Array<String>) {
    AppKubernetesService.runAsStandalone(args)
}
