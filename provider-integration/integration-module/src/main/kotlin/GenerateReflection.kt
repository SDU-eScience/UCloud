package dk.sdu.cloud

import java.io.File

fun main(args: Array<String>) {
    val fileName = args.firstOrNull() ?: error("Usage GenerateReflection.kt <fileName>")
    val file = File(fileName)
    if (!file.exists()) error("Unknown file ${file.absolutePath}")
    file.readText().lines().forEach { line ->
        println("""{ "name": "$line", "fields": [{ "name": "Companion" }] },""")
        println("""{ "name": "$line${"$"}Companion", "methods": [{ "name": "serializer" }] },""")
    }
}
