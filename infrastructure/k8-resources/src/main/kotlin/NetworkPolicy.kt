package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.networking.IPBlock
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeer
import io.fabric8.kubernetes.client.KubernetesClient

class NetworkPolicyResource(val name: String, val version: String) : KubernetesResource {
    val policy = NetworkPolicy().apply {
        metadata = ObjectMeta().apply {
            name = this@NetworkPolicyResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to this@NetworkPolicyResource.version)
        }
    }

    override fun isUpToDate(client: KubernetesClient, namespace: String): Boolean {
        val existingJob =
            client.network().networkPolicies().inNamespace(namespace).withName(name).get() ?: return false
        val k8Version = existingJob.metadata.annotations[UCLOUD_VERSION_ANNOTATION]
        return k8Version == version
    }

    override fun create(client: KubernetesClient, namespace: String) {
        client.network().networkPolicies().inNamespace(namespace).withName(name).createOrReplace(policy)
    }

    override fun delete(client: KubernetesClient, namespace: String) {
        client.network().networkPolicies().inNamespace(namespace).withName(name).delete()
    }
}

fun MutableBundle.withNetworkPolicy(
    name: String = this.name,
    version: String = this.version,
    init: NetworkPolicyResource.() -> Unit
): NetworkPolicyResource {
    val resource = NetworkPolicyResource(name, version).apply(init)
    resources.add(resource)
    return resource
}

data class PortAndProtocol(val port: Int, val protocol: NetworkProtocol)
enum class NetworkProtocol {
    TCP,
    UDP
}

fun allowPortEgress(portAndProtocols: List<PortAndProtocol>): NetworkPolicyEgressRule {
    return NetworkPolicyEgressRule().apply {
        ports = portAndProtocols.map { (port, protocol) ->
            NetworkPolicyPort().apply {
                this.port = IntOrString(port)
                this.protocol = protocol.name
            }
        }
    }
}

data class EgressToPolicy(val ipCidr: String, val exceptionCidrs: List<String>? = null)

fun allowEgressTo(toPolicies: List<EgressToPolicy>): NetworkPolicyEgressRule {
    return NetworkPolicyEgressRule().apply {
        to = toPolicies.map {
            NetworkPolicyPeer().apply {
                ipBlock = IPBlock(it.ipCidr, it.exceptionCidrs)
            }
        }
    }
}
