package dk.sdu.cloud

import dk.sdu.cloud.calls.CallDescriptionContainer
import java.io.File
import java.util.*
import kotlin.collections.LinkedHashMap

fun generateTypeScriptCode(
    types: LinkedHashMap<String, GeneratedType>,
    calls: List<GeneratedRemoteProcedureCall>,
    title: String,
    container: CallDescriptionContainer
) {
//    val outputFile =
//        File("../frontend-web/webclient/app/UCloud/index.ts").takeIf { it.exists() }
//            ?: File("/opt/frontend/app/UCloud/index.ts").takeIf { it.exists() }
    val outputFile = File("/tmp/documentation/${container.namespace.replace(".", "_")}.ts")
    if (outputFile == null) {
        println("WARN: Could not find frontend code")
        return
    }

    outputFile.writer().use { w ->
        w.appendLine("/* eslint-disable */")
        w.appendLine("/* AUTO GENERATED CODE - DO NOT MODIFY */")
        w.appendLine("/* Generated at: ${Date()} */")
        w.appendLine()
        w.appendLine(
            """
                import {buildQueryString} from "@/Utilities/URIUtilities";
                
                export type Int8 = number;
                export type Int16 = number;
                export type Int32 = number;
                export type Int64 = number;
                export type Float32 = number;
                export type Float64 = number;
                
            """.trimIndent()
        )

        for ((qualifiedName, type) in types) {
            w.appendLine(type.typescript())
        }

        for (call in calls) {

        }
    }
}

fun GeneratedTypeReference.typescript(addColon: Boolean = true): String {
    val baseValue = when (this) {
        is GeneratedTypeReference.Any -> "any"
        is GeneratedTypeReference.Array -> "(${valueType.typescript(addColon = false)})[]"
        is GeneratedTypeReference.Bool -> "boolean"
        is GeneratedTypeReference.ConstantString -> "\"${value}\""
        is GeneratedTypeReference.Dictionary -> "Record<string, any>"
        is GeneratedTypeReference.Float32 -> "Float32"
        is GeneratedTypeReference.Float64 -> "Float64"
        is GeneratedTypeReference.Int8 -> "Int8"
        is GeneratedTypeReference.Int16 -> "Int16"
        is GeneratedTypeReference.Int32 -> "Int32"
        is GeneratedTypeReference.Int64 -> "Int64"
        is GeneratedTypeReference.Structure -> buildString {
            append(simplifyClassName(name))
            if (generics.isNotEmpty()) {
                append("<")
                for ((index, generic) in generics.withIndex()) {
                    if (index != 0) append(", ")
                    append(generic.typescript(addColon = false))
                }
                append(">")
            }
        }
        is GeneratedTypeReference.Text -> "string"
        is GeneratedTypeReference.Void -> "{}"
    }

    return when {
        nullable && addColon -> "?: $baseValue | null"
        nullable && !addColon -> "$baseValue | null | undefined"
        addColon -> ": $baseValue"
        else -> baseValue
    }
}

fun GeneratedType.typescript(): String {
    return when (this) {
        is GeneratedType.Enum -> {
            buildString {
                append("export type ${simplifyClassName(name)} = ")
                for ((index, option) in options.withIndex()) {
                    if (index != 0) append(" | ")
                    append('"')
                    append(option.name)
                    append('"')
                }
                appendLine(";")
                append("export const ${simplifyClassName(name)}Values: ${simplifyClassName(name)}[] = [")
                for ((index, option) in options.withIndex()) {
                    if (index != 0) append(", ")
                    append('"')
                    append(option.name)
                    append('"')
                    append(" as const")
                }
                appendLine("];")
            }
        }
        is GeneratedType.Struct -> {
            buildString {
                append("export interface ${simplifyClassName(name)}")
                if (generics.isNotEmpty()) {
                    append("<")
                    for ((index, generic) in generics.withIndex()) {
                        if (index != 0) append(", ")
                        append(generic)
                    }
                    append(">")
                }
                appendLine(" {")
                for (property in properties) {
                    appendLine("    ${property.name}${property.type.typescript(addColon = true)};")
                }
                appendLine("}")
            }
        }
        is GeneratedType.TaggedUnion -> {
            buildString {
                val hasBase = baseProperties.isNotEmpty()
                if (hasBase) {
                    append("export interface ${simplifyClassName(name)}Base")
                    if (generics.isNotEmpty()) {
                        append("<")
                        for ((index, generic) in generics.withIndex()) {
                            if (index != 0) append(", ")
                            append(generic)
                        }
                        append(">")
                    }
                    appendLine(" {")
                    for (property in baseProperties) {
                        appendLine("    ${property.name}${property.type.typescript(addColon = true)};")
                    }
                    appendLine("}")
                }
                append("export type ${simplifyClassName(name)} = ")
                for ((index, option) in options.withIndex()) {
                    if (index != 0) append(" | ")
                    append(option.typescript(addColon = false))
                }
                appendLine(";")
            }
        }
    }
}

private fun simplifyClassName(qualifiedName: String): String {
    return simplifyName(qualifiedName).replace(".", "")
}

private fun tsSafeIdentifier(name: String): String {
    return when (name) {
        "delete" -> "remove"
        "new" -> "new_" // not ideal but will be deprecated anyway
        else -> name
    }
}
