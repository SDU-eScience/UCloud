package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.PersistentVolume
import io.fabric8.kubernetes.api.model.PersistentVolumeSpec

class PersistentVolumeResource(val name: String, val version: String) : KubernetesResource {
    val resource = PersistentVolume().apply {
        metadata = ObjectMeta().apply {
            name = this@PersistentVolumeResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to this@PersistentVolumeResource.version)
        }

        spec = PersistentVolumeSpec()
    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        return client.persistentVolumes().withName(name).get() != null
    }

    override fun DeploymentContext.create() {
        client.persistentVolumes().createOrReplace(resource)
    }

    override fun DeploymentContext.delete() {
        client.persistentVolumes().withName(name).delete()
    }

    override fun toString(): String = "PV($name, $version)"
}

fun ResourceBundle.withPersistentVolume(
    name: String = this.name,
    version: String = this.version,
    init: PersistentVolumeResource.() -> Unit
): PersistentVolumeResource {
    val resource = PersistentVolumeResource(name, version).apply(init)
    resources.add(resource)
    return resource
}

