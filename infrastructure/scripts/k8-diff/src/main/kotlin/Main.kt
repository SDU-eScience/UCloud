import com.fasterxml.jackson.module.kotlin.readValues
import java.io.File

fun discoverFiles(): List<Pair<String, K8Doc>> {
    val currentDir = File(".")
    val rootFiles = currentDir.listFiles() ?: throw IllegalStateException("Not found: ${currentDir.absolutePath}")
    return rootFiles
        .filter { it.isDirectory && it.name.endsWith("-service") }
        .flatMap { serviceDirectory ->
            val k8Directory = File(serviceDirectory, "k8").takeIf { it.exists() && it.isDirectory }
                ?: return@flatMap emptyList<Pair<String, K8Doc>>()
            (k8Directory.listFiles() ?: emptyArray())
                .filter { it.extension == "yml" || it.extension == "yaml" }
                .flatMap { resourceFile ->
                    val parser = yamlFactory.createParser(resourceFile)
                    yamlMapper.readValues<K8Doc>(parser).readAll()
                        .map { serviceDirectory.name to it.cleanDocument() }
                }
        }
}

typealias K8Doc = MutableMap<String, Any?>

fun K8Doc.cleanDocument(): K8Doc {
    val copy = HashMap<String, Any?>(this)
    when (kind) {
        ResourceType.Deployment -> {
            val whitelistKeys = listOf("kind", "metadata", "spec")
            copy.keepWhitelist(whitelistKeys)

            copy.getDocument("metadata")?.keepWhitelist(listOf("name", "namespace"))

            val spec = copy.getDocument("spec")
            spec?.keepWhitelist(listOf("selector", "template"))

            spec?.getDocument("template")?.getDocument("spec")
                ?.keepWhitelist(listOf("containers", "volumes", "imagePullSecrets"))
        }

        ResourceType.Service -> {
            copy.keepWhitelist(listOf("metadata", "spec", "kind"))
            copy.getDocument("metadata")?.keepWhitelist(listOf("name", "namespace", "annotations"))
            copy.getDocument("metadata")?.getDocument("annotations")?.keepWhitelist(listOf("getambassador.io/config"))
            copy.getDocument("spec")?.keepWhitelist(listOf("clusterIP", "type", "ports", "selector"))
        }
    }

    return copy
}

fun MutableMap<String, Any?>.keepWhitelist(whitelist: List<String>) {
    HashSet(keys).forEach { key ->
        if (key !in whitelist) remove(key)
    }
}

@Suppress("UNCHECKED_CAST")
fun K8Doc.getDocument(key: String): K8Doc? = get(key) as? K8Doc

val K8Doc.name: String get() = getDocument("metadata")!!["name"] as String
val K8Doc.namespace: String get() = runCatching { getDocument("metadata")!!["namespace"] as String }.getOrElse { "default" }
val K8Doc.kind: ResourceType? get() = ResourceType.values().find { it.type == get("kind") as String }

// The likely list we need to keep:
// ("Ingress", "CronJob", "Deployment", "Service", "ClusterRole", "RoleBinding", "ServiceAccount", "NetworkPolicy", "Role")

val resourceWhitelist = setOf<ResourceType>(ResourceType.Deployment, ResourceType.Service)

enum class ResourceType(val type: String) {
    Deployment("Deployment"),
    Service("Service")
}

fun compareDocs(context: String, localValue: Any?, remoteValue: Any?) {
    fun complain() {
        println()
        println("---")
        println("$context values are different!\nlocal:\n$localValue\n\nremote:\n$remoteValue")
    }

    if ((localValue == null || remoteValue == null) && localValue != remoteValue) {
        return complain()
    }

    when (localValue) {
        is MutableMap<*, *> -> {
            if (remoteValue !is MutableMap<*, *>) return complain()

            localValue.forEach { key, localMValue ->
                compareDocs("$context/$key", localMValue, remoteValue[key])
            }
        }

        is List<*> -> {
            if (remoteValue !is List<*>) return complain()
            if (localValue.size != remoteValue.size) return complain()

            localValue.indices.forEach { i ->
                compareDocs("$context[$i]", localValue[i], remoteValue[i])
            }
        }

        else -> {
            if (localValue != remoteValue) {
                complain()
            }
        }
    }
}

fun main() {
    discoverFiles()
        .filter { (_, resource) -> resource.kind in resourceWhitelist }
        .sortedBy { (_, resource) -> resource.kind!!.ordinal }
        .forEach { (service, resource) ->
            val remoteDoc = Kubernetes.readResource(resource.kind!!.type, resource.name, resource.namespace)

            compareDocs("${resource.name}:${resource.kind}", resource, remoteDoc)
        }
}
