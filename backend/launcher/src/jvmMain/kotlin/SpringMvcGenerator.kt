package dk.sdu.cloud

import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.orchestrator.api.ChunkedUploadProtocol
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsProvider
import dk.sdu.cloud.file.orchestrator.api.FilesProvider
import dk.sdu.cloud.file.orchestrator.api.SharesProvider
import java.io.File
import java.util.*
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType

fun main() {
    generateSpringMvcCode()
}

fun generateSpringMvcCode() {
    val potentialBaseDirs = listOf(File("."), File(".."))
    val baseDir = potentialBaseDirs.find {
        it.listFiles()?.any { it.isDirectory && it.name.endsWith("-service") } == true
    }
    if (baseDir == null) {
        println("WARN: Could not find UCloud services at current working directory (${File(".").absolutePath}")
        println("WARN: No Spring code will be generated!")
        return
    }
    val outputFile = File(baseDir, "./jvm-provider-support/src/jvmMain/kotlin/Controllers.kt")
    if (!outputFile.exists()) {
        println("WARN: Could not find controllers file at ${outputFile.absolutePath}")
        return
    }

    data class ContainerWithReplacements(
        val container: CallDescriptionContainer,
        val replacements: Map<String, String>
    )

    val containers = listOf(
        ContainerWithReplacements(
            JobsProvider(PROVIDER_ID_PLACEHOLDER),
            mapOf(
                "Res" to "dk.sdu.cloud.app.orchestrator.api.Job",
                "Support" to "dk.sdu.cloud.app.orchestrator.api.ComputeSupport"
            )
        ),
        ContainerWithReplacements(
            NetworkIPProvider(PROVIDER_ID_PLACEHOLDER),
            mapOf(
                "Res" to "dk.sdu.cloud.app.orchestrator.api.NetworkIP",
                "Support" to "dk.sdu.cloud.app.orchestrator.api.NetworkIPSupport",
            )
        ),
        ContainerWithReplacements(
            IngressProvider(PROVIDER_ID_PLACEHOLDER),
            mapOf(
                "Res" to "dk.sdu.cloud.app.orchestrator.api.Ingress",
                "Support" to "dk.sdu.cloud.app.orchestrator.api.IngressSupport",
            )
        ),
        ContainerWithReplacements(Shells(PROVIDER_ID_PLACEHOLDER), emptyMap()),
        ContainerWithReplacements(
            LicenseProvider(PROVIDER_ID_PLACEHOLDER),
            mapOf(
                "Res" to "dk.sdu.cloud.app.orchestrator.api.License",
                "Support" to "dk.sdu.cloud.app.orchestrator.api.LicenseSupport",
            )
        ),
        ContainerWithReplacements(
            FileCollectionsProvider(PROVIDER_ID_PLACEHOLDER),
            mapOf(
                "Res" to "dk.sdu.cloud.file.orchestrator.api.FileCollection",
                "Support" to "dk.sdu.cloud.file.orchestrator.api.FSSupport",
            )
        ),
        ContainerWithReplacements(
            FilesProvider(PROVIDER_ID_PLACEHOLDER),
            mapOf(
                "Res" to "dk.sdu.cloud.file.orchestrator.api.UFile",
                "Support" to "dk.sdu.cloud.file.orchestrator.api.FSSupport",
            )
        ),
        ContainerWithReplacements(
            ChunkedUploadProtocol(PROVIDER_ID_PLACEHOLDER, "/ucloud/chunked"),
            emptyMap()
        ),
        ContainerWithReplacements(
            SharesProvider(PROVIDER_ID_PLACEHOLDER),
            mapOf(
                "Res" to "dk.sdu.cloud.file.orchestrator.api.Share",
                "Support" to "dk.sdu.cloud.file.orchestrator.api.ShareSupport",
            )
        ),
    )

    outputFile.writer().use { w ->
        w.appendLine("@file:Suppress(\"RemoveRedundantQualifierName\", \"RedundantUnitReturnType\", \"unused\", \"UNREACHABLE_CODE\", \"UNCHECKED_CAST\")")
        w.appendLine("package dk.sdu.cloud.providers")
        w.appendLine()
        w.appendLine("/* AUTO GENERATED CODE - DO NOT MODIFY */")
        w.appendLine("/* Generated at: ${Date()} */")
        w.appendLine()
        w.appendLine()
        w.appendLine("import dk.sdu.cloud.providers.UCloudRpcDispatcher")
        w.appendLine("import dk.sdu.cloud.providers.UCloudWsDispatcher")
        w.appendLine("import dk.sdu.cloud.providers.UCloudWsContext")
        w.appendLine("import dk.sdu.cloud.calls.CallDescription")
        w.appendLine("import javax.servlet.http.HttpServletRequest")
        w.appendLine("import javax.servlet.http.HttpServletResponse")
        w.appendLine("import org.springframework.web.bind.annotation.RequestMapping")
        w.appendLine()

        for ((container, genericTable) in containers) {
            val dispatchBuilder = StringBuilder()
            val wsDispatchBuilder = StringBuilder()
            val wsCanHandleBuilder = StringBuilder()
            val paths = HashSet<String>()
            val ops = ArrayList<String>()
            container.documentation()
            container::class.members.forEach {
                try {
                    if (it.returnType.isSubtypeOf(CallDescription::class.starProjectedType) && it.name != "call") {
                        it.call(container)
                    }
                } catch (ex: Throwable) {
                    println("Unexpected failure: ${container} ${it}. ${ex.stackTraceToString()}")
                }
            }

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

                ops.add(generateOp(call, dispatchBuilder, wsDispatchBuilder, wsCanHandleBuilder, genericTable))
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
            if (containerName == "dk.sdu.cloud.file.orchestrator.api.ChunkedUploadProtocol") {
                w.appendLine("UCloudRpcDispatcher($containerName(providerId, \"/ucloud/chunked\"), wsDispatcher) {")
            } else {
                w.appendLine("UCloudRpcDispatcher($containerName(providerId), wsDispatcher) {")
            }
            for (text in ops) {
                text.lines().forEach {
                    w.append("    ")
                    w.appendLine(it)
                }
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

private fun generateOp(
    call: CallDescription<*, *, *>,
    httpDispatchBuilder: StringBuilder,
    wsDispatchBuilder: StringBuilder,
    wsCanHandleBuilder: StringBuilder,
    genericTable: Map<String, String>,
): String {
    return buildString {
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

        val req = replaceGenerics(call.requestClass, genericTable)
        val resp = replaceGenerics(call.successClass, genericTable)
        val err = replaceGenerics(call.errorClass, genericTable)

        append("    ")
        appendLine("request: $req,")
        if (call.websocketOrNull != null) {
            appendLine("    wsContext: UCloudWsContext<$req, $resp, $err>,")
        }
        if (call.httpOrNull != null) {
            appendLine("): $resp")
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
                append(req)
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
                append(req)
                append(", ")
                append("ctx as ")
                append("UCloudWsContext<")
                append(req)
                append(", ")
                append(resp)
                append(", ")
                append(err)
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

// NOTE(Dan): Extremely quick solution. It is not really robust if we want to make major changes, but it will work for
// any code we are likely to write right now.
fun replaceGeneric(any: Any?, genericTable: Map<String, String>): String {
    val toString = any.toString().replace("class ", "")
    return genericTable[toString] ?: toString
}

fun replaceGenerics(type: KType, genericTable: Map<String, String>): String {
    return buildString {
        append(replaceGeneric(type.classifier, genericTable))
        val args = type.arguments
        if (args.isNotEmpty()) {
            append("<")
            var first = true
            for (arg in args) {
                if (first) first = false else append(", ")

                val argType = arg.type
                if (argType != null) {
                    append(replaceGenerics(argType, genericTable))
                } else {
                    append(replaceGeneric(arg, genericTable))
                }
            }
            append(">")
        }
    }
}

const val PROVIDER_ID_PLACEHOLDER = "PROVIDERID"
