package dk.sdu.cloud

import dk.sdu.cloud.calls.*
import io.ktor.http.*
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
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
    api: OpenAPI,
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
        for ((path, pathItem) in api.paths) {
            if (pathItem.get != null) {
                val callExtension = pathItem.get.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Get, pathItem.get, callExtension, typeRegistry)
            }
            if (pathItem.post != null) {
                val callExtension = pathItem.post.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Post, pathItem.post, callExtension, typeRegistry)
            }
            if (pathItem.delete != null) {
                val callExtension = pathItem.delete.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Delete, pathItem.delete, callExtension, typeRegistry)
            }
            if (pathItem.head != null) {
                val callExtension = pathItem.head.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Head, pathItem.head, callExtension, typeRegistry)
            }
            if (pathItem.put != null) {
                val callExtension = pathItem.put.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Put, pathItem.put, callExtension, typeRegistry)
            }
            if (pathItem.options != null) {
                val callExtension = pathItem.options.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Options, pathItem.options, callExtension, typeRegistry)
            }
            if (pathItem.patch != null) {
                val callExtension = pathItem.patch.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Patch, pathItem.patch, callExtension, typeRegistry)
            }
        }

        for ((name, type) in typeRegistry) {
            if (!name.endsWith("_Opt")) {
                if (type is ComputedType.Struct && type.genericInfo != null) {
                    continue
                }

                writer(simplifyNamespace(name).substringBeforeLast('.', "")) {
                    buildType(type, typeRegistry)
                }
            }
        }

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

            data class Node(val name: String, val children: ArrayList<Node>, val namespace: String, var text: String)

            val root = Node("", ArrayList(), "", "")
            for ((ns, text) in ctx.writers) {
                var nodeSearch = root
                var namespaceBuilder = ""
                var first = true
                ns.split(".").filter { it.isNotEmpty() }.forEach { n ->
                    val nextNode = nodeSearch.children.find { it.name == n }

                    if (first) first = false
                    else namespaceBuilder += "."
                    namespaceBuilder += n

                    if (nextNode == null) {
                        val newNode = Node(n, ArrayList(), namespaceBuilder, "")
                        nodeSearch.children.add(newNode)
                        nodeSearch = newNode
                    } else {
                        nodeSearch = nextNode
                    }
                }

                nodeSearch.text += text
            }

            fun writeNode(node: Node) {
                if (node.name != "") {
                    w.appendLine("export namespace ${node.name} {")
                }

                var text = node.text.removeSuffix("\n")
                for (n in node.namespace.split(".")) {
                    text = text.replace("$n.", "")
                }

                w.appendLine(text)

                for (child in node.children) {
                    writeNode(child)
                }

                if (node.name != "") w.appendLine("}")
            }

            writeNode(root)
        }
    }
}

private fun GenerationContext.generateOp(
    method: HttpMethod,
    op: Operation,
    info: CallExtension,
    registry: Map<String, ComputedType>,
) {

    val namespace =
        (info.call.containerRef.javaClass.annotations.find { it is TSNamespace } as TSNamespace?)?.namespace
            ?: simplifyNamespace(
                info.call.containerRef.javaClass.canonicalName!!.substringBeforeLast('.') +
                    if (info.call.containerRef.javaClass.annotations.any { it is TSTopLevel }) ""
                    else '.' + info.call.namespace.substringAfterLast('.')
            )

    writer(namespace) {
        if (op.summary != null) {
            appendLine("/**")
            appendLine(op.summary.prependIndent(" * "))
            appendLine(" *")
            appendLine(op.description.prependIndent(" * "))
            if (op.deprecated == true) appendLine(" * @deprecated")
            appendLine(" */")
        }
        // Hack: Delete anything after '.' to allow versioning in call ids
        append("export function ${tsSafeIdentifier(info.call.name.substringBefore('.'))}(")
        val requestType = info.requestType
        if (requestType != null) {
            appendLine()
            append("    request: ")
            buildType(requestType.asRef(), registry)
            appendLine()
        }
        append("): APICallParameters<")
        if (requestType == null) {
            append("{}")
        } else {
            buildType(requestType.asRef(), registry)
        }

        val responseType = info.responseType
        if (responseType == null) {
            append(", {}")
        } else {
            append(", ")
            buildType(responseType.asRef(), registry)
        }

        appendLine("> {")
        appendLine("    return {")
        appendLine("        context: \"\",")
        appendLine("        method: \"${method.value.toUpperCase()}\",")
        append("        path: ")
        with(info.call.http.path) {
            val usesParams = info.call.http.params?.parameters?.isNotEmpty() == true
            if (usesParams) {
                append("buildQueryString(")
            }
            append('"' + basePath.removeSuffix("/") + '"')
            for (segment in segments) {
                when (segment) {
                    is HttpPathSegment.Simple -> {
                        append(" + \"/")
                        append(segment.text)
                        append('"')
                    }
                }
            }
            if (usesParams) {
                append(", {")
                var isFirst = true
                for (param in (info.call.http.params?.parameters ?: emptyList())) {
                    when (param) {
                        is HttpQueryParameter.Property<*> -> {
                            if (isFirst) {
                                isFirst = false
                            } else {
                                append(", ")
                            }

                            append(param.property)
                            append(": ")
                            append("request.${param.property}")
                        }
                    }
                }
                append("})")
            }
            appendLine(",")
        }
        if (requestType != null) appendLine("        parameters: request,")
        appendLine("        reloadId: Math.random(),")
        if (op.requestBody != null) {
            appendLine("        payload: request,")
        }
        appendLine("    };")
        appendLine("}")

    }
}

