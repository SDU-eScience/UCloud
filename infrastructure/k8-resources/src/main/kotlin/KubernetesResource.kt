package dk.sdu.cloud.k8

import io.fabric8.kubernetes.client.KubernetesClient

data class DeploymentContext(
    val client: KubernetesClient,
    val namespace: String,
    val remainingArgs: List<String>,
    val environment: Environment
)

interface KubernetesResource {
    val phase: DeploymentPhase get() = DeploymentPhase.DEPLOY
    fun DeploymentContext.isUpToDate(): Boolean
    fun DeploymentContext.create()
    fun DeploymentContext.delete()
}

const val UCLOUD_VERSION_ANNOTATION = "ucloud.dk/res_version"
