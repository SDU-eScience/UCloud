package dk.sdu.cloud.controllers

import dk.sdu.cloud.ProductReferenceWithoutProvider
import dk.sdu.cloud.ServerMode
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
import dk.sdu.cloud.plugins.ProductBasedPlugins
import dk.sdu.cloud.plugins.ResourcePlugin
import dk.sdu.cloud.plugins.rpcClient
import kotlinx.coroutines.runBlocking

class AccountingController(
    private val controllerContext: ControllerContext,
) : Controller {
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
            pullAndNotify()
        }
    }

    private suspend fun pullAndNotify() {
        val batch = DepositNotifications.retrieve.call(
            Unit,
            controllerContext.pluginContext.rpcClient
        ).orThrow()

        if (batch.responses.isEmpty()) return

        val output = arrayOfNulls<OnResourceAllocationResult>(batch.responses.size)

        with(controllerContext.plugins) {
            if (fileCollection != null) dispatchToPlugin(batch.responses, fileCollection, output)
            if (files != null) dispatchToPlugin(batch.responses, files, output)
            if (compute != null) dispatchToPlugin(batch.responses, compute, output)
        }

        val items = ArrayList<DepositNotificationsMarkAsReadRequestItem>()
        for ((index, res) in output.withIndex()) {
            if (res == null) {
                throw IllegalStateException("Could not find plugin for ${batch.responses[index].category}")
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

    private suspend fun dispatchToPlugin(
        batch: List<DepositNotification>,
        plugins: ProductBasedPlugins<*>,
        output: Array<OnResourceAllocationResult?>
    ) {
        data class IndexAndNotification(val index: Int, val notification: DepositNotification)
        val pluginToNotifications = HashMap<ResourcePlugin<*, *, *, *>, ArrayList<IndexAndNotification>>()

        for ((index, notification) in batch.withIndex()) {
            val responsiblePlugin = runCatching {
                plugins.lookup(ProductReferenceWithoutProvider("unknown", notification.category.name))
            }.getOrNull() ?: continue

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
