package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.*
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
    var services = ArrayList<AmbassadorMapping>()
    private val mappingCounter = AtomicInteger(0)

    fun addSimpleMapping(prefix: String, mappingName: String = "$name-${mappingCounter.incrementAndGet()}") {
        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: $mappingName
                    prefix: ^${prefix}(/.*)?$
                    prefix_regex: true
                    service: ${name}:$port
                    rewrite: ""
                """.trimIndent()
            )
        )
    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        val service = client.services().inNamespace(namespace).withName(name).get() ?: return false
        val k8Version = service.metadata.annotations[UCLOUD_VERSION_ANNOTATION] ?: return false
        return k8Version == version
    }

    override fun DeploymentContext.create() {
        val service = Service().apply {
            metadata = ObjectMeta().apply {
                val ambassadorConfig = services.joinToString("\n") { svc ->
                    svc.yamlDocument
                }

                this.name = this@AmbassadorService.name
                annotations = mapOf(
                    UCLOUD_VERSION_ANNOTATION to version,
                    "getambassador.io/config" to ambassadorConfig
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

                selector = mapOf("app" to appSelector)
            }
        }

        client.services().inNamespace(namespace).withName(name).createOrReplace(service)
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
