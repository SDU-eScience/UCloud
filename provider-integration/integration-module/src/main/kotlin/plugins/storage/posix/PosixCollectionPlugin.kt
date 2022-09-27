package dk.sdu.cloud.plugins.storage.posix

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.controllers.ResourceOwnerWithId
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.debug.enterContext
import dk.sdu.cloud.debug.logD
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.plugins.storage.PathConverter
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.forEachGraal
import dk.sdu.cloud.utils.whileGraal
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.text.SimpleDateFormat

class PosixCollectionPlugin : FileCollectionPlugin {
    override val pluginTitle: String = "Posix"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()
    private lateinit var pluginConfig: ConfigSchema.Plugins.FileCollections.Posix
    private var initializedProjects = HashMap<ResourceOwnerWithId, List<PathConverter.Collection>>()
    private val mutex = Mutex()

    override fun configure(config: ConfigSchema.Plugins.FileCollections) {
        this.pluginConfig = config as ConfigSchema.Plugins.FileCollections.Posix
    }

    override suspend fun PluginContext.onAllocationComplete(notification: AllocationNotification) {
        locateAndRegisterCollections(notification.owner)
    }

    private suspend fun PluginContext.locateAndRegisterCollections(
        owner: ResourceOwnerWithId
    ): List<PathConverter.Collection> {
        val pathConverter = PathConverter(this)
        mutex.withLock {
            val cached = initializedProjects[owner]
            if (cached != null) return cached
        }

        data class CollWithProduct(
            val title: String,
            val pathPrefix: String,
            val product: ProductReferenceWithoutProvider
        )

        val homes = HashMap<String, CollWithProduct>()
        val collections = ArrayList<PathConverter.Collection>()

        run {
            val product = productAllocation.firstOrNull() ?: return@run

            run {
                // Simple mappers
                pluginConfig.simpleHomeMapper.forEach { home ->
                    homes[home.prefix] = CollWithProduct(home.title, home.prefix, product)
                }
            }

            run {
                // Extensions
                val extension = pluginConfig.extensions.additionalCollections
                if (extension != null) {
                    retrieveCollections.invoke(extension, owner).forEach {
                        collections.add(
                            PathConverter.Collection(owner.toResourceOwner(), it.title, it.path, product)
                        )
                    }
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

    override suspend fun RequestContext.retrieveProducts(
        knownProducts: List<ProductReference>
    ): BulkResponse<FSSupport> {
        return BulkResponse(productAllocation.map { ref ->
            FSSupport(
                ProductReference(ref.id, ref.category, config.core.providerId),
                FSProductStatsSupport(),
                FSCollectionSupport(),
                FSFileSupport()
            )
        })
    }

    private suspend fun ArrayList<ResourceChargeCredits>.sendBatch(client: AuthenticatedClient, db: DBContext) {
        val filteredBatch = this.filter { it.periods > 0 }
        if (filteredBatch.isEmpty()) return
        FileCollectionsControl.chargeCredits.call(BulkRequest(filteredBatch), client).orThrow()

        // TODO(Brian): Bad code coming up. Can be more easily improved with Postgres.
        // NOTE(Brian): Add or update date and time of last scan (read: last charge) for each resource.
        db.withSession { session ->
            filteredBatch.forEach {
                session.prepareStatement(
                """
                    insert into posix_storage_scan
                    (id, last_scan) values (:id, datetime())
                    on conflict (id) do update set last_scan = datetime()
                """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("id", it.id)
                    }
                )
            }
        }

        clear()
    }

    private suspend fun ArrayList<ResourceChargeCredits>.addToBatch(
        client: AuthenticatedClient,
        item: ResourceChargeCredits
    ) {
        add(item)
        if (size >= 100) sendBatch(client, dbConnection)
    }

    private var nextScan = 0L
    override suspend fun PluginContext.runMonitoringLoop() {
        if (!config.shouldRunServerCode()) return
        if (pluginConfig.accounting == null) return

        val pathConverter = PathConverter(this)
        val productCategories = productAllocation.map { it.category }.toSet()


        while (currentCoroutineContext().isActive) {
            loop(pathConverter, productCategories)
        }
    }

    // NOTE(Dan): Extracted because of an issue with GraalVM not supporting loops with coroutines directly inside them
    private suspend fun PluginContext.loop(pathConverter: PathConverter, productCategories: Set<String>) {
        try {
            val now = Time.now()
            if (now >= nextScan) {
                debugSystem.enterContext("Posix collection monitoring") {
                    var updates = 0
                    val batchBuilder = ArrayList<ResourceChargeCredits>()

                    val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

                    val lastScan: MutableMap<String, Long> = HashMap()
                    dbConnection.withSession { session ->
                        session.prepareStatement("""
                            select id, last_scan from posix_storage_scan
                        """).useAndInvoke(
                            readRow = {
                                val productId = it.getString(0)!!
                                val lastScanTime = it.getString(1)!!
                                lastScan[productId] = dateFormatter.parse(lastScanTime).time
                            }
                        )
                    }

                    productCategories.forEachGraal { category ->
                        var next: String? = null
                        var shouldBreak = false
                        whileGraal({ currentCoroutineContext().isActive && !shouldBreak }) {
                            val summary = Wallets.retrieveProviderSummary.call(
                                WalletsRetrieveProviderSummaryRequest(
                                    filterCategory = category,
                                    itemsPerPage = 250,
                                    next = next,
                                ),
                                rpcClient
                            ).orThrow()

                            summary.items.associateBy { it.id }.values.forEachGraal inner@{ item ->
                                val resourceOwner = ResourceOwnerWithId.load(item.owner, this@loop) ?: return@inner
                                val colls = locateAndRegisterCollections(resourceOwner)
                                    .filter { it.product.category == category }

                                if (colls.isNotEmpty()) {
                                    val bytesUsed = colls.sumOf { calculateUsage(it) }
                                    val unitsUsed = bytesUsed / 1_000_000_000L
                                    val coll = pathConverter.ucloudToCollection(
                                        pathConverter.internalToUCloud(InternalFile(colls.first().localPath))
                                    )

                                    val periods: Long = if (lastScan.keys.contains(coll.id)) {
                                        val minutesSinceLastScan = (Time.now() - lastScan[coll.id]!!) / 1000 / 60

                                        when (item.unitOfPrice) {
                                            ProductPriceUnit.UNITS_PER_MINUTE, ProductPriceUnit.CREDITS_PER_MINUTE ->
                                                minutesSinceLastScan

                                            ProductPriceUnit.UNITS_PER_HOUR, ProductPriceUnit.CREDITS_PER_HOUR ->
                                                minutesSinceLastScan / 60

                                            ProductPriceUnit.UNITS_PER_DAY, ProductPriceUnit.CREDITS_PER_DAY ->
                                                minutesSinceLastScan / 60 / 24

                                            else -> 1
                                        }
                                    } else {
                                        1
                                    }

                                    batchBuilder.addToBatch(
                                        rpcClient,
                                        ResourceChargeCredits(
                                            coll.id,
                                            "$now-${coll.id}",
                                            unitsUsed,
                                            periods
                                        )
                                    )

                                    updates++
                                }
                            }

                            batchBuilder.sendBatch(rpcClient, dbConnection)

                            next = summary.next
                            if (next == null) shouldBreak = true
                        }
                    }

                    debugSystem.logD(
                        "Charged $updates posix collections",
                        Unit.serializer(),
                        Unit,
                        if (updates == 0) MessageImportance.IMPLEMENTATION_DETAIL
                        else MessageImportance.THIS_IS_NORMAL
                    )
                    nextScan = now + (1000L * 60 * 60 * 4)
                }
            }

            delay(5000)
        } catch (ex: Throwable) {
            log.info("Caught exception while monitoring Posix collections: ${ex.stackTraceToString()}")
            nextScan = Time.now() + (1000L * 60 * 60 * 4)
        }
    }

    private suspend fun calculateUsage(coll: PathConverter.Collection): Long {
        return when (val cfg = pluginConfig.accounting) {
            null -> 0
            else -> {
                calculateUsage.invoke(cfg, CalculateUsageRequest(coll.localPath)).bytesUsed
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        private val retrieveCollections = extension(ResourceOwnerWithId.serializer(), ListSerializer(PosixCollectionFromExtension.serializer()))
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
