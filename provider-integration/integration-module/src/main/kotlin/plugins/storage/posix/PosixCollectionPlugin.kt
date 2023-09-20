package dk.sdu.cloud.plugins.storage.posix

import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.controllers.ResourceOwnerWithId
import dk.sdu.cloud.debug.DebugContextType
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.plugins.storage.PathConverter
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.utils.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

object PosixCollectionIpc : IpcContainer("posixfscoll") {
    val retrieveCollections = retrieveHandler(ResourceOwner.serializer(), PageV2.serializer(FindByPath.serializer()))
}

class PosixCollectionPlugin : FileCollectionPlugin {
    override val pluginTitle: String = "Posix"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<ProductV2> = emptyList()
    private lateinit var pluginConfig: ConfigSchema.Plugins.FileCollections.Posix
    private var initializedProjects = HashMap<ResourceOwnerWithId, List<PathConverter.Collection>>()
    private lateinit var partnerPlugin: PosixFilesPlugin
    lateinit var pathConverter: PathConverter
        private set
    private val mutex = Mutex()
    private lateinit var ctx: PluginContext

    private val accountingExtension: String?
        get() = pluginConfig.extensions.reportStorageUsage ?: pluginConfig.extensions.accounting

    override fun configure(config: ConfigSchema.Plugins.FileCollections) {
        this.pluginConfig = config as ConfigSchema.Plugins.FileCollections.Posix
    }

    override suspend fun PluginContext.initialize() {
        ctx = this
        partnerPlugin = (config.plugins.files[pluginName] as? PosixFilesPlugin)
            ?: error("Posix file-collection plugins requires a matching partner plugin of type Posix with name '$pluginName'")
        pathConverter = PathConverter(this)

        if (config.shouldRunServerCode()) {
            ipcServer.addHandler(PosixCollectionIpc.retrieveCollections.handler { _, request ->
                PageV2(
                    Int.MAX_VALUE,
                    ResourceOwnerWithId.load(request, ctx)?.let { owner ->
                        locateAndRegisterCollections(owner).map { FindByPath(it.localPath) }
                    } ?: emptyList(),
                    null
                )
            })
        }
    }

    override suspend fun PluginContext.onAllocationCompleteInServerModeTotal(notification: AllocationNotification.Combined) {
        locateAndRegisterCollections(notification.owner)
    }

    private suspend fun PluginContext.locateAndRegisterCollections(
        owner: ResourceOwnerWithId
    ): List<PathConverter.Collection> {
        mutex.withLock {
            val cached = initializedProjects[owner]
            if (cached != null) return cached
        }

        val collections = ArrayList<PathConverter.Collection>()

        run {
            val product = productAllocation.firstOrNull() ?: return@run

            val extension = pluginConfig.extensions.driveLocator ?: pluginConfig.extensions.additionalCollections
            if (extension != null) {
                retrieveCollections.invoke(ctx, extension, owner).forEach {
                    collections.add(
                        PathConverter.Collection(owner.toResourceOwner(), it.title, it.path, product)
                    )
                }
            }
        }

        mutex.withLock {
            val cached = initializedProjects[owner]
            if (cached != null) return cached
            initializedProjects[owner] = collections
        }

        if (collections.isNotEmpty()) {
            pathConverter.registerCollectionWithUCloud(collections)
        }

        return collections
    }

    override suspend fun RequestContext.initInUserMode(owner: ResourceOwner) {
        ipcClient.sendRequest(
            PosixCollectionIpc.retrieveCollections,
            owner
        )

        runCatching {
            val ucloudUsername = owner.createdBy
            ipcClient.sendRequest(
                PosixCollectionIpc.retrieveCollections,
                ResourceOwner(ucloudUsername, project = null)
            )
        }
    }

    override suspend fun RequestContext.retrieveProducts(
        knownProducts: List<ProductReference>
    ): BulkResponse<FSSupport> {
        return with(partnerPlugin) {
            retrieveProducts(knownProducts)
        }
    }

    private var nextScan = 0L
    override suspend fun PluginContext.runMonitoringLoopInServerMode() {
        if (accountingExtension == null) return

        val productCategories = productAllocation.map { it.category }.toSet()
        while (currentCoroutineContext().isActive) {
            loop(productCategories)
        }
    }

    // NOTE(Dan): Extracted because of an issue with GraalVM not supporting loops with coroutines directly inside them
    private suspend fun PluginContext.loop(productCategories: Set<String>) {
        try {
            val now = Time.now()
            if (now >= nextScan) {
                debugSystem.useContext(DebugContextType.BACKGROUND_TASK, "Posix collection monitoring") {
                    productCategories.forEachGraal { category ->
                        var next: String? = null
                        var shouldBreak = false
                        whileGraal({ currentCoroutineContext().isActive && !shouldBreak }) {
                            val summary = WalletAllocationsV2.retrieveProviderAllocations.call(
                                WalletsRetrieveProviderSummaryRequest(
                                    filterCategory = category,
                                    itemsPerPage = 250,
                                    next = next,
                                ),
                                rpcClient
                            ).orThrow()

                            summary.items.associateByGraal { it.id }.values.forEachGraal inner@{ item ->
                                val resourceOwner = ResourceOwnerWithId.load(item.owner, this@loop) ?: return@inner
                                val colls = locateAndRegisterCollections(resourceOwner)
                                    .filter { it.product.category == category }

                                val bytesUsed = colls.sumOf {
                                    runCatching { calculateUsage(it) }.getOrElse { 0 }
                                }

                                reportConcurrentUseStorage(item.owner, item.categoryId.toV2Id(), bytesUsed)

                                next = summary.next
                                if (next == null) shouldBreak = true
                            }
                        }
                    }
                }
            }

            delay(5000)
        } catch (ex: Throwable) {
            log.info("Caught exception while monitoring Posix collections: ${ex.stackTraceToString()}")
        } finally {
            nextScan = Time.now() + (1000L * 60 * 60 * 4)
        }
    }

    private suspend fun calculateUsage(coll: PathConverter.Collection): Long {
        return when (val cfg = accountingExtension) {
            null -> 0
            else -> {
                calculateUsage.invoke(ctx, cfg, CalculateUsageRequest(coll.localPath)).bytesUsed
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        private val retrieveCollections =
            extension(ResourceOwnerWithId.serializer(), ListSerializer(PosixCollectionFromExtension.serializer()))
        private val calculateUsage = extension(CalculateUsageRequest.serializer(), CalculateUsageResponse.serializer())
    }
}

@Serializable
data class CalculateUsageRequest(
    val path: String,
)

@Serializable
data class CalculateUsageResponse(
    val bytesUsed: Long,
)

@Serializable
private data class PosixCollectionFromExtension(
    val path: String,
    val title: String,
)
