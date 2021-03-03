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
    val outputFile = File(outputDir, "Controllers.kt")
    outputFile.parentFile.mkdirs()

    val dispatchBuilder = StringBuilder()
    val ctx = GenerationContext()
    with(ctx) {
        for ((path, pathItem) in api.paths) {
            if (pathItem.get != null) {
                val callExtension = pathItem.get.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Get, pathItem.get, callExtension, typeRegistry, dispatchBuilder)
            }
            if (pathItem.post != null) {
                val callExtension = pathItem.post.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Post, pathItem.post, callExtension, typeRegistry, dispatchBuilder)
            }
            if (pathItem.delete != null) {
                val callExtension = pathItem.delete.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Delete, pathItem.delete, callExtension, typeRegistry, dispatchBuilder)
            }
            if (pathItem.head != null) {
                val callExtension = pathItem.head.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Head, pathItem.head, callExtension, typeRegistry, dispatchBuilder)
            }
            if (pathItem.put != null) {
                val callExtension = pathItem.put.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Put, pathItem.put, callExtension, typeRegistry, dispatchBuilder)
            }
            if (pathItem.options != null) {
                val callExtension = pathItem.options.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Options, pathItem.options, callExtension, typeRegistry, dispatchBuilder)
            }
            if (pathItem.patch != null) {
                val callExtension = pathItem.patch.extensions[CallExtension.EXTENSION] as CallExtension
                generateOp(HttpMethod.Patch, pathItem.patch, callExtension, typeRegistry, dispatchBuilder)
            }
        }

        outputFile.writer().use { w ->
            w.appendLine("/* AUTO GENERATED CODE - DO NOT MODIFY */")
            w.appendLine("/* Generated at: ${Date()} */")
            w.appendLine()
            w.appendLine("@file:Suppress(\"RemoveRedundantQualifierName\")")
            w.appendLine()
            w.appendLine("import dk.sdu.cloud.app.orchestrator.api.Compute")
            w.appendLine("import dk.sdu.cloud.providers.UCloudRpcDispatcher")
            w.appendLine("import dk.sdu.cloud.calls.CallDescription")
            w.appendLine("import org.springframework.web.bind.annotation.*")
            w.appendLine("import javax.servlet.http.HttpServletRequest")
            w.appendLine("import javax.servlet.http.HttpServletResponse")
            w.appendLine()

            for ((ns, text) in writers) {
                val docs = nsDoc[ns].takeIf { !it.isNullOrBlank() }
                if (docs != null) {
                    w.appendLine("/**")
                    docs.lines().forEach {
                        w.append(" * ")
                        w.appendLine(it)
                    }
                    w.appendLine(" */")
                }
                // TODO Hack
                w.appendLine("abstract class ${ns}Controller(private val providerId: String) : " +
                    "UCloudRpcDispatcher(listOf(Compute(providerId))) {")
                text.lines().forEach {
                    w.append("    ")
                    w.appendLine(it)
                }

                w.appendLine(buildString {
                    appendLine("@Suppress(\"UNCHECKED_CAST\")")
                    appendLine("override fun <R : Any, S : Any, E : Any> dispatchToHandler(")
                    appendLine("    call: CallDescription<R, S, E>,")
                    appendLine("    request: R,")
                    appendLine("    rawRequest: HttpServletRequest,")
                    appendLine("    rawResponse: HttpServletResponse,")
                    appendLine("): R {")
                    appendLine("    return when (call.fullName.replace(providerId, \"*\")) {")
                    append(dispatchBuilder.toString().prependIndent("        "))
                    appendLine("else -> error(\"Unhandled call\")")
                    appendLine("    }")
                    appendLine("}")
                }.prependIndent("    "))
                w.appendLine("}")
                w.appendLine()
            }
            w.appendLine()
        }
    }
}

private fun GenerationContext.generateOp(
    method: HttpMethod,
    op: Operation,
    info: CallExtension,
    registry: Map<String, ComputedType>,
    dispatchBuilder: StringBuilder
) {
    val namespace = info.call.containerRef.javaClass.simpleName ?: error("Found no name for ${info.call}")
    nsDoc[namespace] = info.call.containerRef.description ?: ""

    writer(namespace) {
        if (op.summary != null) {
            appendLine("/**")
            appendLine(op.summary.prependIndent(" * "))
            appendLine(" *")
            appendLine(op.description.prependIndent(" * "))
            appendLine(" */")
            if (op.deprecated == true) appendLine("@Deprecated(\"Deprecated\")")
        }

        /*
        with(info.call.http.path) {
            append("@RequestMapping(")
            append('"' + basePath.replace(PROVIDER_ID_PLACEHOLDER, "*").removeSuffix("/"))
            for (segment in segments) {
                when (segment) {
                    is HttpPathSegment.Simple -> {
                        append("/")
                        append(segment.text.replace(PROVIDER_ID_PLACEHOLDER, "*"))
                    }
                }
            }
            append('"')
            append(", method = ")
            append("[RequestMethod.")
            append(info.call.http.method.value.toUpperCase())
            appendLine("])")
        }
        */

        // Hack: Delete anything after '.' to allow versioning in call ids
        appendLine("abstract fun ${info.call.name.substringBefore('.')}(")

        /*
        val body = info.call.http.body
        val params = info.call.http.params
        val headers = info.call.http.headers
        when {
            body != null -> {
                if (info.call.requestClass.classifier != Unit::class) {
                    append("    ")
                    appendLine("@RequestBody request: ${info.call.requestClass}")
                }
            }

            params != null -> {
                val struct = info.requestType as? ComputedType.Struct
                struct?.properties?.forEach { (p, t) ->
                    val type = when (t) {
                        is ComputedType.Array -> "String"
                        is ComputedType.Bool -> "Boolean"
                        is ComputedType.CustomSchema -> "String"
                        is ComputedType.Dictionary -> "String"
                        is ComputedType.Enum -> "String" // TODO
                        is ComputedType.FloatingPoint -> "Double"
                        is ComputedType.Generic -> "String"
                        is ComputedType.Integer -> "Long"
                        is ComputedType.Struct -> "String"
                        is ComputedType.StructRef -> "String"
                        is ComputedType.Text -> "String"
                        is ComputedType.Unknown -> "String"
                    }

                    append("    ")
                    appendLine("@RequestParam ${p}: $type, ")
                }
            }

            headers != null -> {
                // TODO
            }
        }
         */
        append("    ")
        appendLine("request: ${info.call.requestClass}")
        appendLine("): ${info.call.successClass}")
        appendLine()

        with(dispatchBuilder) {
            append('"')
            append(info.call.fullName.replace(PROVIDER_ID_PLACEHOLDER, "*"))
            append('"')
            append(" -> ")
            append(info.call.name.substringBefore('.'))
            append("(")
            append("request as ")
            append(info.call.requestClass.toString())
            appendLine(") as R")
        }
    }
}

const val PROVIDER_ID_PLACEHOLDER = "PROVIDERID"
