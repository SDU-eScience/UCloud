package dk.sdu.cloud

import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.IngoingCallResponse
import io.ktor.http.*
import kotlinx.serialization.KSerializer
import java.io.File
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.reflect.KClass

fun typescriptBaseFileName(namespace: String): String {
    return namespace.split(".").joinToString("") { it.replaceFirstChar { it.uppercaseChar() } }
}

var didGenerateTypeScriptCore = false

const val TYPESCRIPT_AUTO_GENERATED = "/* DO NOT MODIFY - AUTO GENERATED CODE - DO NOT MODIFY */"

fun generateTypeScriptCode(
    types: LinkedHashMap<String, GeneratedType>,
    calls: List<GeneratedRemoteProcedureCall>,
    title: String,
    container: CallDescriptionContainer
) {
    val baseDirectory = File("../../frontend-web/webclient/app/UCloudTest").takeIf { it.exists() }
        ?: File("/opt/frontend/app/UCloudTest").takeIf { it.exists() }

    if (baseDirectory == null) {
        println("WARN: Could not find frontend code")
        return
    }

    if (!didGenerateTypeScriptCore) {
        didGenerateTypeScriptCore = true
        baseDirectory.listFiles()?.forEach {
            if (it.readLines().any { it == TYPESCRIPT_AUTO_GENERATED }) {
                it.delete()
            }
        }
        File(baseDirectory, "Numbers.ts").writer().use { w ->
            w.appendLine("/* eslint-disable */")
            w.appendLine(TYPESCRIPT_AUTO_GENERATED)
            w.appendLine("export type Int8 = number;")
            w.appendLine("export type Int16 = number;")
            w.appendLine("export type Int32 = number;")
            w.appendLine("export type Int64 = number;")
            w.appendLine("export type Float32 = number;")
            w.appendLine("export type Float64 = number;")
        }
    }

    if (container.namespace.contains(PROVIDER_ID_PLACEHOLDER) || container.namespace.contains(".provider")) return

    val importBuilder = TsImportBuilder(container::class, types, HashMap<String, HashSet<String>>())
    val outputFile = File(baseDirectory, "${typescriptBaseFileName(container.namespace)}.ts")

    val fileBuilder = StringBuilder().let { w ->
        for ((_, type) in types) {
            if (type.owner != container::class) continue
            w.appendLine(type.typescript(importBuilder))
        }

        w.appendLine("class ${container.typescriptApiName()} {")
        for ((index, call) in calls.withIndex()) {
            val http = call.realCall.httpOrNull ?: continue

            if (index != 0) {
                w.appendLine()
                w.appendLine()
            }
            w.append(buildString {
                append("public ")
                append(tsSafeIdentifier(call.name))
                appendLine("(")
                append("    request")
                append(call.requestType.typescript(importBuilder))
                appendLine()
                append("): APICallParameters<")
                append(call.requestType.typescript(importBuilder, addColon = false))
                append(", ")
                append(call.responseType.typescript(importBuilder, addColon = false))
                appendLine("> {")

                val method = when (http.method) {
                    HttpMethod.Get -> "'GET'"
                    HttpMethod.Post -> "'POST'"
                    HttpMethod.Delete -> "'DELETE'"
                    HttpMethod.Put -> "'PUT'"
                    HttpMethod.Patch -> "'PATCH'"
                    HttpMethod.Options -> "'OPTIONS'"
                    HttpMethod.Head -> "'HEAD'"
                    else -> null
                }

                when {
                    method != null && http.params != null && http.body == null && http.headers == null -> {
                        // Simple-case: Bind everything from query parameters
                        append(buildString {
                            appendLine("return {")

                            append("    method: ")
                            append(method)
                            appendLine(",")

                            append("    path: ")
                            append(http.pathToTypescript(true))
                            appendLine(",")

                            appendLine("    context: '',")
                            appendLine("    parameters: request,")

                            appendLine("};")
                        }.prependIndent("    ").trimEnd())
                    }

                    method != null && http.params == null && http.body != null && http.headers == null -> {
                        // Simple-case: Bind everything to the body
                        append(buildString {
                            appendLine("return {")

                            append("    method: ")
                            append(method)
                            appendLine(",")

                            append("    path: ")
                            append(http.pathToTypescript(false))
                            appendLine(",")

                            appendLine("    context: '',")
                            appendLine("    parameters: request,")
                            appendLine("    payload: request,")

                            appendLine("};")
                        }.prependIndent("    ").trimEnd())
                    }

                    method != null && http.params == null && http.body == null && http.headers == null -> {
                        // Simple-case: Everything is null
                        append(buildString {
                            appendLine("return {")

                            append("    method: ")
                            append(method)
                            appendLine(",")

                            append("    path: ")
                            append(http.pathToTypescript(false))
                            appendLine(",")

                            appendLine("    context: '',")

                            appendLine("};")
                        }.prependIndent("    ").trimEnd())
                    }

                    else -> {
                        if (call.name.contains("upload")) {
                            appendLine("throw Error('Uploads are not supported from this interface');")
                        } else {
                            println("Cannot generate implementation for ${call.namespace}.${call.name}")
                            appendLine("throw Error('Missing implementation for ${call.namespace}.${call.name}');")
                        }
                    }
                }
                appendLine()
                appendLine("}")
            }.prependIndent("    ").trimEnd())
        }
        w.appendLine()
        w.appendLine("}")
        w.appendLine()
        w.appendLine("export default new ${container.typescriptApiName()};")
    }

    outputFile.writer().use { w ->
        w.appendLine("/* eslint-disable */")
        w.appendLine(TYPESCRIPT_AUTO_GENERATED)
        w.appendLine("// noinspection ES6UnusedImports")
        w.appendLine()
        w.appendLine(
            """
                import {buildQueryString} from "@/Utilities/URIUtilities";
                import {Int8, Int16, Int32, Int64, Float32, Float64} from "@/UCloudTest/Numbers";
            """.trimIndent()
        )

        for ((module, imports) in importBuilder.imports) {
            w.appendLine("import {${imports.joinToString(", ")}} from \"@/UCloudTest/${typescriptBaseFileName(module)}\"")
        }
        w.appendLine()

        w.appendLine(fileBuilder)
    }
}

