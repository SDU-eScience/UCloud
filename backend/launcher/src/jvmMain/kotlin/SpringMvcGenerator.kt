package dk.sdu.cloud

import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.*
import java.io.File
import java.util.*

fun main() {
    generateSpringMvcCode()
}

fun generateSpringMvcCode() {
    val hasServices = File(".").listFiles()?.any { it.isDirectory && it.name.endsWith("-service") } == true
    if (!hasServices) {
        println("WARN: Could not find UCloud services at current working directory (${File(".").absolutePath}")
        println("WARN: No markdown documentation will be injected!")
        return
    }
    val outputFile = File("./jvm-provider-support/src/jvmMain/kotlin/Controllers.kt")
    if (!outputFile.exists()) {
        println("WARN: Could not find controllers file at ${outputFile.absolutePath}")
        return
    }

    val containers = listOf(
        JobsProvider(PROVIDER_ID_PLACEHOLDER),
        NetworkIPProvider(PROVIDER_ID_PLACEHOLDER),
        IngressProvider(PROVIDER_ID_PLACEHOLDER),
        Shells(PROVIDER_ID_PLACEHOLDER),
        LicenseProvider(PROVIDER_ID_PLACEHOLDER)
    )

    outputFile.writer().use { w ->
        w.appendLine("/* AUTO GENERATED CODE - DO NOT MODIFY */")
        w.appendLine("/* Generated at: ${Date()} */")
        w.appendLine()
        w.appendLine("@file:Suppress(\"RemoveRedundantQualifierName\", \"RedundantUnitReturnType\", \"unused\", \"UNREACHABLE_CODE\", \"UNCHECKED_CAST\")")
        w.appendLine()
        w.appendLine("import dk.sdu.cloud.providers.UCloudRpcDispatcher")
        w.appendLine("import dk.sdu.cloud.providers.UCloudWsDispatcher")
        w.appendLine("import dk.sdu.cloud.providers.UCloudWsContext")
        w.appendLine("import dk.sdu.cloud.calls.CallDescription")
        w.appendLine("import javax.servlet.http.HttpServletRequest")
        w.appendLine("import javax.servlet.http.HttpServletResponse")
        w.appendLine("import org.springframework.web.bind.annotation.RequestMapping")
        w.appendLine()

        for (container in containers) {
            val ctx = GenerationContext()
            with(ctx) {
                val dispatchBuilder = StringBuilder()
                val wsDispatchBuilder = StringBuilder()
                val wsCanHandleBuilder = StringBuilder()
                val paths = HashSet<String>()
                for (call in container.callContainer) {
                    call.httpOrNull?.path?.apply {
                        val path = buildString {
                            append("/")
                            append(basePath.removePrefix("/").removeSuffix("/"))
                            for (segment in segments) {
                                append("/")
                                when (segment) {
                                    is HttpPathSegment.Simple -> append(segment.text)
                                }
                            }
                        }.replace(PROVIDER_ID_PLACEHOLDER, "*")

                        paths.add(path)
                    }

                    generateOp(call, dispatchBuilder, wsDispatchBuilder, wsCanHandleBuilder)
                }

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
                    val containerName = container.javaClass.name
                    val simpleContainerName = container.javaClass.simpleName.replace("Provider", "")
                    w.appendLine("@RequestMapping(")
                    for (path in paths) {
                        w.append("    ")
                        w.append('"')
                        w.append(path)
                        w.append('"')
                        w.appendLine(",")
                    }
                    w.appendLine(")")
                    w.appendLine("abstract class ${simpleContainerName}Controller(")
                    w.appendLine("    private val providerId: String,")
                    w.appendLine("    wsDispatcher: UCloudWsDispatcher,")
                    w.append("): ")
                    w.appendLine("UCloudRpcDispatcher($containerName(providerId), wsDispatcher) {")
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
                        appendLine("): S {")
                        appendLine("    return when (call.fullName.replace(providerId, \"*\")) {")
                        append(dispatchBuilder.toString().prependIndent("        "))
                        appendLine("else -> error(\"Unhandled call\")")
                        appendLine("    }")
                        appendLine("}")
                    }.prependIndent("    "))

                    w.appendLine(buildString {
                        appendLine("override fun canHandleWebSocketCall(call: CallDescription<*, *, *>): Boolean {")
                        append(wsCanHandleBuilder.toString().prependIndent("    "))
                        appendLine("return false")
                        appendLine("}")
                    }.prependIndent("    "))

                    w.appendLine(buildString {
                        appendLine("override fun <R : Any, S : Any, E : Any> dispatchToWebSocketHandler(")
                        appendLine("    ctx: UCloudWsContext<R, S, E>,")
                        appendLine("    request: R,")
                        appendLine(") {")
                        appendLine("    when (ctx.call.fullName.replace(providerId, \"*\")) {")
                        append(wsDispatchBuilder.toString().prependIndent("        "))
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
}

private fun GenerationContext.generateOp(
    call: CallDescription<*, *, *>,
    httpDispatchBuilder: StringBuilder,
    wsDispatchBuilder: StringBuilder,
    wsCanHandleBuilder: StringBuilder,
) {
    val namespace = call.containerRef.javaClass.simpleName ?: error("Found no name for ${call}")
    nsDoc[namespace] = call.containerRef.description ?: ""

    writer(namespace) {
        val docs = call.docOrNull
        if (docs != null) {
            appendLine("/**")
            if (docs.summary != null) appendLine(docs.summary!!.prependIndent(" * "))
            appendLine(" *")
            if (docs.description != null) appendLine(docs.description!!.prependIndent(" * "))
            appendLine(" */")
        }

        // Hack: Delete anything after '.' to allow versioning in call ids
        appendLine("abstract fun ${call.name.substringBefore('.')}(")

        append("    ")
        appendLine("request: ${call.requestClass},")
        if (call.websocketOrNull != null) {
            appendLine("    wsContext: UCloudWsContext<${call.requestClass}, ${call.successClass}, ${call.errorClass}>,")
        }
        if (call.httpOrNull != null) {
            appendLine("): ${call.successClass}")
        } else {
            appendLine(")")
        }
        appendLine()

        if (call.httpOrNull != null) {
            with(httpDispatchBuilder) {
                append('"')
                append(call.fullName.replace(PROVIDER_ID_PLACEHOLDER, "*"))
                append('"')
                append(" -> ")
                append(call.name.substringBefore('.'))
                append("(")
                append("request as ")
                append(call.requestClass.toString())
                appendLine(") as S")
            }
        }

        if (call.websocketOrNull != null) {
            with(wsDispatchBuilder) {
                append('"')
                append(call.fullName.replace(PROVIDER_ID_PLACEHOLDER, "*"))
                append('"')
                append(" -> ")
                append(call.name.substringBefore('.'))
                append("(")
                append("request as ")
                append(call.requestClass.toString())
                append(", ")
                append("ctx as ")
                append("UCloudWsContext<")
                append(call.requestClass.toString())
                append(", ")
                append(call.successClass.toString())
                append(", ")
                append(call.errorClass.toString())
                append(">")
                appendLine(")")
            }
        }


        if (call.websocketOrNull != null) {
            with(wsCanHandleBuilder) {
                append("if (call.fullName.replace(providerId, \"*\") == ")
                append('"')
                append(call.fullName.replace(PROVIDER_ID_PLACEHOLDER, "*"))
                append('"')
                appendLine(") return true")
            }
        }
    }
}

const val PROVIDER_ID_PLACEHOLDER = "PROVIDERID"
