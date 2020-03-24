package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.PodSpec
import io.fabric8.kubernetes.api.model.PodTemplateSpec
import io.fabric8.kubernetes.api.model.batch.*

/**
 * A resource for creating a cron job
 */
class CronJobResource(
    deployment: DeploymentResource,
    schedule: String,
    additionalArgs: List<String>,
    val name: String = deployment.name,
    val version: String = deployment.version
) : KubernetesResource {
    override val phase: DeploymentPhase = DeploymentPhase.DEPLOY

    val job = CronJob().apply {
        metadata = ObjectMeta().apply {
            name = this@CronJobResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }

        spec = CronJobSpec().apply {
            this.schedule = schedule

            jobTemplate = JobTemplateSpec().apply {
                metadata = ObjectMeta().apply {
                    name = this@CronJobResource.name
                }

                spec = JobSpec().apply {
                    template = PodTemplateSpec().apply {
                        // Copy most stuff from the deployment
                        metadata = ObjectMeta().apply {
                            name = this@CronJobResource.name
                        }

                        spec = PodSpec().apply {
                            containers = listOf(Container().apply {
                                volumeMounts = deployment.serviceContainer.volumeMounts
                                name = deployment.serviceContainer.name
                                image = deployment.serviceContainer.image
                                env = deployment.serviceContainer.env
                                command = deployment.serviceContainer.command + additionalArgs
                            })
                            volumes = deployment.deployment.spec.template.spec.volumes
                            imagePullSecrets = deployment.deployment.spec.template.spec.imagePullSecrets
                            restartPolicy = "Never"
                        }
                    }
                }
            }
        }

    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        try {
            val existingJob =
                client.batch().cronjobs().inNamespace(resourceNamespace(job)).withName(this@CronJobResource.name).get()
                    ?: return false
            val k8Version = existingJob.metadata.annotations[UCLOUD_VERSION_ANNOTATION]
            return k8Version == version
        } catch (ex: Throwable) {
            return false
        }
    }

    override fun DeploymentContext.create() {
        client.batch().cronjobs().inNamespace(resourceNamespace(job)).withName(this@CronJobResource.name).createOrReplace(job)
    }

    override fun DeploymentContext.delete() {
        client.batch().cronjobs().inNamespace(resourceNamespace(job)).withName(this@CronJobResource.name).delete()
    }

    override fun toString(): String = "CronJob($name, $version)"
}

fun MutableBundle.withCronJob(
    deployment: DeploymentResource,
    schedule: String,
    additionalArgs: List<String>,
    name: String = this.name,
    version: String = this.version,
    init: CronJobResource.() -> Unit
): CronJobResource {
    return CronJobResource(deployment, schedule, additionalArgs, name, version).apply(init).also { resources.add(it) }
}
