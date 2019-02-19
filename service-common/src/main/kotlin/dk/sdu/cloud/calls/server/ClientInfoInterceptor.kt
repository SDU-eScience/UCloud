package dk.sdu.cloud.calls.server

import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.service.Loggable
import io.ktor.application.call
import io.ktor.features.origin
import io.ktor.request.userAgent

/**
 * Intercepts information about a client
 *
 * This information includes:
 *
 * - User Agent
 * - Hostname
 */
class ClientInfoInterceptor : IngoingCallFilter.BeforeParsing() {
    override fun canUseContext(ctx: IngoingCall): Boolean = true

    override suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>) {
        val remoteHost = when (context) {
            is WSCall -> context.session.underlyingSession.call.request.origin.remoteHost
            is HttpCall -> context.call.request.origin.remoteHost
            else -> null
        }

        if (remoteHost != null) context.remoteHost = remoteHost

        val userAgent = when (context) {
            is WSCall -> context.session.underlyingSession.call.request.userAgent()
            is HttpCall -> context.call.request.userAgent()
            else -> null
        }

        if (userAgent != null) context.userAgent = userAgent
    }

    fun register(server: RpcServer) {
        server.attachFilter(this)
    }

    companion object : Loggable {
        override val log = logger()

        internal val remoteHostKey = AttributeKey<String>("server-remote-host")
        internal val userAgentKey = AttributeKey<String>("server-user-agent")
    }
}

var IngoingCall.remoteHost: String?
    get() = attributes.getOrNull(ClientInfoInterceptor.remoteHostKey)
    internal set(value) {
        if (value != null) attributes[ClientInfoInterceptor.remoteHostKey] = value
        else attributes.remove(ClientInfoInterceptor.remoteHostKey)
    }

var IngoingCall.userAgent: String?
    get() = attributes.getOrNull(ClientInfoInterceptor.userAgentKey)
    internal set(value) {
        if (value != null) attributes[ClientInfoInterceptor.userAgentKey] = value
        else attributes.remove(ClientInfoInterceptor.userAgentKey)
    }
