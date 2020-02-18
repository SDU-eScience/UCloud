package dk.sdu.cloud.k8

import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.ObjectMeta

class ConfigMapResource(
    private val name: String,
    private val version: String
) : KubernetesResource {
    val configMap = ConfigMap().apply {
        metadata = ObjectMeta().apply {
            this.name = this@ConfigMapResource.name
            labels = mapOf("app" to this@ConfigMapResource.name)
            annotations = mapOf(UCLOUD_VERSION_ANNOTATION to version)
        }

        data = HashMap()
        binaryData = HashMap()
    }

    override fun DeploymentContext.isUpToDate(): Boolean {
        val existing =
            client.configMaps().inNamespace(resourceNamespace(configMap)).withName(name).get()?.metadata ?: return false
        return checkVersion(version, existing)
    }

    override fun DeploymentContext.create() {
        client.configMaps().inNamespace(resourceNamespace(configMap)).createOrReplace(configMap)
    }

    override fun DeploymentContext.delete() {
        client.configMaps().inNamespace(resourceNamespace(configMap)).delete(configMap)
    }

    override fun toString(): String = "ConfigMapResource($name, $version)"
}

inline fun <reified T : Any> ConfigMapResource.addConfig(fileName: String, configuration: T) {
    val writer = yamlMapper.writerFor(jacksonTypeRef<T>())
    configMap.data[fileName] = writer.writeValueAsString(configuration)
}

fun ConfigMapResource.addConfig(fileName: String, fileContents: String) {
    configMap.data[fileName] = fileContents
}

fun MutableBundle.withConfigMap(
    name: String = this.name,
    version: String = this.version,
    init: ConfigMapResource.() -> Unit
): ConfigMapResource {
    return ConfigMapResource(name, version)
        .apply {
            init(this)
            resources.add(this)
        }
}
