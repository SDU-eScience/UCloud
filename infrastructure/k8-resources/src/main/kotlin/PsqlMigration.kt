package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.PodSpec
import io.fabric8.kubernetes.api.model.PodTemplateSpec
import io.fabric8.kubernetes.api.model.batch.Job
import io.fabric8.kubernetes.api.model.batch.JobSpec
import io.fabric8.kubernetes.client.KubernetesClient

class PsqlMigration(
    deployment: DeploymentResource,
    val name: String = deployment.name,
    val version: String = deployment.version
) : KubernetesResource {
    override val isMigration = true

    val job = Job().apply {
        metadata = ObjectMeta().apply {
            name = "${deployment.name}-migration"
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }

        spec = JobSpec().apply {
            template = PodTemplateSpec().apply {
                // Copy most stuff from the deployment
                metadata = ObjectMeta().apply {
                    name = "${deployment.name}-migration"
                }

                spec = PodSpec().apply {
                    containers = listOf(Container().apply {
                        volumeMounts = deployment.serviceContainer.volumeMounts
                        name = deployment.serviceContainer.name
                        image = deployment.serviceContainer.image
                        env = deployment.serviceContainer.env
                        command = deployment.serviceContainer.command + listOf("--run-script", "migrate-db")
                    })
                    volumes = deployment.deployment.spec.template.spec.volumes
                    imagePullSecrets = deployment.deployment.spec.template.spec.imagePullSecrets
                    restartPolicy = "Never"
                }
            }
        }
    }

    override fun isUpToDate(client: KubernetesClient, namespace: String): Boolean {
        val existingJob = client.batch().jobs().inNamespace(namespace).withName("$name-migration").get() ?: return false
        val k8Version = existingJob.metadata.annotations[UCLOUD_VERSION_ANNOTATION]
        return k8Version == version
    }

    override fun create(client: KubernetesClient, namespace: String) {
        client.batch().jobs().inNamespace(namespace).withName(name).createOrReplace(job)
    }

    override fun delete(client: KubernetesClient, namespace: String) {
        client.batch().jobs().inNamespace(namespace).withName(name).delete()
    }

    override fun toString(): String = "PsqlMigration($name, $version)"
}

fun MutableBundle.withPostgresMigration(
    deployment: DeploymentResource,
    name: String = deployment.name,
    version: String = deployment.version,
    init: PsqlMigration.() -> Unit = {}
): PsqlMigration {
    val psqlMigration = PsqlMigration(deployment, name, version).apply(init)
    resources.add(psqlMigration)
    return psqlMigration
}
