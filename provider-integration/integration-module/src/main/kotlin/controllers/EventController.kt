package dk.sdu.cloud.controllers

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.debug.*
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.IpcServer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.project.api.v2.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.toSimpleString
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.atomic.AtomicLong

@Serializable
data class EventPauseState(val isPaused: Boolean)

@Serializable
sealed class UCloudCoreEvent {
    abstract val id: String

    @Serializable
    data class Project(
        override val id: String,
        val event: ProjectNotification
    ) : UCloudCoreEvent()

    @Serializable
    data class Allocation(
        override val id: String,
        val event: DepositNotification
    ) : UCloudCoreEvent()
}

object EventIpc : IpcContainer("events") {
    val updatePauseState = updateHandler("updatePauseState", EventPauseState.serializer(), Unit.serializer())
    val retrievePauseState = updateHandler("retrievePauseState", Unit.serializer(), EventPauseState.serializer())

    val browse = browseHandler(Unit.serializer(), PageV2.serializer(UCloudCoreEvent.serializer()))
    val delete = deleteHandler(FindByStringId.serializer(), Unit.serializer())
    val replay = updateHandler("replay", Unit.serializer(), Unit.serializer())
}

class EventController(
    private val controllerContext: ControllerContext,
) : Controller, IpcController {
    private val debug: DebugSystem
        get() = controllerContext.pluginContext.debugSystem
    private var isPaused = false

    private val nextPull = AtomicLong(0L)
    private var pullRequested = false

    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        if (!controllerContext.configuration.shouldRunServerCode()) return

        controllerContext.configuration.plugins.temporary
            .onConnectionCompleteHandlers.add(this@EventController::onConnectionComplete)

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

    override fun configureIpc(server: IpcServer) {
        server.addHandler(EventIpc.updatePauseState.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            isPaused = request.isPaused
        })

        server.addHandler(EventIpc.retrievePauseState.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            EventPauseState(isPaused)
        })

        val projectPrefix = "p-"
        val depositPrefix = "a-"
        val rpcClient = controllerContext.pluginContext.rpcClient

        server.addHandler(EventIpc.browse.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

            val projectBatch = ProjectNotifications.retrieve.call(Unit, rpcClient).orThrow().responses.map {
                UCloudCoreEvent.Project(projectPrefix + it.id, it)
            }

            val allocationBatch = DepositNotifications.retrieve.call(Unit, rpcClient).orThrow().responses.map {
                UCloudCoreEvent.Allocation(depositPrefix + it.id, it)
            }

            PageV2(Int.MAX_VALUE, projectBatch + allocationBatch, null)
        })

        server.addHandler(EventIpc.delete.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

            if (request.id.startsWith(depositPrefix)) {
                DepositNotifications.markAsRead.call(
                    bulkRequestOf(DepositNotificationsMarkAsReadRequestItem(request.id.removePrefix(depositPrefix))),
                    rpcClient
                ).orThrow()
            } else if (request.id.startsWith(projectPrefix)) {
                ProjectNotifications.markAsRead.call(
                    bulkRequestOf(FindByStringId(request.id.removePrefix(projectPrefix))),
                    rpcClient
                ).orThrow()
            }
        })

        server.addHandler(EventIpc.replay.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            if (isPaused) {
                throw RPCException(
                    "The system is currently paused, you cannot trigger a replay now",
                    HttpStatusCode.BadRequest
                )
            }

            triggerPullRequest()
            nextRescan = 0L
        })
    }

    private fun triggerPullRequest() {
        pullRequested = true
    }

    private fun startLoop() {
        ProcessingScope.launch {
            while (isActive) {
                if (isPaused) {
                    delay(1000)
                    continue
                }

                loopNotifications()
            }
        }

        ProcessingScope.launch {
            while (isActive) {
                if (isPaused) {
                    delay(1000)
                    continue
                }

                loopScans()
            }
        }
    }

    private suspend fun loopNotifications() {
        val now = Time.now()
        val nextPullValue = nextPull.get()
        if (now >= nextPullValue || pullRequested) {
            pullRequested = false
            try {
                // NOTE(Dan): It is crucial that events are _always_ processed in this order, regardless of
                // how the pull requests arrive at the provider. We must know about a new project _before_ any
                // allocations are processed.
                debug.useContext(
                    DebugContextType.BACKGROUND_TASK,
                    "Project notifications",
                    MessageImportance.IMPLEMENTATION_DETAIL,
                    block = {
                        processProjects()
                    }
                )

                debug.useContext(
                    DebugContextType.BACKGROUND_TASK,
                    "Allocation notifications",
                    MessageImportance.IMPLEMENTATION_DETAIL,
                    block = {
                        processAllocations()
                    }
                )
            } catch (ex: Throwable) {
                log.info(
                    "Caught exception while processing notifications from UCloud/Core: " +
                            ex.stackTraceToString()
                )
            }

            // NOTE(Dan): We always pull once a minute, even if we aren't told to do so.
            // NOTE(Dan): Only change the value if it wasn't changed while we were processing the notifications. If
            // it has changed while processing, it means we have events already pending again.
            nextPull.compareAndSet(nextPullValue, now + 60_000L)
        }
        delay(1000)
    }

    private var nextRescan = 0L
    private suspend fun loopScans() {
        val now = Time.now()
        try {
            if (now >= nextRescan) {
                debug.useContext(
                    DebugContextType.BACKGROUND_TASK,
                    "Allocation scan",
                    MessageImportance.IMPLEMENTATION_DETAIL,
                    block = {
                        scanAllocations()
                    }
                )
                nextRescan = now + (1000L * 60 * 60)
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
        controllerContext.configuration.plugins.projects ?: return

        // NOTE(Dan): Fetch another batch of notifications from the server
        val batch = ProjectNotifications.retrieve.call(
            Unit,
            controllerContext.pluginContext.rpcClient
        ).orThrow()
        if (batch.responses.isEmpty()) return

        val projectWasSynchronized = synchronizeProjects(batch.responses.map { it.project })
        val toAcknowledge = batch.responses.zip(projectWasSynchronized).mapNotNull { (req, resp) ->
            if (resp) FindByStringId(req.id)
            else null
        }

        // Notify UCloud/Core about the notifications we handled successfully
        if (toAcknowledge.isNotEmpty()) {
            ProjectNotifications.markAsRead.call(
                BulkRequest(toAcknowledge),
                controllerContext.pluginContext.rpcClient
            ).orThrow()
        }

        debug.normal("Handled ${toAcknowledge.size} project notifications")
    }

    private suspend fun synchronizeProject(id: String): Boolean {
        val project = Projects.retrieve.call(
            ProjectsRetrieveRequest(id, includeMembers = true, includeGroups = true, includeSettings = true,
                includePath = true),
            controllerContext.pluginContext.rpcClient
        ).orNull() ?: return false

        return synchronizeProjects(listOf(project)).single()
    }

    private suspend fun synchronizeProjects(projects: List<Project>): List<Boolean> {
        val projectPlugin = controllerContext.configuration.plugins.projects ?: return emptyList()

        val projectsToIgnore = HashSet<String>()
        val acknowledgedEvents = ArrayList<Boolean>()

        for (project in projects) {
            val success = try {
                if (!project.specification.canConsumeResources) {
                    projectsToIgnore.add(project.id)
                } else {
                    with(controllerContext.pluginContext) {
                        with(projectPlugin) {
                            onProjectUpdated(project)
                        }
                    }
                }

                true
            } catch (ex: Throwable) {
                log.warn(
                    "Caught an exception while handling project update: ${project}\n" +
                            ex.toReadableStacktrace().toString()
                )
                false
            }

            acknowledgedEvents.add(success)
        }

        if (projectsToIgnore.isNotEmpty()) {
            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                        insert into events.projects_to_ignore(project_id)
                        select unnest(:project_ids::text[]) on conflict do nothing
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindList("project_ids", projectsToIgnore.toList())
                    }
                )
            }
        }

        return acknowledgedEvents
    }

    // Allocations
    // ================================================================================================================
    private suspend fun processAllocations() {
        // TODO(Dan): Very fragile piece of code that will break if too many people don't connect to the system. We
        //  are only marking notifications as read if we can resolve the owner. This can easily lead to a situation
        //  where the batch is always full of items that we cannot handle/starvation problem.
        val batch = DepositNotifications.retrieve.call(
            Unit,
            controllerContext.pluginContext.rpcClient
        ).orThrow()

        if (batch.responses.isEmpty()) return

        var notifications = batch.responses
        val projects = batch.responses.mapNotNull { (it.owner as? WalletOwner.Project)?.projectId }.toSet()
        if (projects.isNotEmpty()) {
            val projectsToIgnore = HashSet<String>()
            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                        select project_id
                        from events.projects_to_ignore
                        where project_id = some(:project_ids::text[])
                    """
                ).useAndInvoke(
                    prepare = {
                        bindList("project_ids", projects.toList())
                    },
                    readRow = { row ->
                        projectsToIgnore.add(row.getString(0)!!)
                    }
                )
            }

            notifications = notifications.filter { event ->
                val owner = event.owner

                val shouldIgnoreProject = owner is WalletOwner.Project && owner.projectId in projectsToIgnore
                if (shouldIgnoreProject) return@filter false

                val isRegistered = ResourceOwnerWithId.load(owner, controllerContext.pluginContext) != null
                if (!isRegistered) return@filter false

                true
            }
        }

        for (notification in notifications) {
            notifyAllocationForWorkspace(notification.owner, notification.category.name)
        }

        val items = ArrayList<DepositNotificationsMarkAsReadRequestItem>()
        for (notification in notifications) {
            items.add(DepositNotificationsMarkAsReadRequestItem(notification.id))
        }

        if (items.isNotEmpty()) {
            DepositNotifications.markAsRead.call(
                BulkRequest(items),
                controllerContext.pluginContext.rpcClient
            ).orThrow()
        }

        debug.normal("Processed ${items.size} allocations")
    }

    private suspend fun notifyAllocationForWorkspace(
        owner: WalletOwner?,
        filterByCategory: String?,
    ) {
        val combinedAllocations = HashMap<Pair<WalletOwner, String>, AllocationNotification.Combined>()
        val singleAllocations = ArrayList<AllocationNotification.Single>()

        var next: String? = null
        while (true) {
            val page = AccountingV2.browseProviderAllocations.call(
                WalletsRetrieveProviderSummaryRequest(
                    filterOwnerId = when (owner) {
                        is WalletOwner.User -> owner.username
                        is WalletOwner.Project -> owner.projectId
                        else -> null
                    },
                    filterOwnerIsProject = when (owner) {
                        is WalletOwner.Project -> true
                        is WalletOwner.User -> false
                        null -> null
                    },
                    filterCategory = filterByCategory,
                    next = next,
                ),
                controllerContext.pluginContext.rpcClient
            ).orThrow()

            val observedOwners = HashSet<WalletOwner>()
            val now = Time.now()
            for (alloc in page.items) {
                val allocOwner = alloc.owner
                if (allocOwner !in observedOwners) {
                    observedOwners.add(allocOwner)
                    if (allocOwner is WalletOwner.Project) {
                        synchronizeProject(allocOwner.projectId)
                    }
                }

                val allocRange = alloc.notBefore..(alloc.notAfter ?: Long.MAX_VALUE)
                if (now !in allocRange) continue

                val key = allocOwner to alloc.categoryId.name
                val resolvedOwner = ResourceOwnerWithId.load(allocOwner, controllerContext.pluginContext) ?: continue

                val combined = combinedAllocations.getOrPut(key) {
                    AllocationNotification.Combined(0L, resolvedOwner, alloc.categoryId.name,
                        alloc.categoryId.productType)
                }

                combined.quota += alloc.quota

                val single = AllocationNotification.Single(alloc.quota, resolvedOwner, alloc.categoryId.name,
                    alloc.categoryId.productType, alloc.id)

                singleAllocations.add(single)
            }

            next = page.next ?: break
        }

        for (single in singleAllocations) {
            notifyPlugins(single)
        }

        for (combined in combinedAllocations.values) {
            notifyPlugins(combined)
        }
    }

    private suspend fun notifyPlugins(notification: AllocationNotification) {
        var hasPreviouslyBeenHandled = true
        dbConnection.withSession { session ->
            when (notification) {
                is AllocationNotification.Combined -> {
                    session.prepareStatement(
                        """
                            insert into events.wallets_handled(workspace, category)
                            values (:workspace, :category)
                            on conflict do nothing
                            returning workspace
                        """
                    ).useAndInvoke(
                        prepare = {
                            bindString("workspace", notification.owner.toResourceOwner().toSimpleString())
                            bindString("category", notification.productCategory)
                        },
                        readRow = { hasPreviouslyBeenHandled = false }
                    )
                }
                is AllocationNotification.Single -> {
                    session.prepareStatement(
                        """
                            insert into events.allocations_handled(id)
                            values (:id)
                            on conflict (id) do nothing
                            returning id
                        """
                    ).useAndInvoke(
                        prepare = { bindString("id", notification.allocationId) },
                        readRow = { hasPreviouslyBeenHandled = false }
                    )
                }
            }
        }

        val plugins = controllerContext.configuration.plugins

        val allocationPlugin = plugins.allocations.entries.find { (t) -> t.type == notification.productType }?.value
        if (allocationPlugin != null) {
            with(controllerContext.pluginContext) {
                with(allocationPlugin) {
                    notify(listOf(notification), !hasPreviouslyBeenHandled)
                }
            }
        }

        val allPlugin = controllerContext.configuration.plugins.allocations.entries.find { (t) ->
            t == ConfigSchema.Plugins.AllocationsProductType.ALL
        }?.value

        allPlugin?.run {
            with(controllerContext.pluginContext) {
                notify(listOf(notification), !hasPreviouslyBeenHandled)
            }
        }

        if (!hasPreviouslyBeenHandled) {
            plugins.resourcePlugins().asSequence()
                .filter { plugin ->
                    plugin.productAllocation.any { it.category == notification.productCategory }
                }
                .forEach { plugin ->
                    with(controllerContext.pluginContext) {
                        with(plugin) {
                            notifyAllocationCompleteInServerMode(notification)
                        }
                    }
                }
        }
    }

    private suspend fun onConnectionComplete(ucloudId: String, localId: Int) {
        notifyAllocationForWorkspace(WalletOwner.User(ucloudId), null)
    }

    private suspend fun scanAllocations() {
        notifyAllocationForWorkspace(null, null)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