private fun Appendable.buildType(
    computedType: ComputedType,
    registry: Map<String, ComputedType>,
) {
    when (computedType) {
        is ComputedType.Integer -> append("number /* int${computedType.size} */")

        is ComputedType.FloatingPoint -> append("number /* float${computedType.size} */")

        is ComputedType.Text -> append("string")

        is ComputedType.Bool -> append("boolean")

        is ComputedType.Array -> {
            buildType(computedType.itemType.asRef(), registry)
            append("[]")
        }

        is ComputedType.Dictionary -> {
            append("Record<string, ")
            buildType(computedType.itemType.asRef(), registry)
            append(">")
        }

        is ComputedType.Unknown -> {
            append("any /* unknown */")
        }

        is ComputedType.Generic -> {
            append(computedType.id)
        }

        is ComputedType.Struct -> {
            if (computedType.documentation != null) {
                appendLine("/**")
                append(computedType.documentation!!.prependIndent(" * ").removeSuffix("\n"))
                appendLine()
                if (computedType.deprecated) {
                    appendLine(" * @deprecated")
                }
                appendLine(" */")
            }
            if (computedType.tsDef != null) {
                append(computedType.tsDef!!.code)
            } else {
                if (computedType.discriminator != null) {
                    append("export type ${computedType.qualifiedName.substringAfterLast('.')} = ")
                    var first = true
                    for ((k, v) in computedType.discriminator!!.valueToQualifiedName) {
                        if (first) first = false
                        else append(" | ")
                        buildType(registry.getValue(v).asRef(), registry)
                    }
                    appendLine()
                } else {
                    append("export interface ${computedType.qualifiedName.substringAfterLast('.')}")
                    run {
                        val alreadyAdded = HashSet<String>()
                        var first = true
                        computedType.properties.filter {
                            val value = it.value

                            value is ComputedType.Generic ||
                                (value is ComputedType.Array && value.itemType is ComputedType.Generic)
                        }.forEach { prop ->
                            val id = if (prop.value is ComputedType.Generic) {
                                (prop.value as ComputedType.Generic).id
                            } else {
                                ((prop.value as ComputedType.Array).itemType as ComputedType.Generic).id
                            }

                            if (id in alreadyAdded) return@forEach
                            alreadyAdded.add(id)
                            if (first) {
                                first = false
                                append("<")
                            } else {
                                append(", ")
                            }
                            append(id)
                            append(" = unknown")
                        }
                        if (alreadyAdded.isNotEmpty()) {
                            append(">")
                        }
                    }
                    appendLine(" {")
                    computedType.properties.forEach { (name, type) ->
                        if (type.documentation != null) {
                            appendLine("/**".prependIndent("    "))
                            append(type.documentation!!.prependIndent("     * ").removeSuffix("\n"))
                            appendLine()
                            if (type.deprecated) {
                                appendLine("     * @deprecated")
                            }
                            appendLine(" */".prependIndent("    "))
                        }
                        append("    ")
                        append(name)
                        if (type.nullable) {
                            append("?")
                        }
                        append(": ")
                        buildType(type.asRef(), registry)
                        appendLine(",")
                    }
                    appendLine("}")
                }
            }
        }

        is ComputedType.StructRef -> {
            val structMaybe = registry[computedType.qualifiedName] as? ComputedType.Struct
            val isGeneric = structMaybe?.genericInfo != null
            if (isGeneric) {
                val struct = structMaybe!!
                val genericInfo = struct.genericInfo!!
                val baseType = registry[genericInfo.baseType]!!
                buildType(baseType.asRef(), registry)
                append("<")
                var first = true
                for (gen in genericInfo.typeParameters) {
                    if (first) {
                        first = false
                    } else {
                        append(", ")
                    }
                    buildType(gen.asRef(), registry)
                }
                append(">")
            } else {
                append(simplifyNamespace(computedType.qualifiedName))
            }
        }

        is ComputedType.Enum -> {
            append("(")
            append(computedType.options.joinToString(" | ") { '"' + it + '"' })
            append(")")
        }

        is ComputedType.CustomSchema -> {
            if (computedType.tsDefinition != null) {
                buildType(computedType.tsDefinition.asRef(), registry)
            } else {
                append("any /* CustomSchema */")
            }
        }
    }
}

private fun simplifyNamespace(qualifiedName: String): String {
    val result = StringBuilder()
    val components = qualifiedName
        .removeSuffix("_Opt")
        .replace("dk.sdu.cloud.app.license.", "compute.license.")
        .replace("dk.sdu.cloud.app.store.", "compute.")
        .replace("dk.sdu.cloud.app.orchestrator.", "compute.")
        .replace("dk.sdu.cloud.app.kubernetes.", "compute.ucloud.")
        .replace("contact.book", "contactbook")
        .removePrefix("dk.sdu.cloud.calls.types")
        .removePrefix("dk.sdu.cloud.calls")
        .removePrefix("dk.sdu.cloud.service")
        .removePrefix("dk.sdu.cloud")
        .split(".")
        .filter { it.isNotEmpty() }
        .filter {
            it != "api"
        }

    for ((index, component) in components.withIndex()) {
        if (index != 0) {
            result.append(".")
        }
        result.append(component)
        if (component.first().isUpperCase() && index != components.lastIndex) {
            result.append("NS")
        }
    }

    return result.toString()
}

private fun tsSafeIdentifier(name: String): String {
    return when (name) {
        "delete" -> "remove"
        "new" -> "new_" // not ideal but will be deprecated anyway
        else -> name
    }
}