package dk.sdu.cloud.providers

import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer

data class UCloudClient(val nonBlockingClient: AuthenticatedClient)

fun UCloudClient(
    refreshToken: String,
    host: String,
    tls: Boolean = true,
    port: Int? = null
): UCloudClient {
    val actualPort = port ?: if (tls) 443 else 80
    val client = RpcClient()
    OutgoingHttpRequestInterceptor()
        .install(
            client,
            FixedOutgoingHostResolver(HostInfo(host, if (tls) "https" else "http", actualPort))
        )

    val authenticator = RefreshingJWTAuthenticator(
        client,
        JwtRefresher.Provider(refreshToken)
    )

    return runBlocking { UCloudClient(authenticator.authenticateClient(OutgoingHttpCall)) }
}

fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.call(
    request: R,
    authenticatedBackend: UCloudClient,
): IngoingCallResponse<S, E> {
    val c = authenticatedBackend.nonBlockingClient

    return runBlocking {
        @Suppress("UNCHECKED_CAST")
        c.client.call(
            this@call,
            request,
            c.backend as OutgoingCallCompanion<OutgoingCall>,
            c.authenticator,
            afterHook = c.afterHook
        )
    }
}
