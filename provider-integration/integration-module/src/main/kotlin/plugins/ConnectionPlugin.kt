package dk.sdu.cloud.plugins

import dk.sdu.cloud.app.orchestrator.api.SSHKey
import dk.sdu.cloud.config.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.controllers.RequestContext
import kotlinx.serialization.Serializable

@Serializable
data class UidAndGid(val uid: Int, val gid: Int)

sealed class ConnectionResponse {
    class Redirect(
        /**
         * The URL to redirect the client to. This URL can be relative to the integration module or absolute to any URL.
         *
         * NOTE: The integration module (ConnectionController) might do this through a proxy endpoint, which is required
         * if message signing is needed.
         */
        val redirectTo: String,

        /**
         * A globally unique ID which is required when message signing is needed. If message signing is not needed, then
         * this value is not used. This value should be passed to UserMapping.insertMapping() to ensure the message
         * signing keys are correctly activated.
         */
        val globallyUniqueConnectionId: String,

        /**
         * This will be invoked immediately before sending the final redirect. If this function crashes, then the
         * redirect will not be sent to the recipient.
         */
        val beforeRedirect: suspend () -> Unit = {},
    ) : ConnectionResponse()
    class ShowInstructions(val query: Map<String, List<String>>) : ConnectionResponse()
}

@JvmInline
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

interface ConnectionPlugin : Plugin<ConfigSchema.Plugins.Connection> {
    suspend fun RequestContext.initiateConnection(username: String): ConnectionResponse

    fun PluginContext.initializeRpcServer(server: RpcServer) {
        // Default is empty
    }

    suspend fun RequestContext.showInstructions(query: Map<String, List<String>>): HTML {
        // By default, this is not required. This is useful if initiateConnection returns a redirect.
        throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }

    suspend fun PluginContext.mappingExpiration(): Long? {
        return null
    }

    suspend fun PluginContext.requireMessageSigning(): Boolean

    suspend fun PluginContext.onSshKeySynchronized(username: String, keys: List<SSHKey>) {

    }
}
