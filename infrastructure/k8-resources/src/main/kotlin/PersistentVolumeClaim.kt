package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec

class PersistentVolumeClaimResource(val name: String, val version: String) : KubernetesResource {
    val resource = PersistentVolumeClaim().apply {
        metadata = ObjectMeta().apply {
            name = this@PersistentVolumeClaimResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to this@PersistentVolumeClaimResource.version)
        }

        spec = PersistentVolumeClaimSpec()
    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        return client.persistentVolumeClaims().inNamespace(resourceNamespace(resource)).withName(name).get() != null
    }

    override fun DeploymentContext.create() {
        client.persistentVolumeClaims().inNamespace(resourceNamespace(resource)).createOrReplace(resource)
    }

    override fun DeploymentContext.delete() {
        client.persistentVolumeClaims().inNamespace(resourceNamespace(resource)).withName(name).delete()
    }

    override fun toString(): String = "PVC($name, $version)"
}

fun ResourceBundle.withPersistentVolumeClaim(
    name: String = this.name,
    version: String = this.version,
    init: PersistentVolumeClaimResource.() -> Unit
): PersistentVolumeClaimResource {
    val resource = PersistentVolumeClaimResource(name, version).apply(init)
    resources.add(resource)
    return resource
}

