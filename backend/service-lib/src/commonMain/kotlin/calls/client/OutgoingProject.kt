package dk.sdu.cloud.calls.client

import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import io.ktor.client.request.header

class OutgoingProject : OutgoingCallFilter.BeforeCall() {
    override fun canUseContext(ctx: OutgoingCall): Boolean = ctx is OutgoingHttpCall

    override suspend fun run(context: OutgoingCall, callDescription: CallDescription<*, *, *>) {
        when (context) {
            is OutgoingHttpCall -> {
                val project = context.project
                if (project != null) context.builder.header("Project", project)
            }
        }
    }

    companion object {
        internal val key = AttributeKey<String>("outgoing-project")
    }
}

var OutgoingCall.project: String?
    get() = attributes.getOrNull(OutgoingProject.key)
    internal set(value) {
        if (value != null) attributes[OutgoingProject.key] = value
        else attributes.remove(OutgoingProject.key)
    }
