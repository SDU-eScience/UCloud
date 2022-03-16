package dk.sdu.cloud.controllers

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.http.OutgoingCallResponse
import dk.sdu.cloud.http.RpcServer
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.project.api.v2.ProjectNotifications
import dk.sdu.cloud.project.api.v2.ProjectNotificationsProvider
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ProjectController(
    private val controllerContext: ControllerContext,
) : Controller {
    override fun RpcServer.configure() {
        if (controllerContext.configuration.serverMode != ServerMode.Server) return

        val provider = controllerContext.configuration.core.providerId
        val api = ProjectNotificationsProvider(provider)

        implement(api.pullRequest) {
            pullAndNotify()
            OutgoingCallResponse.Ok(Unit)
        }

        // NOTE(Dan): We will immediately trigger a pullAndNotify to retrieve anything we might have in the backlog
        runBlocking {
            pullAndNotify()
        }
    }

    private suspend fun pullAndNotify() {
        // NOTE(Dan): If we don't have a project plugin, then let's just assume that we don't care about
        // projects in our system.
        val projectPlugin = controllerContext.plugins.projects ?: return

        // NOTE(Dan): Fetch another batch of notifications from the server
        val batch = ProjectNotifications.retrieve.call(
            Unit,
            controllerContext.pluginContext.rpcClient
        ).orThrow()
        if (batch.responses.isEmpty()) return

        // Handle notifications by dispatching to the plugin.
        val output = ArrayList<FindByStringId>()
        for (item in batch.responses) {
            with(controllerContext.pluginContext) {
                with(projectPlugin) {
                    try {
                        onProjectUpdated(item.project)
                        output.add(FindByStringId(item.id))
                    } catch (ex: Throwable) {
                        log.warn("Caught an exception while handling project update: ${item.project}\n" +
                            ex.stackTraceToString())
                    }
                }
            }
        }

        // Notify UCloud/Core about the notifications we handled successfully
        if (output.isEmpty()) return
        ProjectNotifications.markAsRead.call(
            BulkRequest(output),
            controllerContext.pluginContext.rpcClient
        ).orThrow()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
