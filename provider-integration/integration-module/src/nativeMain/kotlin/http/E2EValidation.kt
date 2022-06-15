package dk.sdu.cloud.http

import dk.sdu.cloud.NativeJWTValidation
import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.controllers.MessageSigningIpc
import dk.sdu.cloud.debug.detailD
import dk.sdu.cloud.debugSystem
import dk.sdu.cloud.ipc.IpcClient
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.provider.api.IntegrationProvider
import dk.sdu.cloud.utils.mapProviderApiToUserApi
import dk.sdu.cloud.utils.normalizeCertificate
import kotlinx.atomicfu.atomic
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libjwt.*

suspend fun loadE2EValidation(pluginContext: PluginContext) {
    val config = pluginContext.config
    if (config.serverMode != ServerMode.User) return
    val connectionPlugin = config.plugins.connection ?: return
    val signingRequired = pluginContext.run { connectionPlugin.run { requireMessageSigning() } }
    if (!signingRequired) return

    addMiddleware(object : Middleware {
        val invalidSignature = HttpStatusCode(482, "Invalid signature")
        val certCache = CertificateCache(pluginContext.ipcClient)

        override suspend fun <R : Any> beforeRequest(handler: CallHandler<R, *, *>) {
            val mappedCall = mapProviderApiToUserApi(handler.description.fullName)

            val signedIntent: String = when (val sctx = handler.ctx.serverContext) {
                is HttpContext -> {
                    sctx.headers.find { it.header.equals(IntegrationProvider.UCLOUD_SIGNED_INTENT, ignoreCase = true) }
                        ?.value
                }

                is WebSocketContext<*, *, *> -> {
                    TODO()
                }

                else -> error("Unexpected server context of type $sctx")
            } ?: run {
                debugSystem.detailD("Invalid signature: No signed intent found", Unit)
                throw RPCException.fromStatusCode(invalidSignature)
            }

            val validIntent = certCache.validate(signedIntent) ?: run {
                debugSystem.detailD("Invalid signature: Metadata did not validate", Unit)
                throw RPCException.fromStatusCode(invalidSignature)
            }
            if (validIntent.call != mappedCall) {
                debugSystem.detailD(
                    "Invalid signature: Call does not match intention",
                    JsonObject(
                        mapOf(
                            "intendedCall" to JsonPrimitive(validIntent.call),
                            "rawCall" to JsonPrimitive(handler.description.fullName),
                            "mappedCall" to JsonPrimitive(mappedCall)
                        )
                    )
                )
                throw RPCException.fromStatusCode(invalidSignature)
            }

            // TODO(Dan): Should we try to verify user/project? This is probably not needed and unlikely to make much
            //  of a difference.
        }
    })
}

private data class IntentToCall(
    val call: String,
    val user: String,
    val project: String?,
)

private class CertificateCache(private val ipcClient: IpcClient) {
    private val knownCertificates = atomic<List<NativeJWTValidation>>(emptyList())

    private suspend fun attemptValidate(signedIntent: String): IntentToCall? {
        for (validator in knownCertificates.value) {
            try {
                val intent = validator.validateOrNull(signedIntent) v@{ jwt ->
                    IntentToCall(
                        jwt_get_grant(jwt, "call")?.toKStringFromUtf8() ?: return@v null,
                        jwt_get_grant(jwt, "username")?.toKStringFromUtf8() ?: return@v null,
                        jwt_get_grant(jwt, "project")?.toKStringFromUtf8(),
                    )
                }

                if (intent != null) return intent
            } catch (ignored: Throwable) {
                // Ignored
            }
        }

        return null
    }

    private suspend fun renewCertificates() {
        knownCertificates.getAndSet(
            ipcClient.sendRequest(MessageSigningIpc.browse, Unit).keys.map {
                NativeJWTValidation(normalizeCertificate(it.key))
            }
        )
    }

    suspend fun validate(signedIntent: String): IntentToCall? {
        val res = attemptValidate(signedIntent)
        if (res == null) renewCertificates()
        else return res

        return attemptValidate(signedIntent)
    }
}
