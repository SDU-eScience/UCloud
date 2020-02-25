package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServiceSpec
import io.fabric8.kubernetes.api.model.networking.IPBlock
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeer
import io.fabric8.kubernetes.client.KubernetesClient

/**
 * A resource for creating services
 */
class ServiceResource(val name: String, val version: String) : KubernetesResource {
    val service = Service().apply {
        metadata = ObjectMeta().apply {
            name = this@ServiceResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to this@ServiceResource.version)
        }

        spec = ServiceSpec()
    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        val existingJob =
            client.services().inNamespace(resourceNamespace(service)).withName(name).get() ?: return false
        val k8Version = existingJob.metadata.annotations[UCLOUD_VERSION_ANNOTATION]
        return k8Version == version
    }

    override fun DeploymentContext.create() {
        client.services().inNamespace(resourceNamespace(service)).withName(name).createOrReplace(service)
    }

    override fun DeploymentContext.delete() {
        client.network().networkPolicies().inNamespace(resourceNamespace(service)).withName(name).delete()
    }

    override fun toString(): String = "ServiceResource($name, $version)"
}

fun ResourceBundle.withService(
    name: String = this.name,
    version: String = this.version,
    init: ServiceResource.() -> Unit
): ServiceResource {
    val resource = ServiceResource(name, version).apply(init)
    resources.add(resource)
    return resource
}

