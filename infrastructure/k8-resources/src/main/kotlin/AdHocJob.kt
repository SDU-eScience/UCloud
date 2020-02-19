package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.PodSpec
import io.fabric8.kubernetes.api.model.PodTemplateSpec
import io.fabric8.kubernetes.api.model.batch.Job
import io.fabric8.kubernetes.api.model.batch.JobSpec

/**
 * An ad hob job which can be run on demand.
 *
 * This type of resource is useful for running small jobs on demand. Additional arguments can be passed via the CLI.
 *
 * Common example of ad hoc jobs:
 *
 * - Run a scan-type job now
 * - Gather one-time statistics
 */
open class AdHocJob(
    deployment: DeploymentResource,
    private val additionalArgs: DeploymentContext.() -> List<String>,
    private val nameSuffix: String,
    val name: String = deployment.name,
    val version: String = deployment.version
) : KubernetesResource {
    override val phase: DeploymentPhase = DeploymentPhase.AD_HOC_JOB

    val job = Job().apply {
        metadata = ObjectMeta().apply {
            name = "${deployment.name}-$nameSuffix"
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }

        spec = JobSpec().apply {
            template = PodTemplateSpec().apply {
                // Copy most stuff from the deployment
                metadata = ObjectMeta().apply {
                    name = "${deployment.name}-$nameSuffix"
                }

                spec = PodSpec().apply {
                    containers = listOf(Container().apply {
                        volumeMounts = deployment.serviceContainer.volumeMounts
                        name = deployment.serviceContainer.name
                        image = deployment.serviceContainer.image
                        env = deployment.serviceContainer.env
                        command = deployment.serviceContainer.command.toMutableList()
                    })
                    volumes = deployment.deployment.spec.template.spec.volumes
                    imagePullSecrets = deployment.deployment.spec.template.spec.imagePullSecrets
                    restartPolicy = "Never"
                }
            }
        }
    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        val existingJob =
            client.batch().jobs().inNamespace(resourceNamespace(job)).withName("$name-$nameSuffix").get() ?: return false
        val k8Version = existingJob.metadata.annotations[UCLOUD_VERSION_ANNOTATION]
        return k8Version == version
    }

    override fun DeploymentContext.create() {
        delete()
        val command = job.spec.template.spec.containers.first().command.toMutableList()
        job.spec.template.spec.containers.first().command = command
        command.addAll(additionalArgs())

        client.batch().jobs().inNamespace(resourceNamespace(job)).withName("$name-$nameSuffix").createOrReplace(job)
    }

    override fun DeploymentContext.delete() {
        client.batch().jobs().inNamespace(resourceNamespace(job)).withName("$name-$nameSuffix").delete()
    }

    override fun toString(): String = "AdHocJob($name, $version, $nameSuffix)"
}

fun MutableBundle.withAdHocJob(
    deployment: DeploymentResource,
    nameSuffix: String,
    additionalArgs: DeploymentContext.() -> List<String>,
    name: String = deployment.name,
    version: String = deployment.version,
    init: AdHocJob.() -> Unit = {}
): AdHocJob {
    val job = AdHocJob(deployment, additionalArgs, nameSuffix, name, version).apply(init)
    resources.add(job)
    return job
}
