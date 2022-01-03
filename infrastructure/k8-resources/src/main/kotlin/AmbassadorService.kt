package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.networking.v1beta1.*
import io.fabric8.kubernetes.client.KubernetesClient
import java.util.concurrent.atomic.AtomicInteger

data class AmbassadorMapping(val yamlDocument: String)

/**
 * A resource for creating Ambassador services
 */
class AmbassadorService(
    var name: String,
    var version: String,
    var appSelector: String = name,
    var port: Int = 8080
) : KubernetesResource {
    //    var services = ArrayList<AmbassadorMapping>()
    private val prefixes = ArrayList<String>()
    private val mappingCounter = AtomicInteger(0)

    fun addSimpleMapping(prefix: String) {
        prefixes.add(prefix)
    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        val service = client.services().inNamespace(namespace).withName(name).get() ?: return false
        val k8Version = service.metadata.annotations[UCLOUD_VERSION_ANNOTATION] ?: return false
        return k8Version == version
    }

    override fun DeploymentContext.create() {
        val service = Service().apply {
            val fixedSelector = if (appSelector == "auth") "ucloud-auth" else name
            metadata = ObjectMeta().apply {
                this.name = this@AmbassadorService.name
                annotations = mapOf(
                    UCLOUD_VERSION_ANNOTATION to version
                )
            }

            spec = ServiceSpec().apply {
                clusterIP = "None"
                type = "ClusterIP"
                ports = listOf(
                    ServicePort().apply {
                        this.name = this@AmbassadorService.name
                        this.port = this@AmbassadorService.port
                        protocol = "TCP"
                        targetPort = IntOrString(this@AmbassadorService.port)
                    },
                    ServicePort().apply {
                        this.name = this@AmbassadorService.name + "80"
                        this.port = 80
                        protocol = "TCP"
                        targetPort = IntOrString(80)
                    }
                )

                selector = mapOf("app" to fixedSelector)
            }
        }

        client.services().inNamespace(namespace).withName(name).createOrReplace(service)

        val ingress = Ingress().apply {
            metadata = ObjectMeta().apply {
                this.name = this@AmbassadorService.name
                this.annotations = mapOf(
                    "nginx.ingress.kubernetes.io/proxy-body-size" to "0",
                    "nginx.ingress.kubernetes.io/proxy-read-timeout" to "3600",
                    "nginx.ingress.kubernetes.io/proxy-send-timeout" to "3600"
                )
            }

            spec = IngressSpec().apply {
                rules = listOf(IngressRule(
                    Configuration.retrieve("domain", " The domain"),
                    HTTPIngressRuleValue().apply {
                        paths = prefixes.map { prefix ->
                            HTTPIngressPath(
                                IngressBackend().apply {
                                    serviceName = name
                                    servicePort = IntOrString(port)
                                },
                                prefix,
                                null
                            )
                        }
                    }
                ))
            }
        }

        client.network().ingresses().inNamespace(namespace).withName(name).createOrReplace(ingress)
    }

    override fun DeploymentContext.delete() {
        client.services().inNamespace(namespace).withName(name).delete()
    }

    override fun toString(): String = "AmbassadorService($name, $version)"
}

fun MutableBundle.withAmbassador(
    pathPrefix: String? = "/api/${name.replace('-', '/')}",
    init: AmbassadorService.() -> Unit = {}
): AmbassadorService {
    val resource = AmbassadorService(name, version).apply {
        if (pathPrefix != null) {
            addSimpleMapping(pathPrefix.removeSuffix("/"))
        }

        init()
    }

    resources.add(resource)
    return resource
}
