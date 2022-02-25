package dk.sdu.cloud.sync.mounter.api

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException

fun joinPath(vararg components: String, isDirectory: Boolean = false): String {
    val basePath = components.joinToString("/") + (if (isDirectory) "/" else "").normalize()
    return if (basePath.startsWith("/")) basePath
    else "/$basePath"
}

fun String.parents(): List<String> {
    val components = components().dropLast(1)
    return components.mapIndexed { index, _ ->
        val path = "/" + components.subList(0, index + 1).joinToString("/").removePrefix("/")
        if (path == "/") path else "$path/"
    }
}

fun String.parent(): String {
    val components = components().dropLast(1)
    if (components.isEmpty()) return "/"

    val path = "/" + components.joinToString("/").removePrefix("/")
    return if (path == "/") path else "$path/"
}

fun String.components(): List<String> = removePrefix("/").removeSuffix("/").split("/")

fun String.fileName(): String = substringAfterLast('/')

fun String.normalize(): String {
    val inputComponents = components()
    val reconstructedComponents = ArrayList<String>()

    for (component in inputComponents) {
        when (component) {
            ".", "" -> {
                // Do nothing
            }

            ".." -> {
                if (reconstructedComponents.isNotEmpty()) {
                    reconstructedComponents.removeAt(reconstructedComponents.lastIndex)
                }
            }

            else -> reconstructedComponents.add(component)
        }
    }

    return "/" + reconstructedComponents.joinToString("/")
}

fun relativize(parentPath: String, childPath: String): String {
    val rootNormalized = parentPath.normalize()
    val rootComponents = rootNormalized.components()
    val childNormalized = childPath.normalize()
    val childComponents = childNormalized.components()

    // Throw exception if child is not a child of root
    require(rootNormalized.length <= childNormalized.length || rootComponents.size < childComponents.size) {
        "child is not a child of parent ($childPath !in $parentPath)"
    }

    // Throw exception if child is not a child of root
    for (i in rootComponents.indices) {
        require(rootComponents[i] == childComponents[i]) {
            "child is not a child of parent ($childPath !in $parentPath)"
        }
    }

    return "./" + childComponents.takeLast(childComponents.size - rootComponents.size).joinToString("/")
}

data class PathMetadata(val collection: String)
fun extractPathMetadata(path: String): PathMetadata {
    val normalizedPath = path.normalize()
    val components = normalizedPath.components()
    val collection = components.getOrNull(0) ?: throw RPCException("Invalid path: $path", HttpStatusCode.BadRequest)
    return PathMetadata(collection)
}
