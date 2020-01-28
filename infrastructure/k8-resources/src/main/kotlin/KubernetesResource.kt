package dk.sdu.cloud.k8

import io.fabric8.kubernetes.client.KubernetesClient

interface KubernetesResource {
    val isMigration: Boolean get() = false
    fun isUpToDate(client: KubernetesClient, namespace: String): Boolean
    fun create(client: KubernetesClient, namespace: String)
    fun delete(client: KubernetesClient, namespace: String)
}

const val UCLOUD_VERSION_ANNOTATION = "ucloud.dk/res_version"
