package dk.sdu.cloud.calls.client

import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.service.Loggable
import kotlin.reflect.KClass

interface OutgoingCall {
    val attributes: AttributeContainer
}

interface OutgoingCallCompanion<Ctx : OutgoingCall> {
    val klass: KClass<Ctx>
    val attributes: AttributeContainer
}

interface OutgoingRequestInterceptor<Ctx : OutgoingCall, Companion : OutgoingCallCompanion<Ctx>> {
    val companion: Companion

    suspend fun <R : Any, S : Any, E : Any> prepareCall(
        call: CallDescription<R, S, E>,
        request: R
    ): Ctx

    suspend fun <R : Any, S : Any, E : Any> finalizeCall(
        call: CallDescription<R, S, E>,
        request: R,
        ctx: Ctx
    ): IngoingCallResponse<S, E>
}

interface OutgoingContextFilter {
    fun canUseContext(ctx: OutgoingCall): Boolean
}

sealed class OutgoingCallFilter : OutgoingContextFilter {
    abstract class BeforeCall : OutgoingCallFilter() {
        abstract suspend fun run(context: OutgoingCall, callDescription: CallDescription<*, *, *>)
    }
}

class RpcClient {
    private val requestInterceptors = HashMap<OutgoingCallCompanion<*>, OutgoingRequestInterceptor<*, *>>()
    private val callFilters = ArrayList<OutgoingCallFilter>()

    fun attachFilter(filter: OutgoingCallFilter) {
        log.debug("Attaching filter: $filter")
        callFilters.add(filter)
    }

    fun <Ctx : OutgoingCall, Companion : OutgoingCallCompanion<Ctx>> attachRequestInterceptor(
        interceptor: OutgoingRequestInterceptor<Ctx, Companion>
    ) {
        log.debug("Attaching interceptor for ${interceptor.companion}: $interceptor")
        requestInterceptors[interceptor.companion] = interceptor
    }

    suspend fun <R : Any, S : Any, E : Any, Ctx : OutgoingCall, Companion : OutgoingCallCompanion<Ctx>> call(
        callDescription: CallDescription<R, S, E>,
        request: R,
        backend: Companion,
        beforeFilters: (suspend (Ctx) -> Unit)? = null,
        afterFilters: (suspend (Ctx) -> Unit)? = null
    ): IngoingCallResponse<S, E> {
        val interceptor =
            requestInterceptors[backend] ?: throw IllegalStateException("No handler exists for this backend: $backend")

        @Suppress("UNCHECKED_CAST")
        interceptor as OutgoingRequestInterceptor<Ctx, Companion>

        val ctx = interceptor.prepareCall(callDescription, request)
        beforeFilters?.invoke(ctx)

        callFilters.filterIsInstance<OutgoingCallFilter.BeforeCall>().forEach {
            if (it.canUseContext(ctx)) {
                it.run(ctx, callDescription)
            }
        }

        afterFilters?.invoke(ctx)

        return interceptor.finalizeCall(callDescription, request, ctx)
    }

    companion object : Loggable {
        override val log = logger()
    }
}

suspend fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.call(
    request: R,
    clientAndBackend: ClientAndBackend
): IngoingCallResponse<S, E> {
    @Suppress("UNCHECKED_CAST")
    return clientAndBackend.client.call(
        this,
        request,
        clientAndBackend.backend as OutgoingCallCompanion<OutgoingCall>
    )
}

data class ClientAndBackend(
    val client: RpcClient,
    val backend: OutgoingCallCompanion<*>
)

data class AuthenticatedClient(
    val client: RpcClient,
    val companion: OutgoingCallCompanion<*>,
    val afterFilters: (suspend (OutgoingCall) -> Unit)? = null,
    val authenticator: suspend (OutgoingCall) -> Unit
) {
    companion object {
        fun <Ctx : OutgoingCall, Companion : OutgoingCallCompanion<Ctx>> create(
            client: RpcClient,
            companion: Companion,
            authenticator: suspend (Ctx) -> Unit,
            afterFilters: (suspend (Ctx) -> Unit)? = null
        ): AuthenticatedClient {
            return AuthenticatedClient(
                client,
                companion,
                authenticator = {
                    @Suppress("UNCHECKED_CAST")
                    authenticator(it as Ctx)
                },
                afterFilters = {
                    @Suppress("UNCHECKED_CAST")
                    afterFilters?.invoke(it as Ctx)
                }
            )
        }
    }
}

fun AuthenticatedClient.withFixedHost(hostInfo: HostInfo): AuthenticatedClient {
    return AuthenticatedClient(
        client,
        companion,
        authenticator = {
            authenticator(it)
        },
        afterFilters = {
            it.attributes.outgoingTargetHost = hostInfo
        }
    )
}

fun AuthenticatedClient.withoutAuthentication(): ClientAndBackend = ClientAndBackend(client, companion)

suspend fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.call(
    request: R,
    authenticatedBackend: AuthenticatedClient
): IngoingCallResponse<S, E> {
    @Suppress("UNCHECKED_CAST")
    return authenticatedBackend.client.call(
        this,
        request,
        authenticatedBackend.companion as OutgoingCallCompanion<OutgoingCall>,
        authenticatedBackend.authenticator,
        authenticatedBackend.afterFilters
    )
}
