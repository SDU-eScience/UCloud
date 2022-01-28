package dk.sdu.cloud.calls.client

import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.freeze
import kotlin.native.concurrent.SharedImmutable

@SharedImmutable
private val outgoingAuthTokenKey = AttributeKey<String>("outgoing-auth").freeze()

var AttributeContainer.outgoingAuthToken: String?
    get() = getOrNull(outgoingAuthTokenKey)
    set(value) = if (value != null) set(outgoingAuthTokenKey, value) else remove(outgoingAuthTokenKey)

fun ClientAndBackend.bearerAuth(
    bearerToken: String
): AuthenticatedClient {
    return AuthenticatedClient(client, backend) { it.attributes.outgoingAuthToken = bearerToken }
}

fun ClientAndBackend.noAuth(): AuthenticatedClient {
    return AuthenticatedClient(client, backend) {}
}
