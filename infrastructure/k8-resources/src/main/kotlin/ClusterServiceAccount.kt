package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.rbac.ClusterRole
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding
import io.fabric8.kubernetes.api.model.rbac.PolicyRule
import io.fabric8.kubernetes.api.model.rbac.RoleRef
import io.fabric8.kubernetes.api.model.rbac.Subject

/**
 * A resource for creating a service account with a bound cluster role
 */
class ClusterServiceAccountResource(
    val name: String,
    val version: String
) : KubernetesResource {
    val serviceAccount = ServiceAccount().apply {
        metadata = ObjectMeta().apply {
            this.name = this@ClusterServiceAccountResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }
    }

    val clusterRole = ClusterRole().apply {
        metadata = ObjectMeta().apply {
            this.name = this@ClusterServiceAccountResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }

        rules = ArrayList()
    }

    val roleBinding = ClusterRoleBinding().apply {
        metadata = ObjectMeta().apply {
            this.name = this@ClusterServiceAccountResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }

        subjects = arrayListOf(
            Subject().apply {
                kind = "ServiceAccount"
                name = this@ClusterServiceAccountResource.name
                namespace = "default"
                apiGroup = ""
            }
        )

        roleRef = RoleRef().apply {
            kind = "ClusterRole"
            name = this@ClusterServiceAccountResource.name
            apiGroup = ""
        }
    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        return checkVersion(
            version,
            client.rbac().clusterRoles().inNamespace(resourceNamespace(clusterRole)).withName(name).get()?.metadata
        ) && checkVersion(
            version,
            client.rbac().clusterRoleBindings().inNamespace(resourceNamespace(clusterRole)).withName(name).get()?.metadata
        ) && checkVersion(
            version,
            client.serviceAccounts().inNamespace(resourceNamespace(clusterRole)).withName(name).get()?.metadata
        )
    }

    override fun DeploymentContext.create() {
        // The order of these matter
        client.serviceAccounts().inNamespace(resourceNamespace(clusterRole)).withName(name)
            .createOrReplace(serviceAccount)
        client.rbac().clusterRoles().inNamespace(resourceNamespace(clusterRole)).withName(name)
            .createOrReplace(clusterRole)
        client.rbac().clusterRoleBindings().inNamespace(resourceNamespace(clusterRole)).withName(name)
            .createOrReplace(roleBinding)
    }

    override fun DeploymentContext.delete() {
        // Order of these might matter
        client.rbac().roleBindings().inNamespace(resourceNamespace(clusterRole)).withName(name).delete()
        client.rbac().clusterRoles().inNamespace(resourceNamespace(clusterRole)).withName(name).delete()
        client.serviceAccounts().inNamespace(resourceNamespace(clusterRole)).withName(name).delete()
    }

    override fun toString(): String = "ClusterServiceAccount($name, $version)"
}

fun ClusterServiceAccountResource.addRule(apiGroups: List<String>, resources: List<String>, verbs: List<String>) {
    clusterRole.rules.add(PolicyRule().apply {
        this.apiGroups = apiGroups
        this.resources = resources
        this.verbs = verbs
    })
}

fun MutableBundle.withClusterServiceAccount(
    name: String = this.name,
    version: String = this.version,
    init: ClusterServiceAccountResource.() -> Unit
): ClusterServiceAccountResource {
    return ClusterServiceAccountResource(name, version).apply(init).also { resources.add(it) }
}
