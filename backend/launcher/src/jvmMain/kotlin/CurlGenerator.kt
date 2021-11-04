package dk.sdu.cloud

import dk.sdu.cloud.calls.UseCase
import dk.sdu.cloud.calls.UseCaseNode
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.httpOrNull
import dk.sdu.cloud.calls.toKtorTemplate
import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*

fun UseCase.curl(): String {
    return buildString {
        var lastActor: String? = null

        appendLine("```bash")
        appendLine("# ------------------------------------------------------------------------------------------------------")
        appendLine("# \$host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'")
        appendLine("# \$accessToken is a valid access-token issued by UCloud")
        appendLine("# ------------------------------------------------------------------------------------------------------")
        appendLine()

        loop@ for (node in nodes) {
            when (node) {
                is UseCaseNode.Actor -> {
                    // Do nothing
                }
                is UseCaseNode.Call<*, *, *> -> {
                    val http = node.call.httpOrNull
                    if (http == null) {
                        appendLine("# curl generation not supported for ${node.call.fullName}")
                        continue
                    }

                    if (http.params != null && http.body != null) {
                        appendLine("# curl generation not supported for ${node.call.fullName}")
                        continue
                    }

                    if (lastActor != node.actor.name) {
                        appendLine("# Authenticated as ${node.actor.name}")
                        lastActor = node.actor.name
                    }

                    append("curl ")
                    append("-X${http.method.value.uppercase()} ")

                    append("-H \"Authorization: Bearer \$accessToken\" ")
                    if (http.body != null) {
                        append("-H \"Content-Type: content-type: application/json; charset=utf-8\" ")
                    }

                    append('"')
                    append("\$host")
                    append(http.path.toKtorTemplate(fullyQualified = true))
                    if (http.params != null) {
                        fun encodeElement(name: String, element: JsonElement): Map<String, String> {
                            return when (element) {
                                JsonNull -> emptyMap()

                                is JsonPrimitive -> mapOf(name to element.content.encodeURLQueryComponent())

                                is JsonObject -> {
                                    element.entries.map {
                                        encodeElement(it.key, it.value)
                                    }.reduce { acc, elem -> acc + elem }
                                }

                                is JsonArray -> {
                                    element.firstOrNull()?.let { encodeElement(name, it) } ?: emptyMap()
                                }
                            }
                        }

                        append("?")
                        val element = defaultMapper.encodeToJsonElement(
                            node.call.requestType as KSerializer<Any>,
                            node.request
                        )

                        encodeElement("request", element).entries.forEachIndexed { index, (k, v) ->
                            if (index != 0) append('&')
                            append(k)
                            append('=')
                            append(v)
                        }
                    }
                    append("\" ")
                    if (http.body != null) {
                        append("-d '")
                        append(
                            prettyMapper.encodeToString(
                                node.call.requestType as KSerializer<Any>, node.request
                            )
                        )
                        appendLine("'")
                    }

                    appendLine()
                    appendLine()


                    if (node.name != null) {
                        appendLine("# ${node.name} = ")
                    }
                    when (val response = node.response) {
                        is IngoingCallResponse.Error -> {
                            append("# ")
                            appendLine(response.statusCode.toString())
                        }
                        is IngoingCallResponse.Ok -> {
                            prettyMapper.encodeToString(node.call.successType as KSerializer<Any>, response.result)
                                .lines().forEach { appendLine("# $it") }
                        }
                    }
                    appendLine()
                }
                is UseCaseNode.Comment -> {
                    node.comment.lines().forEach { appendLine("# $it") }
                    appendLine()
                }
                is UseCaseNode.SourceCode -> {
                    // Do nothing
                }

                else -> {
                    // TODO
                }
            }
        }
        appendLine("```")
    }
}