fun HttpRequest<*, *, *>.pathToTypescript(usesParams: Boolean): String {
    return buildString {
        if (usesParams) append("buildQueryString(")
        append('"' + path.basePath.removeSuffix("/") + '"')
        for (segment in path.segments) {
            when (segment) {
                is HttpPathSegment.Simple -> {
                    append(" + \"/")
                    append(segment.text)
                    append('"')
                }
            }
        }
        if (usesParams) append(", request)")
    }
}

fun CallDescriptionContainer.typescriptApiName(): String {
    return namespace.split(".").joinToString("") { it.replaceFirstChar { it.uppercaseChar() } } + "Api"
}

data class TsImportBuilder(
    val owner: KClass<out CallDescriptionContainer>,
    val visitedTypes: Map<String, GeneratedType>,
    val imports: MutableMap<String, HashSet<String>>,
)

fun GeneratedTypeReference.typescript(
    importBuilder: TsImportBuilder,
    addColon: Boolean = true
): String {
    val baseValue = when (this) {
        is GeneratedTypeReference.Any -> "any"
        is GeneratedTypeReference.Array -> "(${valueType.typescript(importBuilder, addColon = false)})[]"
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
            val type = importBuilder.visitedTypes[name]
            if (type != null && importBuilder.owner != type.owner && name.startsWith("dk.sdu.cloud.")) {
                val typeOwner = type.owner ?: run {
                    println("MISSING OWNER FOR $name. THIS SHOULD NOT HAPPEN.")
                    null
                }

                if (typeOwner != null) {
                    val namespace = typeOwner.objectInstance?.namespace
                    if (namespace != null) {
                        val imports = importBuilder.imports[namespace] ?: run {
                            val newList = HashSet<String>()
                            importBuilder.imports[namespace] = newList
                            newList
                        }
                        imports.add(simplifyClassName(name))
                    }
                }
            }

            append(simplifyClassName(name))
            if (generics.isNotEmpty()) {
                append("<")
                for ((index, generic) in generics.withIndex()) {
                    if (index != 0) append(", ")
                    append(generic.typescript(importBuilder, addColon = false))
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

fun GeneratedType.typescript(importBuilder: TsImportBuilder): String {
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
                    appendLine("    ${property.name}${property.type.typescript(importBuilder, addColon = true)};")
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
                        appendLine("    ${property.name}${property.type.typescript(importBuilder, addColon = true)};")
                    }
                    appendLine("}")
                }
                append("export type ${simplifyClassName(name)} = ")
                for ((index, option) in options.withIndex()) {
                    if (index != 0) append(" | ")
                    append(option.typescript(importBuilder, addColon = false))
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

fun UseCase.typescript(): String {
    return buildString {
        var lastActor: String? = null

        appendLine("```typescript")
        for (node in nodes) {
            when (node) {
                is UseCaseNode.Actor -> {
                    // Do nothing
                }
                is UseCaseNode.Call<*, *, *> -> {
                    if (lastActor != node.actor.name) {
                        appendLine("// Authenticated as ${node.actor.name}")
                        lastActor = node.actor.name
                    }

                    if (node.name != null) {
                        append("const ")
                        append(node.name)
                        append(" = ")
                    }
                    append("await callAPI(")
                    append(node.call.containerRef.typescriptApiName())
                    append(".")
                    append(node.call.field?.name ?: node.call.name)
                    appendLine("(")
                    appendLine(
                        prettyMapper.encodeToString(
                            node.call.requestType as KSerializer<Any>, node.request
                        ).prependIndent("    ")
                    )
                    appendLine(");")
                    appendLine()
                    appendLine("/*")
                    if (node.name != null) {
                        append(node.name)
                        append(" = ")
                    }
                    when (val response = node.response) {
                        is IngoingCallResponse.Error -> {
                            appendLine(response.statusCode.toString())
                        }
                        is IngoingCallResponse.Ok -> {
                            appendLine(
                                prettyMapper.encodeToString(node.call.successType as KSerializer<Any>, response.result)
                            )
                        }
                    }
                    appendLine("*/")
                }
                is UseCaseNode.Comment -> {
                    appendLine()
                    appendLine("/* ${node.comment} */")
                    appendLine()
                }
                is UseCaseNode.SourceCode -> {
                    if (node.language == UseCaseNode.Language.TYPESCRIPT) {
                        appendLine(node.code)
                    }
                }

                else -> {
                    // TODO
                }
            }
        }
        appendLine("```")
    }
}
