package dk.sdu.cloud.calls.server

import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.WSRequest
import dk.sdu.cloud.service.Loggable
import io.ktor.application.call
import io.ktor.request.header

class ProjectInterceptor {
    fun register(server: RpcServer) {
        server.attachFilter(object : IngoingCallFilter.BeforeParsing() {
            override fun canUseContext(ctx: IngoingCall): Boolean = true

            override suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>) {
                context.project = readProject(context)
            }
        })
    }

    private fun readProject(context: IngoingCall): String? {
        return when (context) {
            is HttpCall -> context.call.request.header("Project")
            is WSCall -> context.frameNode[WSRequest.PROJECT_FIELD]?.takeIf { !it.isNull && it.isTextual }?.textValue()
            else -> {
                log.warn("Unable to extract project from call context: $context")
                null
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        internal val projectKey = AttributeKey<String>("project")
    }
}

var IngoingCall.project: String?
    get() = attributes.getOrNull(ProjectInterceptor.projectKey)
    internal set(value) {
        if (value != null) attributes[ProjectInterceptor.projectKey] = value
        else attributes.remove(ProjectInterceptor.projectKey)
    }
