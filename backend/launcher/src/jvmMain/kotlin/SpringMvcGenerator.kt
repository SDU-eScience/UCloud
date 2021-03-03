package dk.sdu.cloud

import dk.sdu.cloud.calls.*
import io.ktor.http.*
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import java.io.File
import java.util.*
import kotlin.collections.ArrayList


fun generateSpringMvcCode(
    outputDir: File,
    api: OpenAPI,
    typeRegistry: Map<String, ComputedType>,
) {
    val outputFile = File(outputDir, "Applications/index.ts")
    outputFile.parentFile.mkdirs()

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
    val namespace = info.call.containerRef.javaClass.simpleName ?: error("Found no name for ${info.call}")

    writer(namespace) {
        if (op.summary != null) {
            appendLine("/**")
            appendLine(op.summary.prependIndent(" * "))
            appendLine(" *")
            appendLine(op.description.prependIndent(" * "))
            appendLine(" */")
            if (op.deprecated == true) appendLine("@Deprecated(\"Deprecated\")")
        }
        appendLine("abstract fun ${info.call.name.substringBefore('.')}: ${info.call.successClass}")
        // Hack: Delete anything after '.' to allow versioning in call ids
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