package dk.sdu.cloud.calls.client

import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

private val outgoingAuthTokenKey = AttributeKey<String>("outgoing-auth")

var AttributeContainer.outgoingAuthToken: String?
    get() = getOrNull(outgoingAuthTokenKey)
    set(value) = if (value != null) set(outgoingAuthTokenKey, value) else remove(outgoingAuthTokenKey)

fun ClientAndBackend.bearerAuth(
    bearerToken: String
): AuthenticatedClient {
    return AuthenticatedClient(client, backend) { it.attributes.outgoingAuthToken = bearerToken }
}

class OutgoingAuthFilter : OutgoingCallFilter.BeforeCall() {
    override fun canUseContext(ctx: OutgoingCall): Boolean {
        return ctx is OutgoingHttpCall
    }

    override suspend fun run(context: OutgoingCall, callDescription: CallDescription<*, *, *>) {
        context as OutgoingHttpCall
        val token = context.attributes.outgoingAuthToken
        if (token != null) {
            context.builder.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
