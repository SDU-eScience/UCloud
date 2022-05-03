package dk.sdu.cloud.controllers

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.http.OutgoingCallResponse
import dk.sdu.cloud.http.RpcServer
import dk.sdu.cloud.plugins.AllocationNotification
import dk.sdu.cloud.plugins.OnResourceAllocationResult
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.project.api.v2.ProjectNotifications
import dk.sdu.cloud.project.api.v2.ProjectNotificationsProvider
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NotificationController(
    private val controllerContext: ControllerContext,
) : Controller {
    private val nextPull = atomic(0L)

    override fun RpcServer.configure() {
        if (controllerContext.configuration.serverMode != ServerMode.Server) return

        val provider = controllerContext.configuration.core.providerId
        val projectApi = ProjectNotificationsProvider(provider)
        val allocationApi = DepositNotificationsProvider(provider)

        implement(projectApi.pullRequest) {
            nextPull.value = 0L // Trigger a new pull
            OutgoingCallResponse.Ok(Unit)
        }

        implement(allocationApi.pullRequest) {
            nextPull.value = 0L // Trigger a new pull
            OutgoingCallResponse.Ok(Unit)
        }

        startLoop()
    }

    private fun startLoop() {
        ProcessingScope.launch {
            while (isActive) {
                val now = Time.now()
                if (now >= nextPull.value) {
                    try {
                        processProjects()
                        processAllocations()
                    } catch (ex: Throwable) {
                        log.info("Caught exception while processing notifications from UCloud/Core: " +
                            ex.stackTraceToString())
                    }

                    // NOTE(Dan): We always pull once a minute, even if we aren't told to do so.
                    nextPull.value = now + 60_000L
                }
                delay(1000)
            }
        }
    }

    // Projects
    // ================================================================================================================
    private suspend fun processProjects() {
        // NOTE(Dan): If we don't have a project plugin, then let's just assume that we don't care about
        // projects in our system.
        val projectPlugin = controllerContext.configuration.plugins.projects ?: return

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

    // Allocations
    // ================================================================================================================
    private suspend fun processAllocations() {
        fun findProductType(category: ProductCategoryId): ProductType? {
            val products = controllerContext.configuration.products ?: return null
            return when {
                category.name in (products.compute ?: emptyMap()) -> ProductType.COMPUTE
                category.name in (products.storage ?: emptyMap()) -> ProductType.STORAGE
                else -> null
            }
        }

        val projectPlugin = controllerContext.configuration.plugins?.projects
        val batch = DepositNotifications.retrieve.call(
            Unit,
            controllerContext.pluginContext.rpcClient
        ).orThrow()

        if (batch.responses.isEmpty()) return

        val output = arrayOfNulls<OnResourceAllocationResult>(batch.responses.size)
        val notificationsByType = HashMap<ProductType, ArrayList<Pair<Int, AllocationNotification>>>()

        outer@for ((idx, notification) in batch.responses.withIndex()) {
            val productType = findProductType(notification.category) ?: continue@outer

            val list = notificationsByType[productType] ?: ArrayList()
            notificationsByType[productType] = list

            val notification = AllocationNotification(
                notification.balance,
                when (val owner = notification.owner) {
                    is WalletOwner.User -> {
                        val username = owner.username
                        AllocationNotification.Owner.User(
                            username,
                            UserMapping.ucloudIdToLocalId(username) ?: continue@outer
                        )
                    }

                    is WalletOwner.Project -> {
                        val projectId = owner.projectId
                        if (projectPlugin == null) continue@outer

                        val gid = with(controllerContext.pluginContext) {
                            with(projectPlugin) {
                                lookupLocalId(projectId)
                            }
                        }

                        if (gid == null) {
                            log.info("Could not find GID for $projectId")
                            continue@outer
                        }

                        AllocationNotification.Owner.Project(projectId, gid)
                    }
                },
                notification.id,
                notification.category.name,
                productType
            )

            list.add(Pair(idx, notification))
        }

        for ((type, list) in notificationsByType) {
            val plugin = controllerContext.configuration.plugins?.allocations?.get(type) ?: continue

            with(controllerContext.pluginContext) {
                val response = with(plugin) {
                    onResourceAllocation(list.map { it.second })
                }

                for ((request, response) in list.zip(response)) {
                    val (origIdx, _) = request
                    output[origIdx] = response
                }
            }
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

    companion object : Loggable {
        override val log = logger()
    }
}
