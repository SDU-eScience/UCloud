package dk.sdu.cloud.plugins

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.http.RpcServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UidAndGid(val uid: Int, val gid: Int)

sealed class ConnectionResponse {
    class Redirect(val redirectTo: String) : ConnectionResponse()
    class ShowInstructions(val query: Map<String, List<String>>) : ConnectionResponse()
}

value class HTML(val html: String) {
    companion object {
        // NOTE(Dan): This is not meant for extensive use of truly untrusted data. This is intended to be used as a
        // precaution in-case bad data somehow makes it through the system.
        fun escape(value: String): String {
            return buildString {
                for (char in value) {
                    // Escape list is based on:
                    // https://github.com/google/guava/blob/master/guava/src/com/google/common/html/HtmlEscapers.java
                    append(
                        when (char) {
                            '"' -> "&quot;"
                            '\'' -> "&#39;"
                            '&' -> "&amp;"
                            '<' -> "&lt;"
                            '>' -> "&gt;"
                            else -> char
                        }
                    )
                }
            }
        }
    }
}

interface ConnectionPlugin : Plugin<JsonObject> {
    fun PluginContext.initiateConnection(username: String): ConnectionResponse

    fun PluginContext.initializeRpcServer(server: RpcServer) {
        // Default is empty
    }

    fun PluginContext.showInstructions(query: Map<String, List<String>>): HTML {
        // By default, this is not required. This is useful if initiateConnection returns a redirect.
        throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }

    fun PluginContext.mappingExpiration(): Long? {
        return null
    }
}
