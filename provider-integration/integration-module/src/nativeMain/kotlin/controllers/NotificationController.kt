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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

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
            triggerPullRequest()
            OutgoingCallResponse.Ok(Unit)
        }

        implement(allocationApi.pullRequest) {
            triggerPullRequest()
            OutgoingCallResponse.Ok(Unit)
        }

        startLoop()
    }

    private fun triggerPullRequest() {
        nextPull.value = 0L
    }

    private fun startLoop() {
        ProcessingScope.launch {
            while (isActive) {
                val now = Time.now()
                if (now >= nextPull.value) {
                    try {
                        // NOTE(Dan): It is crucial that events are _always_ processed in this order, regardless of
                        // how the pull requests arrive at the provider. We must know about a new project _before_ any
                        // allocations are processed.
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

        ProcessingScope.launch {
            var nextRescan = 0L
            while (isActive) {
                val now = Time.now()
                try {
                    if (now >= nextRescan) {
                        scanAllocations()
                        nextRescan = now + (1000L * 60 * 60 * 3)
                    }
                    delay(1000)
                } catch (ex: Throwable) {
                    log.info("Caught an exception while scanning allocations. We will retry again in a minute: " +
                        ex.stackTraceToString())
                    nextRescan = now + (1000L * 60)
                }
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
        val batch = DepositNotifications.retrieve.call(
            Unit,
            controllerContext.pluginContext.rpcClient
        ).orThrow()

        if (batch.responses.isEmpty()) return

        val output = arrayOfNulls<OnResourceAllocationResult>(batch.responses.size)
        val notificationsByType = HashMap<ProductType, ArrayList<Pair<Int, AllocationNotification>>>()

        outer@for ((idx, notification) in batch.responses.withIndex()) {
            val providerSummary = Wallets.retrieveProviderSummary.call(
                WalletsRetrieveProviderSummaryRequest(
                    filterOwnerId = when (val owner = notification.owner) {
                        is WalletOwner.User -> owner.username
                        is WalletOwner.Project -> owner.projectId
                    },
                    filterOwnerIsProject = notification.owner is WalletOwner.Project,
                    filterCategory = notification.category.name
                ),
                controllerContext.pluginContext.rpcClient
            ).orThrow().items.singleOrNull() ?: continue

            val productType = providerSummary.productType

            val list = notificationsByType[productType] ?: ArrayList()
            notificationsByType[productType] = list

            list.add(Pair(idx, prepareAllocationNotification(providerSummary) ?: continue@outer))
        }

        for ((type, list) in notificationsByType) {
            val plugin = controllerContext.configuration.plugins.allocations[type] ?: continue

            with(controllerContext.pluginContext) {
                val response = with(plugin) {
                    onResourceAllocation(list.map { it.second })
                }

                for ((request, pluginResponse) in list.zip(response)) {
                    val (origIdx, _) = request
                    output[origIdx] = pluginResponse
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

        if (items.isNotEmpty()) {
            DepositNotifications.markAsRead.call(
                BulkRequest(items),
                controllerContext.pluginContext.rpcClient
            ).orThrow()
        }
    }

    private suspend fun prepareAllocationNotification(summary: ProviderWalletSummary): AllocationNotification? {
        val projectPlugin = controllerContext.configuration.plugins.projects
        return AllocationNotification(
            min(summary.maxUsableBalance, summary.maxPromisedBalance),
            when (val owner = summary.owner) {
                is WalletOwner.User -> {
                    val username = owner.username
                    val uid = UserMapping.ucloudIdToLocalId(username)

                    if (uid == null) {
                        log.info("Could not find UID for $username")
                        return null
                    }

                    AllocationNotification.Owner.User(username, uid)
                }

                is WalletOwner.Project -> {
                    val projectId = owner.projectId
                    if (projectPlugin == null) return null

                    val gid = with(controllerContext.pluginContext) {
                        with(projectPlugin) {
                            lookupLocalId(projectId)
                        }
                    }

                    if (gid == null) {
                        log.info("Could not find GID for $projectId")
                        return null
                    }

                    AllocationNotification.Owner.Project(projectId, gid)
                }
            },
            summary.id,
            summary.categoryId.name,
            summary.productType
        )
    }

    private suspend fun scanAllocations() {
        var next: String? = null
        while (currentCoroutineContext().isActive) {
            val providerSummaryResponse = Wallets.retrieveProviderSummary.call(
                WalletsRetrieveProviderSummaryRequest(next = next),
                controllerContext.pluginContext.rpcClient
            ).orThrow()

            next = providerSummaryResponse.next

            val providerSummary = providerSummaryResponse.items
            if (providerSummary.isEmpty()) break

            val notificationsByType = providerSummary.groupBy { it.productType }

            for ((type, list) in notificationsByType) {
                val plugin = controllerContext.configuration.plugins.allocations[type] ?: continue

                with(controllerContext.pluginContext) {
                    with(plugin) {
                        val items = list.mapNotNull { prepareAllocationNotification(it) }
                        if (items.isNotEmpty()) onResourceSynchronization(items)
                    }
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
