package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.rbac.*
import io.fabric8.kubernetes.client.KubernetesClient

class ServiceAccountResource(
    val name: String,
    val version: String
) : KubernetesResource {
    val serviceAccount = ServiceAccount().apply {
        metadata = ObjectMeta().apply {
            this.name = this@ServiceAccountResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }
    }

    val clusterRole = ClusterRole().apply {
        metadata = ObjectMeta().apply {
            this.name = this@ServiceAccountResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }

        rules = ArrayList()
    }

    val roleBinding = RoleBinding().apply {
        metadata = ObjectMeta().apply {
            this.name = this@ServiceAccountResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }

        subjects = arrayListOf(
            Subject().apply {
                kind = "ServiceAccount"
                name = this@ServiceAccountResource.name
                apiGroup = ""
            }
        )

        roleRef = RoleRef().apply {
            kind = "ClusterRole"
            name = this@ServiceAccountResource.name
            apiGroup = ""
        }
    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        return checkVersion(
            version,
            client.rbac().clusterRoles().inNamespace(namespace).withName(name).get()?.metadata
        ) && checkVersion(
            version,
            client.rbac().roleBindings().inNamespace(namespace).withName(name).get()?.metadata
        ) && checkVersion(
            version,
            client.serviceAccounts().inNamespace(namespace).withName(name).get()?.metadata
        )
    }

    override fun DeploymentContext.create() {
        // The order of these matter
        client.serviceAccounts().inNamespace(namespace).withName(name).createOrReplace(serviceAccount)
        client.rbac().clusterRoles().inNamespace(namespace).withName(name).createOrReplace(clusterRole)
        client.rbac().roleBindings().inNamespace(namespace).withName(name).createOrReplace(roleBinding)
    }

    override fun DeploymentContext.delete() {
        // Order of these might matter
        client.rbac().roleBindings().inNamespace(namespace).withName(name).delete()
        client.rbac().clusterRoles().inNamespace(namespace).withName(name).delete()
        client.serviceAccounts().inNamespace(namespace).withName(name).delete()
    }

    override fun toString(): String = "ServiceAccount($name, $version)"
}

fun ServiceAccountResource.addRule(apiGroups: List<String>, resources: List<String>, verbs: List<String>) {
    clusterRole.rules.add(PolicyRule().apply {
        this.apiGroups = apiGroups
        this.resources = resources
        this.verbs = verbs
    })
}

fun MutableBundle.withServiceAccount(
    name: String = this.name,
    version: String = this.version,
    init: ServiceAccountResource.() -> Unit
): ServiceAccountResource {
   return ServiceAccountResource(name, version).apply(init).also { resources.add(it) }
}
