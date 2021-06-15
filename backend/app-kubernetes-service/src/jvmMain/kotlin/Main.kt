package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.kubernetes.api.AppKubernetesServiceDescription
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

data class TolerationKeyAndValue(val key: String, val value: String)

data class Configuration(
    val cookieName: String = "appRefreshToken",
    val prefix: String = "app-",
    val domain: String = "cloud.sdu.dk",
    val performAuthentication: Boolean = true,
    val toleration: TolerationKeyAndValue? = null,
    val reloadableK8Config: String? = null,
    val disableMasterElection: Boolean = false,
    val fullScanFrequency: Long = 1000 * 60 * 15L,
    val useSmallReservation: Boolean = false,
    val networkInterface: String? = null,
    val networkGatewayCidr: String? = null,
    val providerRefreshToken: String? = null,
    val ucloudCertificate: String? = null,
    val useMachineSelector: Boolean? = null,
    val nodes: NodeConfiguration? = null,
)

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
    val subfolder: String = ""
)

object AppKubernetesService : Service {
    override val description = AppKubernetesServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        micro.install(BackgroundScopeFeature)
        val configuration = micro.configuration.requestChunkAtOrNull("app", "kubernetes") ?: Configuration()
        val cephConfig = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()

        if (configuration.networkInterface == null) {
            println("No 'networkInterface' has been configured. Public IPs will not work!")
            println("Expected a string config at 'app/kubernetes/networkInterface'.")
        }

        return Server(micro, configuration, cephConfig)
    }
}

fun main(args: Array<String>) {
    AppKubernetesService.runAsStandalone(args)
}
