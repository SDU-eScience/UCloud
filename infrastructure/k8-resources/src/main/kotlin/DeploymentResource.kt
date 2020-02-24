package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.EnvVar
import io.fabric8.kubernetes.api.model.EnvVarSource
import io.fabric8.kubernetes.api.model.HTTPGetAction
import io.fabric8.kubernetes.api.model.LocalObjectReference
import io.fabric8.kubernetes.api.model.ObjectFieldSelector
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.PodSpec
import io.fabric8.kubernetes.api.model.PodTemplateSpec
import io.fabric8.kubernetes.api.model.Probe
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec

/**
 * A resource for creating a deployment
 *
 * The deployment by default uses the defaults of service-common.
 */
class DeploymentResource(
    val name: String,
    val version: String,
    var image: String = "registry.cloud.sdu.dk/sdu-cloud/$name-service:$version",
    enableLiveness: Boolean = true,
    livenessPort: Int = 8080,
    livenessPath: String = "/status"
) : KubernetesResource {
    val deployment = Deployment().apply {
        metadata = ObjectMeta().apply {
            this.name = this@DeploymentResource.name
            labels = mapOf("app" to this@DeploymentResource.name)
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }

        spec = DeploymentSpec().apply {
            replicas = 1
            selector = LabelSelector().apply {
                matchLabels = mapOf("app" to this@DeploymentResource.name)
            }

            template = PodTemplateSpec().apply {
                metadata = ObjectMeta().apply {
                    labels = mapOf("app" to this@DeploymentResource.name)
                }

                spec = PodSpec().apply {
                    containers = ArrayList()
                    containers.add(
                        Container().apply {
                            this.name = this@DeploymentResource.name
                            this.image = this@DeploymentResource.image

                            env = ArrayList()
                            env.add(EnvVar().apply {
                                name = "POD_IP"
                                valueFrom = EnvVarSource().apply {
                                    fieldRef = ObjectFieldSelector().apply {
                                        fieldPath = "status.podIP"
                                    }
                                }
                            })

                            if (enableLiveness) {
                                livenessProbe = Probe().apply {
                                    httpGet = HTTPGetAction().apply {
                                        port = IntOrString(livenessPort)
                                        this.path = livenessPath
                                    }

                                    initialDelaySeconds = 3
                                    periodSeconds = 30
                                    failureThreshold = 5
                                }
                            }

                            command = ArrayList()
                            command.add("/opt/service/bin/service")
                            volumeMounts = ArrayList()
                        }
                    )

                    volumes = ArrayList()
                    imagePullSecrets = ArrayList()
                    imagePullSecrets.add(LocalObjectReference().apply {
                        name = "esci-docker"
                    })
                }
            }
        }
    }

    val serviceContainer: Container
        get() = deployment.spec.template.spec.containers.first()

    val volumes: MutableList<Volume>
        get() = deployment.spec.template.spec.volumes

    val containers: MutableList<Container>
        get() = deployment.spec.template.spec.containers

    override fun DeploymentContext.create() {
        client.apps().deployments().inNamespace(resourceNamespace(deployment)).withName(name)
            .createOrReplace(deployment)
    }

    override fun DeploymentContext.delete() {
        client.apps().deployments().inNamespace(resourceNamespace(deployment)).withName(name).delete()
    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        val existingDeployment =
            client.apps().deployments().inNamespace(resourceNamespace(deployment)).withName(name).get()
                ?: return false
        val k8Version = existingDeployment.metadata.annotations[UCLOUD_VERSION_ANNOTATION]
        return k8Version == version
    }

    override fun toString() = "Deployment($name, $version)"
}

fun MutableBundle.withDeployment(
    injectAllDefaults: Boolean = true,
    injectServiceSecrets: Boolean = true,
    image: String = "registry.cloud.sdu.dk/sdu-cloud/$name-service:$version",
    init: DeploymentResource.() -> Unit
): DeploymentResource {
    val resource = DeploymentResource(name, version, image)
        .apply(init)
        .apply {
            if (injectAllDefaults) {
                injectDefaults()
            }
        }

    if (injectServiceSecrets) resources.add(ServiceSecrets(name))
    resources.add(resource)
    return resource
}
