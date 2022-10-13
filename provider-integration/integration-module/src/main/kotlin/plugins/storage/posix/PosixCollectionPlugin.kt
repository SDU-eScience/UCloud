package dk.sdu.cloud.plugins.storage.posix

import dk.sdu.cloud.PageV2
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
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.plugins.storage.PathConverter
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.associateByGraal
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
import java.util.Date
import kotlin.math.floor

object PosixCollectionIpc : IpcContainer("posixfscoll") {
    val retrieveCollections = retrieveHandler(ResourceOwner.serializer(), PageV2.serializer(FindByPath.serializer()))
}

class PosixCollectionPlugin : FileCollectionPlugin {
    override val pluginTitle: String = "Posix"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()
    private lateinit var pluginConfig: ConfigSchema.Plugins.FileCollections.Posix
    private var initializedProjects = HashMap<ResourceOwnerWithId, List<PathConverter.Collection>>()
    private lateinit var partnerPlugin: PosixFilesPlugin
    lateinit var pathConverter: PathConverter
        private set
    private val mutex = Mutex()
    private lateinit var ctx: PluginContext

    private data class CollectionChargeCredits(
        val lastCharged: Date,
        val priceUnit: ProductPriceUnit,
        val resourceChargeCredits: ResourceChargeCredits
    )

    override fun configure(config: ConfigSchema.Plugins.FileCollections) {
        this.pluginConfig = config as ConfigSchema.Plugins.FileCollections.Posix
    }

