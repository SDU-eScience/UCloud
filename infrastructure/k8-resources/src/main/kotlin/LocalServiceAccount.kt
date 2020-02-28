package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.rbac.*

/**
 * A resource for creating a service account with a bound role (local to namespace)
 */
class LocalServiceAccountResource(
    val name: String,
    val version: String
) : KubernetesResource {
    val serviceAccount = ServiceAccount().apply {
        metadata = ObjectMeta().apply {
            this.name = this@LocalServiceAccountResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }
    }

    val role = Role().apply {
        metadata = ObjectMeta().apply {
            this.name = this@LocalServiceAccountResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }

        rules = ArrayList()
    }

    val roleBinding = RoleBinding().apply {
        metadata = ObjectMeta().apply {
            this.name = this@LocalServiceAccountResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }

        subjects = arrayListOf(
            Subject().apply {
                kind = "ServiceAccount"
                name = this@LocalServiceAccountResource.name
                apiGroup = ""
            }
        )

        roleRef = RoleRef().apply {
            kind = "ClusterRole"
            name = this@LocalServiceAccountResource.name
            apiGroup = ""
        }
    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        return checkVersion(
            version,
            client.rbac().roles().inNamespace(resourceNamespace(role)).withName(name).get()?.metadata
        ) && checkVersion(
            version,
            client.rbac().roleBindings().inNamespace(resourceNamespace(role)).withName(name).get()?.metadata
        ) && checkVersion(
            version,
            client.serviceAccounts().inNamespace(resourceNamespace(role)).withName(name).get()?.metadata
        )
    }

    override fun DeploymentContext.create() {
        // The order of these matter
        client.serviceAccounts().inNamespace(resourceNamespace(role)).withName(name).createOrReplace(serviceAccount)
        client.rbac().roles().inNamespace(resourceNamespace(role)).withName(name).createOrReplace(role)
        client.rbac().roleBindings().inNamespace(resourceNamespace(role)).withName(name).createOrReplace(roleBinding)
    }

    override fun DeploymentContext.delete() {
        // Order of these might matter
        client.rbac().roleBindings().inNamespace(resourceNamespace(role)).withName(name).delete()
        client.rbac().clusterRoles().inNamespace(resourceNamespace(role)).withName(name).delete()
        client.serviceAccounts().inNamespace(resourceNamespace(role)).withName(name).delete()
    }

    override fun toString(): String = "ServiceAccount($name, $version)"
}

fun LocalServiceAccountResource.addRule(apiGroups: List<String>, resources: List<String>, verbs: List<String>) {
    role.rules.add(PolicyRule().apply {
        this.apiGroups = apiGroups
        this.resources = resources
        this.verbs = verbs
    })
}

fun MutableBundle.withLocalServiceAccount(
    name: String = this.name,
    version: String = this.version,
    init: LocalServiceAccountResource.() -> Unit
): LocalServiceAccountResource {
   return LocalServiceAccountResource(name, version).apply(init).also { resources.add(it) }
}
