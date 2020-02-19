package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Secret

class SecretResource(
    private val name: String,
    private val version: String
) : KubernetesResource {
    var onCreate: (DeploymentContext.() -> Unit)? = null

    val secret = Secret().apply {
        metadata = ObjectMeta().apply {
            this.name = this@SecretResource.name
            labels = mapOf("app" to this@SecretResource.name)
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }

        stringData = HashMap()
    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        val existing =
            client.secrets().inNamespace(resourceNamespace(secret)).withName(name).get()?.metadata ?: return false
        return checkVersion(version, existing)
    }

    override fun DeploymentContext.create() {
        onCreate?.invoke(this)
        client.secrets().inNamespace(resourceNamespace(secret)).createOrReplace(secret)
    }

    override fun DeploymentContext.delete() {
        client.secrets().inNamespace(resourceNamespace(secret)).delete(secret)
    }

    override fun toString(): String = "SecretResource($name, $version)"
}

fun MutableBundle.withSecret(
    name: String = this.name,
    version: String = this.version,
    namespace: String? = null,
    onCreate: SecretResource.() -> Unit
): SecretResource {
    return SecretResource(name, version)
        .apply {
            this.onCreate = { onCreate() }
            if (namespace != null) {
                secret.metadata.namespace = namespace
            }
            resources.add(this)
        }
}