    override suspend fun PluginContext.initialize() {
        ctx = this
        partnerPlugin = (config.plugins.files[pluginName] as? PosixFilesPlugin) ?:
            error("Posix file-collection plugins requires a matching partner plugin of type Posix with name '$pluginName'")
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

    override suspend fun PluginContext.onAllocationCompleteInServerMode(notification: AllocationNotification) {
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

            @Suppress("DEPRECATION")
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

    override suspend fun RequestContext.retrieveProducts(
        knownProducts: List<ProductReference>
    ): BulkResponse<FSSupport> {
        return with(partnerPlugin) {
            retrieveProducts(knownProducts)
        }
    }

    private suspend fun ArrayList<CollectionChargeCredits>.sendBatch(client: AuthenticatedClient) {
        val filteredBatch = this.filter { it.resourceChargeCredits.periods > 0 }
        if (filteredBatch.isEmpty()) return

        FileCollectionsControl.chargeCredits.call(
            BulkRequest(filteredBatch.map { it.resourceChargeCredits }),
            client
        ).orThrow()

        // NOTE(Brian): Add/update the date and time of the end of the calculated period for each resource.
        // Charging is only for whole periods, but scans might happen at irregular intervals. By saving the time of the
        // end of the last charged period, the following scans will eventually make up for fraction-periods not included
        // in the first charge.
        dbConnection.withSession { session ->
            // NOTE(Brian): Can possibly be improved significantly with Postgres
            this.forEach {
                val periodFraction = calculatePeriods(it.lastCharged, it.priceUnit) - it.resourceChargeCredits.periods

                val periodFractionSeconds: Long = when (it.priceUnit) {
                    ProductPriceUnit.UNITS_PER_MINUTE, ProductPriceUnit.CREDITS_PER_MINUTE ->
                        periodFraction * 60

                    ProductPriceUnit.UNITS_PER_HOUR, ProductPriceUnit.CREDITS_PER_HOUR ->
                        periodFraction * 60 * 60

                    ProductPriceUnit.UNITS_PER_DAY, ProductPriceUnit.CREDITS_PER_DAY ->
                        periodFraction * 60 * 60 * 24

                    else -> 0
                }.toLong()

                val lastChargedPeriodEnd = (Time.now() - periodFractionSeconds * 1000) / 1000.0

                session.prepareStatement(
                """
                    insert into posix_storage_scan (id, last_charged_period_end) 
                    values (:id, to_timestamp(:last_charged_period_end))
                    on conflict (id) do update set last_charged_period_end = :last_charged_period_end
                """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("id", it.resourceChargeCredits.id)
                        bindDouble("last_charged_period_end", lastChargedPeriodEnd)
                    }
                )
            }
        }

        clear()
    }

    private suspend fun ArrayList<CollectionChargeCredits>.addToBatch(
        client: AuthenticatedClient,
        item: CollectionChargeCredits,
    ) {
        add(item)
        if (size >= 100) sendBatch(client)
    }

    private var nextScan = 0L
    override suspend fun PluginContext.runMonitoringLoopInServerMode() {
        if (pluginConfig.accounting == null) return

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
                    val batchBuilder = ArrayList<CollectionChargeCredits>()
                    val lastCharges = lastChargeTimes()

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

                            summary.items.associateByGraal { it.id }.values.forEachGraal inner@{ item ->
                                val resourceOwner = ResourceOwnerWithId.load(item.owner, this@loop) ?: return@inner
                                val colls = locateAndRegisterCollections(resourceOwner)
                                    .filter { it.product.category == category }

                                if (colls.isNotEmpty()) {
                                    val bytesUsed = colls.sumOf { calculateUsage(it) }
                                    val unitsUsed = bytesUsed / 1_000_000_000L
                                    val coll = pathConverter.ucloudToCollection(
                                        pathConverter.internalToUCloud(InternalFile(colls.first().localPath))
                                    )

                                    if (lastCharges.keys.contains(coll.id)) {
                                        val lastCharged = lastCharges[coll.id]!!
                                        val periods = calculatePeriods(lastCharged, item.unitOfPrice)

                                        batchBuilder.addToBatch(
                                            rpcClient,
                                            CollectionChargeCredits(
                                                lastCharged,
                                                item.unitOfPrice,
                                                ResourceChargeCredits(
                                                    coll.id,
                                                    "$now-${coll.id}",
                                                    unitsUsed,
                                                    floor(periods).toLong()
                                                )
                                            )
                                        )

                                    } else {
                                        dbConnection.withSession { session ->
                                            session.prepareStatement(
                                                """
                                                insert into posix_storage_scan
                                                (id, last_charged_period_end) values (:id, now())
                                                on conflict (id) do update set last_charged_period_end = now()
                                            """
                                            ).useAndInvokeAndDiscard(
                                                prepare = {
                                                    bindString("id", coll.id)
                                                }
                                            )
                                            Date(Time.now())
                                        }
                                    }

                                    updates++
                                }
                            }

                            batchBuilder.sendBatch(rpcClient)

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
                calculateUsage.invoke(ctx, cfg, CalculateUsageRequest(coll.localPath)).bytesUsed
            }
        }
    }

    private fun calculatePeriods(lastCharged: Date, priceUnit: ProductPriceUnit): Double {
        val minutesSinceLastScan = (Time.now() - lastCharged.time) / 1000.0 / 60.0

        return when (priceUnit) {
            ProductPriceUnit.UNITS_PER_MINUTE, ProductPriceUnit.CREDITS_PER_MINUTE ->
                minutesSinceLastScan

            ProductPriceUnit.UNITS_PER_HOUR, ProductPriceUnit.CREDITS_PER_HOUR ->
                minutesSinceLastScan / 60

            ProductPriceUnit.UNITS_PER_DAY, ProductPriceUnit.CREDITS_PER_DAY ->
                minutesSinceLastScan / 60 / 24

            else -> 1.0
        }
    }

    private suspend fun lastChargeTimes(): Map<String, Date> {
        return dbConnection.withSession { session ->
            val lastCharges: MutableMap<String, Date> = HashMap()
            session.prepareStatement(
                """
                    select id, (extract(epoch from last_charged_period_end) * 1000)::int8 as last_charge_period_end
                    from posix_storage_scan
                """
            ).useAndInvoke(
                readRow = {
                    val productId = it.getString(0)!!
                    val lastScanTime = it.getLong(1)!!
                    lastCharges[productId] = Date(lastScanTime)
                }
            )
            lastCharges
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
