package dk.sdu.cloud

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.IngoingCall
import dk.sdu.cloud.calls.server.IngoingCallFilter
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.controllers.MessageSigningIpc
import dk.sdu.cloud.debug.detailD
import dk.sdu.cloud.ipc.IpcClient
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.provider.api.IntegrationProvider
import dk.sdu.cloud.utils.doesCallRequireSignature
import dk.sdu.cloud.utils.doesIntentMatchCall
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.concurrent.atomic.AtomicReference

suspend fun loadE2EValidation(rpcServer: RpcServer, pluginContext: PluginContext) {
    val config = pluginContext.config
    if (config.rawServerMode() != ServerMode.User) return
    val connectionPlugin = config.plugins.connection ?: return
    val signingRequired = pluginContext.run { connectionPlugin.run { requireMessageSigning() } }
    if (!signingRequired) return

    rpcServer.attachFilter(object : IngoingCallFilter.AfterParsing() {
        val invalidSignature = HttpStatusCode(482, "Invalid signature")
        val certCache = CertificateCache(pluginContext.ipcClient)

        override fun canUseContext(ctx: IngoingCall): Boolean = true
        override suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>, request: Any) {
            val providerId = pluginContext.config.core.providerId
            if (!doesCallRequireSignature(providerId, call.fullName)) return

            val signedIntent: String = when (val sctx = context) {
                is HttpCall -> {
                    sctx.ktor.call.request.header(IntegrationProvider.UCLOUD_SIGNED_INTENT)
                }

                is WSCall -> {
                    sctx.request.signedIntent
                }

                else -> error("Unexpected server context of type $sctx")
            } ?: run {
                debugSystem.detailD("Invalid signature: No signed intent found", Unit.serializer(), Unit)
                throw RPCException.fromStatusCode(invalidSignature)
            }

            val validIntent = certCache.validate(signedIntent) ?: run {
                debugSystem.detailD("Invalid signature: Metadata did not validate", Unit.serializer(), Unit)
                throw RPCException.fromStatusCode(invalidSignature)
            }
            if (!doesIntentMatchCall(providerId, validIntent, call)) {
                debugSystem.detailD(
                    "Invalid signature: Call does not match intention",
                    JsonObject.serializer(),
                    JsonObject(
                        mapOf(
                            "intendedCall" to JsonPrimitive(validIntent.call),
                            "rawCall" to JsonPrimitive(call.fullName),
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

data class IntentToCall(
    val call: String,
    val user: String,
    val project: String?,
)

private class CertificateCache(private val ipcClient: IpcClient) {
    private val knownCertificates = AtomicReference<List<JWTVerifier>>(emptyList())

    private suspend fun attemptValidate(signedIntent: String): IntentToCall? {
        for (validator in knownCertificates.get()) {
            try {
                val jwt = runCatching { validator.verify(signedIntent) }.getOrNull() ?: continue
                val claims = jwt.claims
                val intent = IntentToCall(
                    claims["call"]?.takeIf { !it.isNull }?.asString() ?: continue,
                    claims["username"]?.takeIf { !it.isNull }?.asString() ?: continue,
                    claims["project"]?.takeIf { !it.isNull }?.asString(),
                )

                return intent
            } catch (ignored: Throwable) {
                // Ignored
            }
        }

        return null
    }

    private suspend fun renewCertificates() {
        knownCertificates.getAndSet(
            ipcClient.sendRequest(MessageSigningIpc.browse, Unit).keys.map {
                createVerifier(Algorithm.RSA256(parsePublicKey(it.key), null))
            }
        )
    }

    suspend fun validate(signedIntent: String): IntentToCall? {
        val res = attemptValidate(signedIntent)
        if (res == null) renewCertificates()
        else return res

        return attemptValidate(signedIntent)
    }

    // NOTE(Dan): Copy & pasted from InternalTokenValidationJWT to make a few minor adjustments
    private fun createVerifier(algorithm: Algorithm): JWTVerifier {
        return JWT.require(algorithm).build()
    }

    private fun parsePublicKey(key: String): RSAPublicKey {
        val normalizedKey = key
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\r", "")
            .replace("\n", "")

        val decoded = Base64.getDecoder().decode(normalizedKey)
        val rsa = KeyFactory.getInstance("RSA")
        return rsa.generatePublic(X509EncodedKeySpec(decoded)) as RSAPublicKey
    }
}
