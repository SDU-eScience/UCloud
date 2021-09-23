package dk.sdu.cloud

import dk.sdu.cloud.calls.*
import io.ktor.http.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class GenerationContext {
    val writers = HashMap<String, StringBuilder>()
    val nsDoc = HashMap<String, String>()

    inline fun writer(ns: String, block: StringBuilder.() -> Unit) {
        writers.computeIfAbsent(ns, { StringBuilder() }).block()
    }
}

fun generateTypeScriptCode(
    outputDir: File,
    typeRegistry: Map<String, ComputedType>,
) {
    val outputFile =
        File("../frontend-web/webclient/app/UCloud/index.ts").takeIf { it.exists() }
            ?: File("/opt/frontend/app/UCloud/index.ts").takeIf { it.exists() }
    if (outputFile == null) {
        println("WARN: Could not find frontend code")
        return
    }

    println("Generating typescript output at: ${outputFile.absolutePath}")
    val ctx = GenerationContext()
    with(ctx) {
        outputFile.writer().use { w ->
            w.appendLine("/* eslint-disable */")
            w.appendLine("/* AUTO GENERATED CODE - DO NOT MODIFY */")
            w.appendLine("/* Generated at: ${Date()} */")
            w.appendLine()
            w.appendLine(
                """
                    import {buildQueryString} from "Utilities/URIUtilities";
                    
                """.trimIndent()
            )

        }
    }
}

private fun tsSafeIdentifier(name: String): String {
    return when (name) {
        "delete" -> "remove"
        "new" -> "new_" // not ideal but will be deprecated anyway
        else -> name
    }
}
