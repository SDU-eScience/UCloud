package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.extensions.*

/**
 * A resource for Kubernetes ingresses. For most ingress configuration see [AmbassadorService].
 */
class IngressResource(val name: String, val version: String) : KubernetesResource {
    val resource = Ingress().apply {
        metadata = ObjectMeta().apply {
            this.name = this@IngressResource.name
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }

        spec = IngressSpec().apply {
            rules = ArrayList()
        }
    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        return checkVersion(
            version,
            client.extensions().ingresses().inNamespace(resourceNamespace(resource)).withName(name).get()?.metadata
        )
    }

    override fun DeploymentContext.create() {
        client.extensions().ingresses().inNamespace(resourceNamespace(resource)).withName(name).createOrReplace(resource)
    }

    override fun DeploymentContext.delete() {
        client.extensions().ingresses().inNamespace(resourceNamespace(resource)).withName(name).delete()
    }
}

fun IngressResource.addRule(host: String, service: String = this.name, port: Int = 8080, path: String? = null) {
    resource.spec.rules.add(IngressRule().apply {
        this.host = host
        this.http = HTTPIngressRuleValue().apply {
            this.paths = arrayListOf(
                HTTPIngressPath().apply {
                    this.path = path

                    backend = IngressBackend().apply {
                        this.serviceName = service
                        this.servicePort = IntOrString(port)
                    }
                }
            )
        }
    })
}

fun MutableBundle.withIngress(
    name: String = this.name,
    version: String = this.version,
    init: IngressResource.() -> Unit = {}
): IngressResource {
    return IngressResource(name, version).apply(init).also { resources.add(it) }
}
