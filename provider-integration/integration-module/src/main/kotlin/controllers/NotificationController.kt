package dk.sdu.cloud.controllers

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.debug.*
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.project.api.v2.ProjectNotifications
import dk.sdu.cloud.project.api.v2.ProjectNotificationsProvider
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import kotlin.math.min
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.atomic.AtomicLong

class NotificationController(
    private val controllerContext: ControllerContext,
) : Controller {
    private val debug: DebugSystem?
        get() = controllerContext.pluginContext.debugSystem

    private val nextPull = AtomicLong(0L)

    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        if (!controllerContext.configuration.shouldRunServerCode()) return

        controllerContext.configuration.plugins.temporary
            .onConnectionCompleteHandlers.add(this@NotificationController::onConnectionComplete)

        val provider = controllerContext.configuration.core.providerId
        val projectApi = ProjectNotificationsProvider(provider)
        val allocationApi = DepositNotificationsProvider(provider)

        implement(projectApi.pullRequest) {
            triggerPullRequest()
            ok(Unit)
        }

        implement(allocationApi.pullRequest) {
            triggerPullRequest()
            ok(Unit)
        }

        startLoop()
    }

    private fun triggerPullRequest() {
        nextPull.set(0L)
    }

    private fun startLoop() {
        ProcessingScope.launch {
            while (isActive) {
                loopNotifications()
            }
        }

        ProcessingScope.launch {
            while (isActive) {
                loopScans()
            }
        }
    }

    private suspend fun loopNotifications() {
        val now = Time.now()
        if (now >= nextPull.get()) {
            try {
                // NOTE(Dan): It is crucial that events are _always_ processed in this order, regardless of
                // how the pull requests arrive at the provider. We must know about a new project _before_ any
                // allocations are processed.
                debug.enterContext("Project notifications") {
                    processProjects()
                }

                debug.enterContext("Allocation notifications") {
                    processAllocations()
                    processAllocationsDetailed()
                }
            } catch (ex: Throwable) {
                log.info(
                    "Caught exception while processing notifications from UCloud/Core: " +
                        ex.stackTraceToString()
                )
            }

            // NOTE(Dan): We always pull once a minute, even if we aren't told to do so.
            nextPull.set(now + 60_000L)
        }
        delay(1000)
    }

    private var nextRescan = 0L
    private suspend fun loopScans() {
        val now = Time.now()
        try {
            if (now >= nextRescan) {
                debug.enterContext("Allocation scan") {
                    scanAllocations()
                }
                nextRescan = now + (1000L * 60 * 60 * 3)
            }
            delay(1000)
        } catch (ex: Throwable) {
            log.info(
                "Caught an exception while scanning allocations. We will retry again in a minute: " +
                    ex.stackTraceToString()
            )
            nextRescan = now + (1000L * 60)
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
                        log.warn(
                            "Caught an exception while handling project update: ${item.project}\n" +
                                ex.stackTraceToString()
                        )
                    }
                }
            }
        }

        // Notify UCloud/Core about the notifications we handled successfully
        if (output.isNotEmpty()) {
            ProjectNotifications.markAsRead.call(
                BulkRequest(output),
                controllerContext.pluginContext.rpcClient
            ).orThrow()
        }

        debug.logD(
            "Handled ${output.size} project notifications",
            Unit.serializer(),
            Unit,
            if (output.size == 0) MessageImportance.IMPLEMENTATION_DETAIL
            else MessageImportance.THIS_IS_NORMAL
        )
    }

    // Allocations
    // ================================================================================================================
    private suspend fun processAllocationsDetailed() {
        // TODO(Dan): Very fragile piece of code that will break if too many people don't connect to the system. We
        //  are only marking notifications as read if we can resolve the owner. This can easily lead to a situation
        //  where the batch is always full of items that we cannot handle/starvation problem.
        val batch = DepositNotifications.retrieve.call(
            Unit,
            controllerContext.pluginContext.rpcClient
        ).orThrow()

        if (batch.responses.isEmpty()) return

        val output = arrayOfNulls<OnResourceAllocationResult>(batch.responses.size)
        val notificationsByType = HashMap<ProductType, ArrayList<Pair<Int, DetailedAllocationNotification>>>()

        outer@ for ((idx, notification) in batch.responses.withIndex()) {
            val combinedProviderSummary = Wallets.retrieveProviderSummary.call(
                WalletsRetrieveProviderSummaryRequest(
                    filterOwnerId = when (val owner = notification.owner) {
                        is WalletOwner.User -> owner.username
                        is WalletOwner.Project -> owner.projectId
                    },
                    filterOwnerIsProject = notification.owner is WalletOwner.Project,
                    filterCategory = notification.category.name
                ),
                controllerContext.pluginContext.rpcClient
            ).orThrow().items
            for (providerSummary in combinedProviderSummary) {
                val productType = providerSummary.productType

                val list = notificationsByType[productType] ?: ArrayList()
                notificationsByType[productType] = list

                list.add(Pair(idx, prepareDetailedAllocationNotification(providerSummary) ?: continue))
            }
        }

        for ((type, list) in notificationsByType) {
            val plugins = controllerContext.configuration.plugins
            val allocationPlugin = plugins.allocations.entries.find { (t) -> t.type == type }?.value

            with(controllerContext.pluginContext) {
                if (allocationPlugin != null) {
                    val response = with(allocationPlugin) {
                        onResourceAllocationDetailed(list.map { it.second })
                    }

                    for ((request, pluginResponse) in list.zip(response)) {
                        val (origIdx, _) = request
                        output[origIdx] = pluginResponse
                    }
                }
            }

            for ((_, notification) in list) {
                notifyPlugins(notification)
            }
        }

        val allPlugin = controllerContext.configuration.plugins.allocations.entries.find { (t) ->
            t == ConfigSchema.Plugins.AllocationsProductType.ALL
        }?.value

        allPlugin?.run {
            with(controllerContext.pluginContext) {
                onResourceAllocationDetailed(notificationsByType.entries.flatMap { (_, allocs) -> allocs.map { it.second } })
            }
        }

        val items = ArrayList<DepositNotificationsMarkAsReadRequestItem>()
        for ((index, res) in output.withIndex()) {
            if (res == null) continue

            items.add(
                DepositNotificationsMarkAsReadRequestItem(
                    batch.responses[index].id,
                    (res as? OnResourceAllocationResult.ManageThroughProvider)?.uniqueId
                )
            )
        }

        if (items.isNotEmpty()) {
            DepositNotifications.markAsRead.call(
                BulkRequest(items),
                controllerContext.pluginContext.rpcClient
            ).orThrow()
        }

        debug.logD(
            "Processed ${items.size} allocations",
            Unit.serializer(),
            Unit,
            if (items.size == 0) MessageImportance.IMPLEMENTATION_DETAIL
            else MessageImportance.THIS_IS_NORMAL
        )
    }

    private suspend fun processAllocations() {
        // TODO(Dan): Very fragile piece of code that will break if too many people don't connect to the system. We
        //  are only marking notifications as read if we can resolve the owner. This can easily lead to a situation
        //  where the batch is always full of items that we cannot handle/starvation problem.
        val batch = DepositNotifications.retrieve.call(
            Unit,
            controllerContext.pluginContext.rpcClient
        ).orThrow()

        if (batch.responses.isEmpty()) return

        val output = arrayOfNulls<OnResourceAllocationResult>(batch.responses.size)
        val notificationsByType = HashMap<ProductType, ArrayList<Pair<Int, AllocationNotification>>>()

        outer@ for ((idx, notification) in batch.responses.withIndex()) {
            val combinedProviderSummary = Wallets.retrieveProviderSummary.call(
                WalletsRetrieveProviderSummaryRequest(
                    filterOwnerId = when (val owner = notification.owner) {
                        is WalletOwner.User -> owner.username
                        is WalletOwner.Project -> owner.projectId
                    },
                    filterOwnerIsProject = notification.owner is WalletOwner.Project,
                    filterCategory = notification.category.name
                ),
                controllerContext.pluginContext.rpcClient
            ).orThrow().items
            for (providerSummary in combinedProviderSummary) {
                val productType = providerSummary.productType

                val list = notificationsByType[productType] ?: ArrayList()
                notificationsByType[productType] = list
                val newPair = Pair(idx, prepareAllocationNotification(providerSummary) ?: continue)
                val existing = list.find { it.first == newPair.first && it.second.owner == newPair.second.owner && it.second.productCategory == newPair.second.productCategory }

                if (existing != null) {
                    existing.second.balance += newPair.second.balance
                } else {
                    list.add(newPair)
                }
            }
        }

        println(notificationsByType)

        for ((type, list) in notificationsByType) {
            val plugins = controllerContext.configuration.plugins
            val allocationPlugin = plugins.allocations.entries.find { (t) -> t.type == type }?.value

            with(controllerContext.pluginContext) {
                if (allocationPlugin != null) {
                    val response = with(allocationPlugin) {
                        onResourceAllocation(list.map { it.second })
                    }

                    for ((request, pluginResponse) in list.zip(response)) {
                        val (origIdx, _) = request
                        output[origIdx] = pluginResponse
                    }
                }
            }

            for ((_, notification) in list) {
                notifyPlugins(notification)
            }
        }

        val allPlugin = controllerContext.configuration.plugins.allocations.entries.find { (t) ->
            t == ConfigSchema.Plugins.AllocationsProductType.ALL
        }?.value

        allPlugin?.run {
            with(controllerContext.pluginContext) {
                onResourceAllocation(notificationsByType.entries.flatMap { (_, allocs) -> allocs.map { it.second } })
            }
        }

        val items = ArrayList<DepositNotificationsMarkAsReadRequestItem>()
        for ((index, res) in output.withIndex()) {
            if (res == null) continue

            items.add(
                DepositNotificationsMarkAsReadRequestItem(
                    batch.responses[index].id,
                    (res as? OnResourceAllocationResult.ManageThroughProvider)?.uniqueId
                )
            )
        }

        if (items.isNotEmpty()) {
            DepositNotifications.markAsRead.call(
                BulkRequest(items),
                controllerContext.pluginContext.rpcClient
            ).orThrow()
        }

        debug.logD(
            "Processed ${items.size} allocations",
            Unit.serializer(),
            Unit,
            if (items.size == 0) MessageImportance.IMPLEMENTATION_DETAIL
            else MessageImportance.THIS_IS_NORMAL
        )
    }

    private suspend fun notifyPlugins(notification: AllocationNotification) {
        val plugins = controllerContext.configuration.plugins
        plugins.resourcePlugins().asSequence()
            .filter { plugin ->
                plugin.productAllocation.any { it.category == notification.productCategory }
            }
            .forEach { plugin ->
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        onAllocationCompleteInServerMode(notification)
                    }
                }
            }
    }

    private suspend fun notifyPlugins(notification: DetailedAllocationNotification) {
        val plugins = controllerContext.configuration.plugins
        plugins.resourcePlugins().asSequence()
            .filter { plugin ->
                plugin.productAllocation.any { it.category == notification.productCategory }
            }
            .forEach { plugin ->
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        onAllocationCompleteInServerModeDetailed(notification)
                    }
                }
            }
    }

    private fun onConnectionComplete(ucloudId: String, localId: Int) {
        runBlocking {
            val notifications = ArrayList<AllocationNotification>()
            var next: String? = null
            while (true) {
                val providerSummary = Wallets.retrieveProviderSummary.call(
                    WalletsRetrieveProviderSummaryRequest(
                        filterOwnerId = ucloudId,
                        filterOwnerIsProject = false,
                        itemsPerPage = 250,
                        next = next,
                    ),
                    controllerContext.pluginContext.rpcClient
                ).orThrow()

                for (summary in providerSummary.items) {
                    notifications.add(
                        AllocationNotification(
                            min(summary.maxUsableBalance, summary.maxPromisedBalance),
                            ResourceOwnerWithId.User(ucloudId, localId),
                            summary.categoryId.name,
                            summary.productType,
                        )
                    )
                }

                next = providerSummary.next
                if (next == null) break
            }

            for (notification in notifications) {
                val plugins = controllerContext.configuration.plugins
                val allocationPlugin = plugins.allocations.entries.find { (t) -> t.type == notification.productType }
                    ?.value
                if (allocationPlugin != null) {
                    with(controllerContext.pluginContext) {
                        with(allocationPlugin) {
                            onResourceAllocation(listOf(notification))
                        }
                    }
                }

                notifyPlugins(notification)
            }


            val allPlugin = controllerContext.configuration.plugins.allocations.entries.find { (t) ->
                t == ConfigSchema.Plugins.AllocationsProductType.ALL
            }?.value

            if (allPlugin != null) {
                with(controllerContext.pluginContext) {
                    with(allPlugin) {
                        onResourceAllocation(notifications)
                    }
                }
            }
        }
    }

    private suspend fun prepareDetailedAllocationNotification(summary: ProviderWalletSummary): DetailedAllocationNotification? {
        return DetailedAllocationNotification(
            min(summary.maxUsableBalance, summary.maxPromisedBalance),
            ResourceOwnerWithId.load(summary.owner, controllerContext.pluginContext) ?: run {
                log.info("Could not find UID/GID for ${summary.owner}")
                return null
            },
            summary.id,
            summary.categoryId.name,
            summary.productType
        )
    }

    private suspend fun prepareAllocationNotification(summary: ProviderWalletSummary): AllocationNotification? {
        return AllocationNotification(
            min(summary.maxUsableBalance, summary.maxPromisedBalance),
            ResourceOwnerWithId.load(summary.owner, controllerContext.pluginContext) ?: run {
                log.info("Could not find UID/GID for ${summary.owner}")
                return null
            },
            summary.categoryId.name,
            summary.productType
        )
    }

    private suspend fun scanAllocations() {
        var allocationsScanned = 0
        var next: String? = null
        while (currentCoroutineContext().isActive) {
            val providerSummaryResponse = Wallets.retrieveProviderSummary.call(
                WalletsRetrieveProviderSummaryRequest(next = next),
                controllerContext.pluginContext.rpcClient
            ).orThrow()

            next = providerSummaryResponse.next

            val providerSummary = providerSummaryResponse.items
            allocationsScanned += providerSummaryResponse.items.size
            if (providerSummary.isEmpty()) break

            val notificationsByType = providerSummary.groupBy { it.productType }

            for ((type, list) in notificationsByType) {
                val plugin = controllerContext.configuration.plugins.allocations.entries.find { (pluginType) ->
                    pluginType.type == type
                }?.value ?: continue

                dispatchSyncToPlugin(plugin, list)
            }

            val allPlugin = controllerContext.configuration.plugins.allocations.entries.find { (pluginType) ->
                pluginType == ConfigSchema.Plugins.AllocationsProductType.ALL
            }?.value

            if (allPlugin != null) dispatchSyncToPlugin(allPlugin, providerSummary)
        }

        debug.logD(
            "Scanned $allocationsScanned allocations",
            Unit.serializer(),
            Unit,
            if (allocationsScanned == 0) MessageImportance.IMPLEMENTATION_DETAIL
            else MessageImportance.THIS_IS_NORMAL
        )
    }

    private suspend fun dispatchSyncToPlugin(plugin: AllocationPlugin, list: List<ProviderWalletSummary>) {
        with(controllerContext.pluginContext) {
            with(plugin) {
                val items = list.mapNotNull { prepareAllocationNotification(it) }
                if (items.isNotEmpty()) onResourceSynchronization(items)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
