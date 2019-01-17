package dk.sdu.cloud.file.api

import java.io.File

internal fun homeDirectory(user: String): String = "/home/$user/"

internal fun favoritesDirectory(user: String): String = joinPath(homeDirectory(user), "Favorites", isDirectory = true)

fun joinPath(vararg components: String, isDirectory: Boolean = false): String {
    val basePath = File(components.joinToString("/") + (if (isDirectory) "/" else "")).normalize().path
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

fun String.fileName(): String = File(this).name

fun String.normalize(): String = File(this).normalize().path

fun relativize(rootPath: String, absolutePath: String): String {
    return File(rootPath).toURI().relativize(File(absolutePath).toURI()).normalize().path
}
