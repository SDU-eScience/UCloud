package dk.sdu.cloud.calls.client

import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription

data class HostInfo(
    val host: String,
    val scheme: String? = null,
    val port: Int? = null
)

class FixedOutgoingHostResolver(private val host: HostInfo) : OutgoingHostResolver {
    override fun resolveEndpoint(callDescription: CallDescription<*, *, *>): HostInfo {
        return host
    }
}

interface OutgoingHostResolver {
    fun resolveEndpoint(callDescription: CallDescription<*, *, *>): HostInfo
}

class OutgoingHostResolverInterceptor(
    private val resolver: OutgoingHostResolver
) : OutgoingCallFilter.BeforeCall() {
    override fun canUseContext(ctx: OutgoingCall): Boolean = true

    override suspend fun run(context: OutgoingCall, callDescription: CallDescription<*, *, *>) {
        context.attributes.outgoingTargetHost = resolver.resolveEndpoint(callDescription)
    }

    companion object {
        internal val hostKey = AttributeKey<HostInfo>("target-host")
    }
}

var AttributeContainer.outgoingTargetHost: HostInfo
    get() = this[OutgoingHostResolverInterceptor.hostKey]
    set(value) = set(OutgoingHostResolverInterceptor.hostKey, value)
