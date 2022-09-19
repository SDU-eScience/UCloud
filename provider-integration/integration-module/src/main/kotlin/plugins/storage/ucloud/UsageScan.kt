package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.plugins.RelativeInternalFile
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.bindStringNullable
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import java.time.*
import java.time.format.*
import java.util.concurrent.atomic.AtomicBoolean

// NOTE(Dan): This code is currently not invoked due to potential bugs in the code.

class UsageScan(
    private val pluginName: String,
    private val pathConverter: PathConverter,
    private val fs: NativeFS,
    private val fastDirectoryStats: CephFsFastDirectoryStats,
    private val serviceClient: AuthenticatedClient,
    private val db: DBContext,
) {
    private val isRunning = AtomicBoolean(false)
    private val globalErrorCounter = AtomicInteger(0)
    private val globalRequestCounter = AtomicInteger(0)

    private val dataPoints = HashMap<UsageDataPoint.Key, UsageDataPoint>()

    data class UsageDataPoint(
        val key: Key,
        val initialResourceId: String,
        val internalCollections: ArrayList<FileCollection>,
        var usageInBytes: Long,
    ) {
        data class Key(
            val owner: WalletOwner,
            // NOTE(Dan): The product ID comes from the first collection we encounter. It is critical that we perform
            // the charge against the category and not one for each product. This is due to how `DIFFERENTIAL_QUOTA`
            // works.
            val category: ProductCategoryId
        )
    }

    suspend fun startScanIfNeeded() {
        var lastRun = 0L
        db.withSession { session ->
            session.prepareStatement(
                """
                    select last_run
                    from ucloud_storage_timestamps
                    where name = :name
                """
            ).useAndInvoke(
                prepare = {
                    bindString("name", pluginName)
                },
                readRow = { row ->
                    lastRun = row.getLong(0)!!
                }
            )
        }

        val oneDay = 1000L * 60 * 60 * 24
        val now = Time.now()
        if (now - lastRun < oneDay) return
        if (!isRunning.compareAndSet(false, true)) return

        try {
            dataPoints.clear()
            globalErrorCounter.set(0)
            globalRequestCounter.set(0)

            val scanId = Time.now().toString()

            val chunkSize = 50
            db.withSession { session ->
                run {
                    val collectionRoot = pathConverter.relativeToInternal(RelativeInternalFile("/collections"))
                    val collections = fs.listFiles(collectionRoot).mapNotNull { it.toLongOrNull() }
                    collections.chunked(chunkSize).forEach { chunk ->
                        val resolvedCollections =
                            retrieveCollections(providerGenerated = false, chunk.map { it.toString() })
                                ?: return@forEach

                        val paths = chunk.map {
                            pathConverter.relativeToInternal(RelativeInternalFile("/collections/${it}"))
                        }

                        // NOTE(Dan): We assume that if the recursive size comes back as null then this means that the
                        // collection has been deleted and thus shouldn't count.
                        val sizes = paths.map { thisCollection ->
                            fastDirectoryStats.getRecursiveSize(thisCollection) ?: 0L
                        }

                        processChunk(chunk, sizes, resolvedCollections, chunk.map { it.toString() })
                    }
                }

                run {
                    val collectionRoot = pathConverter.relativeToInternal(RelativeInternalFile("/home"))
                    val collections = fs.listFiles(collectionRoot)
                    collections.chunked(chunkSize).forEach { chunk ->
                        val resolvedCollections = retrieveCollections(
                            providerGenerated = true,
                            chunk.map { PathConverter.COLLECTION_HOME_PREFIX + it }
                        ) ?: return@forEach

                        val paths = chunk.map { filename ->
                            pathConverter.relativeToInternal(RelativeInternalFile("/home/${filename}"))
                        }

                        val sizes = paths.map { thisCollection ->
                            fastDirectoryStats.getRecursiveSize(thisCollection) ?: 0L
                        }

                        val mappedChunk = chunk.map { filename ->
                            resolvedCollections
                                .find { it.providerGeneratedId == PathConverter.COLLECTION_HOME_PREFIX + filename }
                                ?.id
                                ?.toLongOrNull()
                        }

                        processChunk(mappedChunk, sizes, resolvedCollections, chunk)
                    }
                }

                run {
                    // TODO(Dan): If files are only stored in member files then we won't find them with this code
                    val collectionRoot = pathConverter.relativeToInternal(RelativeInternalFile("/projects"))
                    val collections = fs.listFiles(collectionRoot)
                    collections.chunked(chunkSize).forEach { chunk ->
                        val paths = chunk.map { filename ->
                            pathConverter.relativeToInternal(RelativeInternalFile("/projects/${filename}"))
                        }

                        val reposForAccounting = paths.map { projectRoot ->
                            runCatching { fs.listFiles(projectRoot) }.getOrNull()?.firstOrNull() ?: "0"
                        }

                        val chunkAndRepos = chunk.zip(reposForAccounting)
                        val resolvedCollections = retrieveCollections(
                            providerGenerated = true,
                            chunkAndRepos.map { (projectName, firstRepo) ->
                                PathConverter.COLLECTION_PROJECT_PREFIX + projectName + "/" + firstRepo
                            }
                        ) ?: return@forEach

                        val sizes = paths.map { thisCollection ->
                            fastDirectoryStats.getRecursiveSize(thisCollection) ?: 0L
                        }

                        val mappedChunk = chunkAndRepos.map { (projectName, firstRepo) ->
                            resolvedCollections
                                .find {
                                    it.providerGeneratedId == (PathConverter.COLLECTION_PROJECT_PREFIX + projectName +
                                            "/" + firstRepo)
                                }
                                ?.id
                                ?.toLongOrNull()
                        }

                        processChunk(mappedChunk, sizes, resolvedCollections, chunk)
                    }
                }

                for (chunk in dataPoints.values.chunked(100)) {
                    val allRequests = chunk.mapNotNull { dataPoint ->
                        val chargeId = when (val owner = dataPoint.key.owner) {
                            is WalletOwner.Project -> owner.projectId
                            is WalletOwner.User -> owner.username
                        }

                        // NOTE(Dan): we need to floor this, because otherwise we won't actually give people the full quota
                        // they deserve (this becomes very apparent when granting small quotas).
                        val units = kotlin.math.floor(dataPoint.usageInBytes / 1.GB.toDouble()).toLong()
                        if (units < 0) return@mapNotNull null

                        ResourceChargeCredits(
                            dataPoint.initialResourceId,
                            "$chargeId-$scanId",
                            units,
                            description = "Daily storage charge"
                        )
                    }

                    charge(scanId, session, chunk, allRequests)
                }

                session.prepareStatement(
                    """
                        delete from ucloud_storage_quota_locked
                        where scan_id != :scan_id
                    """,
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("scan_id", scanId)
                    }
                )

                session.prepareStatement(
                    """
                        insert into ucloud_storage_timestamps (name, last_run)
                        values (:plugin_name, :now) on conflict (name) do update set last_run = excluded.last_run
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("plugin_name", pluginName)
                        bindLong("now", Time.now())
                    }
                )
            }
        } finally {
            isRunning.set(false)
        }
    }

    private fun processChunk(
        chunk: List<Long?>,
        sizes: List<Long>,
        resolvedCollections: List<FileCollection>,
        debug: List<String>,
    ) {
        for (idx in chunk.indices) {
            val size = sizes[idx]
            val collectionId = chunk[idx] ?: continue
            val resolvedCollection = resolvedCollections.find { it.id == collectionId.toString() } ?: continue
            val (username, project) = resolvedCollection.owner
            val key = UsageDataPoint.Key(
                if (project != null) {
                    WalletOwner.Project(project)
                } else {
                    WalletOwner.User(username)
                },
                ProductCategoryId(
                    resolvedCollection.specification.product.category,
                    resolvedCollection.specification.product.provider,
                )
            )

            val entry = dataPoints[key] ?: UsageDataPoint(key, resolvedCollection.id, ArrayList(), 0L)
            entry.usageInBytes += size
            entry.internalCollections.add(resolvedCollection)
            dataPoints[key] = entry
        }
    }

    private suspend fun retrieveCollections(
        providerGenerated: Boolean,
        collections: List<String>
    ): List<FileCollection>? {
        val includeFlags = if (providerGenerated) {
            FileCollectionIncludeFlags(filterProviderIds = collections.joinToString(","))
        } else {
            FileCollectionIncludeFlags(filterIds = collections.joinToString(","))
        }

        try {
            return retrySection {
                FileCollectionsControl.browse.call(
                    ResourceBrowseRequest(includeFlags, itemsPerPage = 250),
                    serviceClient
                ).orThrow().items
            }
        } catch (ex: Throwable) {
            log.warn("Failed to retrieve information about collections: $collections\n${ex.stackTraceToString()}")
            globalRequestCounter.getAndAdd(collections.size)
            globalErrorCounter.getAndAdd(collections.size)
            checkIfWeShouldTerminate()
            return null
        }
    }

    // NOTE(Dan): We use the following procedure for charging. The procedure is intended to be more roboust against
    // various error scenarios we have encountered in production.
    //
    //  1. Attempt to bulk charge the entire chunk (retry up to 5 times with a fixed delay)
    //  2. If this fails, attempt to charge the individual requests. All requests are retried using the same algorithm.
    //  3. If a request still fails, we skip the entry and log a warning message that we failed.
    //     a. We keep a global failure counter, we use this counter to determine if the entire script should fail.
    //     b. If more than 10% requests have failed AND at least 100 requests have been attempted, then the entire
    //        script will fail.
    //     c. This should trigger an automatic warning in the system.
    //
    // NOTE(Dan): Step 2 is intended to handle situations where a specific folder is triggering an edge-case in the
    // accounting system. This mitigates the risk that a single folder can cause accounting of all folders to fail
    // (See SDU-eScience/UCloud#2712)
    private suspend fun charge(
        scanId: String,
        session: DBContext.Connection,
        chunk: List<UsageDataPoint>,
        requests: List<ResourceChargeCredits>
    ) {
        if (requests.isEmpty()) return

        try {
            retrySection { sendCharge(scanId, session, chunk, requests) }
            return
        } catch (ex: Throwable) {
            log.warn("Unable to charge requests (bulk): $requests")
        }

        for (request in requests) {
            try {
                retrySection { sendCharge(scanId, session, chunk, listOf(request)) }
            } catch (ex: Throwable) {
                log.warn("Unable to charge request (single): $request")
                globalRequestCounter.getAndAdd(1)
                globalErrorCounter.getAndAdd(1)
                checkIfWeShouldTerminate()
            }
        }
    }

    private fun checkIfWeShouldTerminate() {
        val errorCounter = globalErrorCounter.get()
        val requestCounter = globalRequestCounter.get()
        if (requestCounter > 100 && errorCounter / requestCounter.toDouble() >= 0.10) {
            throw IllegalStateException("Error threshold has been exceeded")
        }
    }

    private suspend fun sendCharge(
        scanId: String,
        session: DBContext.Connection,
        chunk: List<UsageDataPoint>,
        request: List<ResourceChargeCredits>
    ) {
        val result = FileCollectionsControl.chargeCredits.call(BulkRequest(request), serviceClient).orThrow()
        globalRequestCounter.getAndAdd(request.size)

        val lockedIdxs = result.insufficientFunds.mapNotNull { (resourceId) ->
            val requestIdx = request.indexOfFirst { it.id == resourceId }
            if (requestIdx == -1)  {
                log.warn("Could not lock resource: ${resourceId}. Something is wrong!")
                null
            } else {
                requestIdx
            }
        }

        for (i in lockedIdxs) {
            session.prepareStatement(
                """
                    insert into ucloud_storage_quota_locked (scan_id, category, username, project_id) 
                    values (:scan_id, :category, :username, :project_id)
                """,
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("scan_id", scanId)
                    bindString("category", chunk[i].key.category.name)
                    bindStringNullable("username", (chunk[i].key.owner as? WalletOwner.User)?.username)
                    bindStringNullable("project_id", (chunk[i].key.owner as? WalletOwner.Project)?.projectId)
                }
            )
        }
    }

    private inline fun <T> retrySection(attempts: Int = 5, delay: Long = 500, block: () -> T): T {
        for (i in 1..attempts) {
            @Suppress("TooGenericExceptionCaught")
            try {
                return block()
            } catch (ex: Throwable) {
                println(ex.stackTraceToString())
                if (i == attempts) throw ex
                Thread.sleep(delay)
            }
        }
        throw IllegalStateException("retrySection impossible situation reached. This should not happen.")
    }

    companion object : Loggable {
        override val log = logger()
    }
}
