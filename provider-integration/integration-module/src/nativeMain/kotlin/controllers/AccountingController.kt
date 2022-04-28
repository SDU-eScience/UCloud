package dk.sdu.cloud.controllers

import dk.sdu.cloud.ProductReferenceWithoutProvider
import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.accounting.api.DepositNotification
import dk.sdu.cloud.accounting.api.DepositNotifications
import dk.sdu.cloud.accounting.api.DepositNotificationsMarkAsReadRequestItem
import dk.sdu.cloud.accounting.api.DepositNotificationsProvider
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.http.OutgoingCallResponse
import dk.sdu.cloud.http.RpcServer
import dk.sdu.cloud.plugins.OnResourceAllocationResult
import dk.sdu.cloud.plugins.ResourcePlugin
import dk.sdu.cloud.plugins.rpcClient
import kotlinx.coroutines.runBlocking

class AccountingController(
    private val controllerContext: ControllerContext,
) : Controller {
    private val log = Logger("AccountingController")
    override fun RpcServer.configure() {
        if (controllerContext.configuration.serverMode != ServerMode.Server) return

        val provider = controllerContext.configuration.core.providerId
        val api = DepositNotificationsProvider(provider)

        implement(api.pullRequest) {
            pullAndNotify()
            OutgoingCallResponse.Ok(Unit)
        }

        // NOTE(Dan): We will immediately trigger a pullAndNotify to retrieve anything we might have in the backlog
        runBlocking {
            try {
                pullAndNotify()
            } catch (ex: Throwable) {
                // Ignored
            }
        }
    }

    private suspend fun pullAndNotify() {
        val batch = DepositNotifications.retrieve.call(
            Unit,
            controllerContext.pluginContext.rpcClient
        ).orThrow()

        if (batch.responses.isEmpty()) return

        val output = arrayOfNulls<OnResourceAllocationResult>(batch.responses.size)

        with(controllerContext.configuration.plugins) {
            dispatchToPlugin(batch.responses, fileCollections.values, output)
            dispatchToPlugin(batch.responses, files.values, output)
            dispatchToPlugin(batch.responses, jobs.values, output)
        }

        val items = ArrayList<DepositNotificationsMarkAsReadRequestItem>()
        for ((index, res) in output.withIndex()) {
            if (res == null) {
                log.warn("Could not find plugin for ${batch.responses[index].category}")
                continue
            }

            items.add(DepositNotificationsMarkAsReadRequestItem(
                batch.responses[index].id,
                (res as? OnResourceAllocationResult.ManageThroughProvider)?.uniqueId)
            )
        }

        DepositNotifications.markAsRead.call(
            BulkRequest(items),
            controllerContext.pluginContext.rpcClient
        ).orThrow()
    }

    private suspend fun <P : ResourcePlugin<*, *, *, *>> dispatchToPlugin(
        batch: List<DepositNotification>,
        plugins: Collection<P>,
        output: Array<OnResourceAllocationResult?>
    ) {
        data class IndexAndNotification(val index: Int, val notification: DepositNotification)
        val pluginToNotifications = HashMap<P, ArrayList<IndexAndNotification>>()

        for ((index, notification) in batch.withIndex()) {
            val responsiblePlugin = plugins.find { plugin ->
                plugin.productAllocation.any { it.category == notification.category.name }
            } ?: continue

            responsiblePlugin as? ResourcePlugin<*, *, *, *> ?: error("Expected a resource based plugin from $plugins")

            pluginToNotifications[responsiblePlugin] = (pluginToNotifications[responsiblePlugin] ?: ArrayList()).also {
                it.add(IndexAndNotification(index, notification))
            }
        }

        for ((plugin, notifications) in pluginToNotifications) {
            with(controllerContext.pluginContext) {
                with(plugin) {
                    val response = onResourceAllocation(BulkRequest(notifications.map { it.notification }))
                    for ((respIdx, resourceResponse) in response.withIndex()) {
                        val outputIndex = notifications[respIdx].index
                        output[outputIndex] = resourceResponse
                    }
                }
            }
        }
    }
}

