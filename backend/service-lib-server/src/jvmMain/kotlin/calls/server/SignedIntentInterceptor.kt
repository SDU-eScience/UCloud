package dk.sdu.cloud.calls.server

import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.service.Loggable
import io.ktor.application.call
import io.ktor.request.header

class SignedIntentInterceptor {
    fun register(server: RpcServer) {
        server.attachFilter(object : IngoingCallFilter.BeforeParsing() {
            override fun canUseContext(ctx: IngoingCall): Boolean = true

            override suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>) {
                context.signedIntent = readSignedIntent(context)
            }
        })
    }

    private fun readSignedIntent(context: IngoingCall): String? {
        return when (context) {
            is HttpCall -> context.call.request.header("UCloud-Signed-Intent")
//            is WSCall -> context.request.project
            else -> {
                log.warn("Unable to extract signed intent from call context: $context")
                null
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        internal val key = AttributeKey<String>("signed-intent")
    }
}

var IngoingCall.signedIntent: String?
    get() = attributes.getOrNull(SignedIntentInterceptor.key)
    internal set(value) {
        if (value != null) attributes[SignedIntentInterceptor.key] = value
        else attributes.remove(SignedIntentInterceptor.key)
    }
