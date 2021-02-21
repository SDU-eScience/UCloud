package dk.sdu.cloud.calls.client

import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.server.CausedBy
import dk.sdu.cloud.service.Loggable
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import org.slf4j.MDC

class CallTracing : OutgoingCallFilter.BeforeCall() {
    override fun canUseContext(ctx: OutgoingCall): Boolean {
        return ctx is OutgoingHttpCall
    }

    override suspend fun run(context: OutgoingCall, callDescription: CallDescription<*, *, *>) {
        if (context.causedBy == null) {
            // Note: The current implementation depends on the CallLogging feature of ktor.
            // This will handle the internals of putting a value in SL4Js MDC when switching between different
            // coroutines.
            val ingoingJobId = MDC.getCopyOfContextMap()["request-id"] ?: return
            context.causedBy = ingoingJobId
        }

        val causedBy = context.causedBy
        if (causedBy != null) {
            when (context) {
                is OutgoingHttpCall -> {
                    context.builder.header(HttpHeaders.CausedBy, causedBy)
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
        internal val CAUSED_BY_KEY = AttributeKey<String>("caused-by-key")
    }
}

var OutgoingCall.causedBy: String?
    get() = attributes.getOrNull(CallTracing.CAUSED_BY_KEY)
    set(value) {
        if (value == null) {
            attributes.remove(CallTracing.CAUSED_BY_KEY)
        } else {
            attributes[CallTracing.CAUSED_BY_KEY] = value
        }
    }

