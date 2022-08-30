package dk.sdu.cloud.calls.client

import dk.sdu.cloud.calls.CallDescription
import io.ktor.client.request.*
import io.ktor.http.*

class OutgoingAuthFilter : OutgoingCallFilter.BeforeCall() {
    override fun canUseContext(ctx: OutgoingCall): Boolean {
        return ctx is OutgoingHttpCall
    }

    override suspend fun run(context: OutgoingCall, callDescription: CallDescription<*, *, *>, request: Any?) {
        context as OutgoingHttpCall
        val token = context.attributes.outgoingAuthToken
        if (token != null) {
            context.builder.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
