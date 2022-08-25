package dk.sdu.cloud.calls.client

import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.AttributeKey
import io.ktor.client.request.*

class OutgoingProject : OutgoingCallFilter.BeforeCall() {
    override fun canUseContext(ctx: OutgoingCall): Boolean = ctx is OutgoingHttpCall

    override suspend fun run(context: OutgoingCall, callDescription: CallDescription<*, *, *>, request: Any?) {
        when (context) {
            is OutgoingHttpCall -> {
                val project = context.project
                if (project != null) context.builder.header("Project", project)
            }
        }
    }
}

private val projectKey = AttributeKey<String>("outgoing-project")

var OutgoingCall.project: String?
    get() = attributes.getOrNull(projectKey)
    internal set(value) {
        if (value != null) attributes[projectKey] = value
        else attributes.remove(projectKey)
    }
