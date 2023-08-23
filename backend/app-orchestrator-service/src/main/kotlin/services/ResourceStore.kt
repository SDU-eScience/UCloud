package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProviderRegisteredResource
import dk.sdu.cloud.accounting.api.providers.SortDirection
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.ResourceDocument
import dk.sdu.cloud.accounting.util.ResourceDocumentUpdate
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.provider.api.ResourceIncludeFlags
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.toReadableStacktrace
import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.IntVector
import jdk.incubator.vector.VectorOperators
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import org.cliffc.high_scale_lib.NonBlockingHashMapLong
import java.lang.RuntimeException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class ResourceStore<T>(
    val type: String,
    private val queries: ResourceStoreDatabaseQueries<T>,
    private val productCache: IProductCache,
    private val idCardService: IIdCardService,
    private val callbacks: Callbacks<T>,
) {
    constructor(
        type: String,
        db: AsyncDBSessionFactory,
        productCache: ProductCache,
        idCardService: IIdCardService,
        backgroundScope: BackgroundScope,
        callbacks: Callbacks<T>
    ) : this(
        type,
        ResourceStoreDatabaseQueriesImpl(db, backgroundScope),
        productCache,
        idCardService,
        callbacks
    ) {
        if (queries is ResourceStoreDatabaseQueriesImpl<T>) queries.init(this)
    }

    data class BrowseResult(val count: Int, val next: String?)

    interface Callbacks<T> {
        suspend fun loadState(transaction: Any, count: Int, resources: LongArray): Array<T>
        suspend fun saveState(transaction: Any, store: ResourceStoreBucket<T>, indices: IntArray, length: Int)
    }

    // Stores and shortcuts tables
    // =================================================================================================================
    // The following calls deal with the buckets and the tables which contain references to the stores. All the data
    // is within the individual buckets stored in the tables. The main entrypoint to the data is a non-blocking
    // hash map, which maps either a UID or a PID to a bucket. Inside a bucket, you will are guaranteed to find _all_
    // of the resources owned by a single workspace.
    //
    // The tables are stored below:

    private val uidShortcut = NonBlockingHashMapLong<ResourceStoreBucket<T>>()
    private val pidShortcut = NonBlockingHashMapLong<ResourceStoreBucket<T>>()

    // Buckets are always owned by a workspace. We identify a workspace by a tuple containing the user ID (`uid`) and
    // the project ID (`pid`). We only use the numeric IDs for this, since it improves performance inside the buckets by
    // quite a bit. The translation from textual IDs to numeric IDs translation is done by the IdCardService. If the
    // `pid` is non-zero then the `uid` will always be ignored. This allows the callers to simply pass in the
    // information an ActorAndProject easily.

    // To use the bucket of a workspace, you will need to call `findOrLoadBucket`:
    private suspend fun findOrLoadBucket(uid: Int, pid: Int): ResourceStoreBucket<T> {
        require(uid == 0 || pid == 0)
        return findBucket(uid, pid) ?: loadBucket(uid, pid)
    }

    // Since the buckets are linked-list like structure, you should make sure to iterate through all the buckets. You need
    // to do this using the `useBuckets` function. Note that you can terminate the search early once you have performed
    // the request you need to do:
    private inline suspend fun useBuckets(
        root: ResourceStoreBucket<T>,
        reverseOrder: Boolean = false,
        consumer: (ResourceStoreBucket<T>) -> ShouldContinue
    ) {
        var currentRoot = root
        if (!reverseOrder) {
            while (true) {
                var current: ResourceStoreBucket<T>? = currentRoot
                try {
                    while (current != null) {
                        if (consumer(current) == ShouldContinue.NO) break
                        current = current.next
                    }
                    return
                } catch (ex: EvictionException) {
                    currentRoot = findOrLoadBucket(root.uid, root.pid)
                    delay(5)
                }
            }
        } else {
            while (true) {
                var current: ResourceStoreBucket<T>? = currentRoot.findTail()
                try {
                    while (current != null) {
                        if (consumer(current) == ShouldContinue.NO) break
                        current = current.previous
                    }
                    return
                } catch (ex: EvictionException) {
                    currentRoot = findOrLoadBucket(root.uid, root.pid)
                    delay(5)
                }
            }
        }
    }

    private enum class ShouldContinue {
        YES,
        NO;

        companion object {
            fun ifThisIsTrue(value: Boolean) = if (value) YES else NO
        }
    }

    // In order to implement `findOrLoadBucket` we must of course implement the two subcomponents. Finding a loaded
    // bucket is relatively straight-forward, we simply look in the relevant hash map.
    private suspend fun findBucket(uid: Int, pid: Int): ResourceStoreBucket<T>? {
        CreateCounters.findBucket.measureValue {
            require(uid == 0 || pid == 0)

            val map = if (uid != 0) uidShortcut else pidShortcut
            val ref = (if (uid != 0) uid else pid).toLong()

            val bucket = map[ref] ?: return null

            if (bucket.evicted) {
                bucket.awaitEvictionComplete()
                map.remove(ref)
            }

            return bucket
        }
    }

    // Loading the bucket is also fairly straightforward. The function will quickly reserve a spot in the map and then
    // trigger a load() inside the bucket. Note that the bucket itself is capable of handling the situation where it
    // receives requests even though it hasn't finished loading.
    private suspend fun loadBucket(uid: Int, pid: Int): ResourceStoreBucket<T> {
        CreateCounters.loadBucket.measureValue {
            require(uid == 0 || pid == 0)

            val map = if (uid != 0) uidShortcut else pidShortcut
            val ref = (if (uid != 0) uid else pid).toLong()

            val result = ResourceStoreBucket(type, uid, pid, queries, callbacks)
            val existing = map.putIfAbsent(ref, result)
            if (existing == null) result.load()

            return existing ?: result
        }
    }

    // The buckets and shortcut tables are consumed by the public API.

    // Public API
    // =================================================================================================================
    // This is where we actually make stuff happen! These functions are consumed by the services of the different
    // resource types. Almost all the functions in this section will follow the same pattern of:
    //
    // 1. Locate (and potentially initialize) the appropriate stores which contain the resources. This will often use
    //    one of the indices we have available to us (see the following sections).
    // 2. Iterate through all the relevant stores (via `useBuckets`) and perform the relevant operation.
    // 3. If relevant, update one or more indices.
    // 4. Aggregate and filter the results. Finally return it to the caller.
    //
    // In other words, you won't find a lot of business logic in these calls, since this is mostly implemented in the
    // individual stores.

    private suspend fun syncTable(
        transaction: Any,
        table: NonBlockingHashMapLong<ResourceStoreBucket<T>>
    ) {
        table.values.forEach { rootBucket ->
            if (rootBucket != null) {
                useBuckets(rootBucket) {
                    it.synchronizeToDatabase(transaction)
                    ShouldContinue.YES
                }
            }
        }
    }

    suspend fun synchronizeNow() {
        queries.withSession { session ->
            syncTable(session, uidShortcut)
            syncTable(session, pidShortcut)
        }
    }

    fun initializeBackgroundTasks(
        scope: CoroutineScope,
    ): Job {
        Runtime.getRuntime().addShutdownHook(Thread {
            runBlocking { synchronizeNow() }
        })

        return scope.launch {
            while (coroutineContext.isActive) {
                try {
                    val start = Time.now()
                    synchronizeNow()
                    val end = Time.now()

                    delay(max(1000, 10_000 - (end - start)))
                } catch (ex: Throwable) {
                    log.warn(ex.toReadableStacktrace().toString())
                }
            }
        }
    }

    object CreateCounters {
        private const val base = "ResourceStore.create"

        val cacheLookup = threadLocalCounter("$base.cacheLookup")
        val findBucket = threadLocalCounter("$base.findBucket")
        val loadBucket = threadLocalCounter("$base.loadBucket")
        val findTail = threadLocalCounter("$base.findTail")
        val bucketCreate = threadLocalCounter("$base.bucketCreate")
        val bucketExpand = threadLocalCounter("$base.bucketExpand")
        val idIndexFind = threadLocalCounter("$base.idIndexFind")
        val idIndexLoad = threadLocalCounter("$base.idIndexLoad")
        val idIndexRegister = threadLocalCounter("$base.idIndexRegister")
        val providerIdLookup = threadLocalCounter("$base.providerIdLookup")
        val providerIndexLookup = threadLocalCounter("$base.providerIndexLookup")
        val providerIndexRegister = threadLocalCounter("$base.providerIndexRegister")
    }

    suspend fun create(
        idCard: IdCard,
        product: ProductReference,
        data: T,
        output: ResourceDocument<T>?
    ): Long {
        if (idCard !is IdCard.User) {
            throw RPCException("Only end-users can use this endpoint", HttpStatusCode.Forbidden)
        }

        val productId = CreateCounters.cacheLookup.measureValue {
            productCache.referenceToProductId(product)
                ?: throw RPCException("Invalid product supplied", HttpStatusCode.BadRequest)
        }

        val pid = if (idCard.activeProject > 0) idCard.activeProject else 0

        return createDocument(idCard.uid, pid, productId, data, null, output = output)
    }

    suspend fun createViaProvider(
        idCard: IdCard,
        product: ProductReference,
        data: T,
        output: ResourceDocument<T>? = null,
        addOwnerToAcl: Boolean = false,
        proxyBlock: suspend (doc: ResourceDocument<T>) -> String?,
    ): Long {
        val doc = output ?: ResourceDocument<T>()
        val allocatedId = create(idCard, product, data, output = doc)

        val providerId = try {
            proxyBlock(doc)
        } catch (ex: Throwable) {
            log.warn(ex.toReadableStacktrace().toString())
            // TODO(Dan): This is not guaranteed to run ever. We will get stuck if never "confirmed" by the provider.
            delete(idCard, longArrayOf(allocatedId))
            throw ex
        }

        if (providerId != null) {
            updateProviderId(idCard, allocatedId, providerId)
            doc.providerId = providerId
        }

        if (addOwnerToAcl && idCard is IdCard.User) {
            updateAcl(
                IdCard.System,
                allocatedId,
                emptyList(),
                listOf(
                    NumericAclEntry(idCard.uid, permission = Permission.READ),
                    NumericAclEntry(idCard.uid, permission = Permission.EDIT),
                )
            )
        }

        return allocatedId
    }

    suspend fun register(
        idCard: IdCard,
        product: ProductReference,
        uid: Int,
        pid: Int,
        data: T,
        providerId: String? = null,
        projectAllWrite: Boolean = false,
        projectAllRead: Boolean = false,
        output: ResourceDocument<T>? = null,
    ): Long {
        if (providerId?.contains(",") == true) {
            throw RPCException("Provider generated ID cannot contain ','", HttpStatusCode.BadRequest)
        }

        if (idCard !is IdCard.Provider) {
            throw RPCException("Only providers can use this endpoint", HttpStatusCode.Forbidden)
        }

        val productId = productCache.referenceToProductId(product)
            ?: throw RPCException("Invalid product supplied", HttpStatusCode.Forbidden)

        if (productId !in idCard.providerOf) {
            throw RPCException("Invalid product supplied", HttpStatusCode.Forbidden)
        }

        return createDocument(
            uid,
            pid,
            productId,
            data,
            providerId,
            output = output,
            projectAllRead = projectAllRead,
            projectAllWrite = projectAllWrite,
        )
    }

    suspend fun register(
        idCard: IdCard,
        request: ProviderRegisteredResource<*>,
        data: T,
        output: ResourceDocument<T>? = null,
    ): Long {
        return register(
            idCard,
            request.spec.product,
            idCardService.lookupUidFromUsernameOrFail(request.createdBy),
            idCardService.lookupPidFromProjectIdOrFail(request.project),
            data,
            request.providerGeneratedId,
            output = output,
            projectAllRead = request.projectAllRead,
            projectAllWrite = request.projectAllWrite,
        )
    }

    private suspend fun createDocument(
        uid: Int,
        pid: Int,
        productId: Int,
        data: T,
        providerGeneratedId: String? = null,
        projectAllRead: Boolean = false,
        projectAllWrite: Boolean = false,
        output: ResourceDocument<T>? = null,
    ): Long {
        while (true) {
            val root = findOrLoadBucket(if (pid == 0) uid else 0, pid)
            var tail = CreateCounters.findTail.measureValue { root.findTail() }
            try {
                val id = CreateCounters.bucketCreate.measureValue {
                    tail.create(uid, productId, data, providerGeneratedId, output)
                }
                if (id < 0L) {
                    tail = CreateCounters.bucketExpand.measureValue { tail.expand() }
                } else {
                    val index = findIdIndexOrLoad(id) ?: error("Index was never initialized. findIdIndex is buggy? $id")
                    CreateCounters.idIndexRegister.measureValue {
                        index.register(id, if (pid != 0) pid else uid, pid == 0)
                    }

                    val providerId = CreateCounters.providerIdLookup.measureValue {
                        productCache.productIdToReference(productId)?.provider ?: error("Unknown product?")
                    }

                    val providerIndex = CreateCounters.providerIndexLookup.measureValue {
                        findOrLoadProviderIndex(providerId)
                    }

                    CreateCounters.providerIndexRegister.measureValue {
                        providerIndex.registerUsage(uid, pid)
                    }

                    if (pid != 0 && (projectAllRead || projectAllWrite)) {
                        val allUserGroupId = idCardService.fetchAllUserGroup(pid)
                        require(
                            tail.updateAcl(
                                IdCard.System,
                                id,
                                emptyList(),
                                buildList {
                                    if (projectAllRead) {
                                        add(NumericAclEntry(gid = allUserGroupId, permission = Permission.READ))
                                    }

                                    if (projectAllWrite) {
                                        add(NumericAclEntry(gid = allUserGroupId, permission = Permission.EDIT))
                                    }
                                }
                            ),

                            lazyMessage = {
                                "updateAcl should not fail when granting permissions to the 'All Users' group"
                            }
                        )
                    }

                    return id
                }
            } catch (ex: EvictionException) {
                delay(5)
            }
        }
    }

    @Suppress("RemoveExplicitTypeArguments")
    private suspend fun buildFilterFunction(
        flags: ResourceIncludeFlags,
        additionalFilters: FilterFunction<T>?,
    ): FilterFunction<T> {
        val filterCreatedByUid = if (flags.filterCreatedBy != null) {
            idCardService.lookupUidFromUsername(flags.filterCreatedBy!!)
        } else {
            null
        }

        val filterCreatedBy = flags.filterCreatedBy
        val filterCreatedAfter = flags.filterCreatedAfter
        val filterCreatedBefore = flags.filterCreatedBefore
        val filterProviderIds = flags.filterProviderIds?.split(",")
        val filterIds = flags.filterIds?.split(",")?.mapNotNull { it.toLongOrNull() }

        val filterProductName = flags.filterProductId
        val filterProductCategory = flags.filterProductCategory
        val filterProvider = flags.filterProvider
        val filterProductIds =
            if (filterProductName != null || filterProductCategory != null || filterProvider != null) {
                buildSet<Int> {
                    if (filterProductName != null) {
                        addAll(productCache.productNameToProductIds(filterProductName) ?: emptyList())
                    }
                    if (filterProductCategory != null) {
                        addAll(productCache.productCategoryToProductIds(filterProductCategory) ?: emptyList())
                    }
                    if (filterProvider != null) {
                        addAll(productCache.productProviderToProductIds(filterProvider) ?: emptyList())
                    }
                }
            } else {
                null
            }

        val hideProductName = flags.hideProductId
        val hideProductCategory = flags.hideProductCategory
        val hideProvider = flags.hideProvider
        val hideProductIds = if (hideProductName != null || hideProductCategory != null || hideProvider != null) {
            buildSet<Int> {
                if (hideProductName != null) {
                    addAll(productCache.productNameToProductIds(hideProductName) ?: emptyList())
                }
                if (hideProductCategory != null) {
                    addAll(productCache.productCategoryToProductIds(hideProductCategory) ?: emptyList())
                }
                if (hideProvider != null) {
                    addAll(productCache.productProviderToProductIds(hideProvider) ?: emptyList())
                }
            }
        } else {
            null
        }

        return object : FilterFunction<T> {
            override fun filter(doc: ResourceDocument<T>): Boolean {
                var success = true

                @Suppress("KotlinConstantConditions")
                if (success && filterCreatedAfter != null) {
                    success = doc.createdAt >= filterCreatedAfter
                }

                if (success && filterCreatedBefore != null) {
                    success = doc.createdAt <= filterCreatedBefore
                }

                if (success && filterCreatedBy != null) {
                    success = filterCreatedByUid != null && filterCreatedByUid == doc.createdBy
                }

                if (success && filterProductIds != null) {
                    success = doc.product in filterProductIds
                }

                if (success && filterProviderIds != null) {
                    success = doc.providerId in filterProviderIds
                }

                if (success && filterIds != null) {
                    success = doc.id in filterIds
                }

                if (success && hideProductIds != null) {
                    success = doc.product !in hideProductIds
                }

                if (success && additionalFilters != null) {
                    success = additionalFilters.filter(doc)
                }

                return success
            }
        }
    }

    suspend fun browse(
        idCard: IdCard,
        outputBuffer: Array<ResourceDocument<T>>,
        next: String?,
        flags: ResourceIncludeFlags,
        outputBufferLimit: Int = outputBuffer.size,
        additionalFilters: FilterFunction<T>? = null,

        sortedBy: String? = null,
        sortDirection: SortDirection? = null,
    ): BrowseResult {
        @Suppress("NAME_SHADOWING")
        val sortDirection = sortDirection ?: if (sortedBy == null) SortDirection.descending else SortDirection.ascending

        if (sortedBy == "createdBy") {
            return browseWithSort(
                idCard,
                outputBuffer,
                next,
                flags,
                keyExtractor = object : DocKeyExtractor<T, Int> {
                    override fun extract(doc: ResourceDocument<T>): Int = doc.createdBy
                },
                comparator = Comparator.naturalOrder<Int>(),
                sortDirection = sortDirection,
                outputBufferLimit = outputBufferLimit,
                additionalFilters = additionalFilters
            )
        }

        // If we are not sorting by `createdBy` and it is not a custom sort, then we just assume that we are sorting by
        // "createdAt" (i.e. ID).

        val filter = buildFilterFunction(flags, additionalFilters)

        when (idCard) {
            IdCard.System -> error("not allowed")

            is IdCard.Provider -> {
                val storeArray = IntList()
                val storeArrayMaxResults = 128
                val providerIndex = findOrLoadProviderIndex(idCard.name)

                val nextSplit = next?.split("-")?.takeIf { it.size == 3 }

                var initialId = if (nextSplit != null) {
                    nextSplit[2].toLongOrNull() ?: -1L
                } else {
                    -1L
                }

                var minimumUid = 0
                var minimumPid = 0

                if (nextSplit != null && nextSplit[0] == "p") {
                    minimumUid = Int.MAX_VALUE
                    minimumPid = nextSplit[1].toIntOrNull() ?: Int.MAX_VALUE
                } else if (nextSplit != null && nextSplit[0] == "u") {
                    minimumUid = nextSplit[1].toIntOrNull() ?: Int.MAX_VALUE
                }

                var didCompleteUsers = minimumUid == Int.MAX_VALUE
                var didCompleteProjects = minimumPid == Int.MAX_VALUE

                var offset = 0
                while (minimumUid < Int.MAX_VALUE && offset < outputBufferLimit) {
                    storeArray.clear()
                    providerIndex.findUidStores(storeArray, minimumUid, storeArrayMaxResults)
                    for (elem in storeArray) {
                        if (offset >= outputBufferLimit) break
                        minimumUid = elem

                        useBuckets(findOrLoadBucket(elem, 0)) { store ->
                            offset += store.search(idCard, outputBuffer, offset, initialId, predicate = filter)
                            ShouldContinue.ifThisIsTrue(offset < outputBuffer.size)
                        }

                        initialId = -1L
                    }

                    if (storeArray.size < storeArrayMaxResults) {
                        if (offset < outputBufferLimit) didCompleteUsers = true
                        break
                    }
                }

                while (minimumPid < Int.MAX_VALUE && offset < outputBufferLimit) {
                    storeArray.clear()
                    providerIndex.findPidStores(storeArray, minimumPid, storeArrayMaxResults)
                    for (elem in storeArray) {
                        if (offset >= outputBufferLimit) break
                        minimumPid = elem

                        useBuckets(findOrLoadBucket(0, elem)) { store ->
                            offset += store.search(idCard, outputBuffer, offset, initialId, predicate = filter)
                            ShouldContinue.ifThisIsTrue(offset < outputBuffer.size)
                        }

                        initialId = -1L
                    }

                    if (storeArray.size < storeArrayMaxResults) {
                        if (offset < outputBufferLimit) didCompleteProjects = true
                        break
                    }
                }

                val idOfLastElement = if (offset >= outputBufferLimit) {
                    outputBuffer[outputBufferLimit - 1].id
                } else {
                    0
                }

                return BrowseResult(
                    min(outputBufferLimit, offset),
                    when {
                        didCompleteProjects -> null
                        didCompleteUsers -> "p-$minimumPid-$idOfLastElement"
                        else -> "u-$minimumUid-$idOfLastElement"
                    }
                )
            }

            is IdCard.User -> {
                val uid = if (idCard.activeProject <= 0) idCard.uid else 0
                val pid = if (uid == 0) idCard.activeProject else 0

                val startId = next?.toLongOrNull() ?: -1L

                val reverseOrder = sortDirection == SortDirection.descending

                var offset = 0
                useBuckets(findOrLoadBucket(uid, pid), reverseOrder = reverseOrder) { store ->
                    offset += store.search(
                        idCard,
                        outputBuffer,
                        offset,
                        startIdExclusive = startId,
                        reverseOrder = reverseOrder,
                        predicate = filter
                    )
                    ShouldContinue.ifThisIsTrue(offset < outputBufferLimit)
                }

                val newNext = if (offset >= outputBufferLimit) {
                    val lastElement = outputBuffer[outputBufferLimit - 1].id
                    lastElement.toString()
                } else {
                    null
                }

                return BrowseResult(min(outputBufferLimit, offset), newNext)
            }
        }
    }

    suspend fun <K> browseWithSort(
        idCard: IdCard,
        outputBuffer: Array<ResourceDocument<T>>,
        next: String?,
        flags: ResourceIncludeFlags,
        keyExtractor: DocKeyExtractor<T, K>,
        comparator: Comparator<K>,
        sortDirection: SortDirection?,
        outputBufferLimit: Int = outputBuffer.size,
        additionalFilters: FilterFunction<T>? = null,
    ): BrowseResult {
        @Suppress("NAME_SHADOWING")
        val sortDirection = sortDirection ?: SortDirection.ascending

        data class Entry(val id: Long, val key: K)

        if (idCard !is IdCard.User) {
            return browse(idCard, outputBuffer, next, flags, outputBufferLimit, additionalFilters)
        }

        val filter = buildFilterFunction(flags, additionalFilters)
        val uid = if (idCard.activeProject <= 0) idCard.uid else 0
        val pid = if (uid == 0) idCard.activeProject else 0

        val rootStore = findOrLoadBucket(uid, pid)

        // NOTE(Dan): Sorting is quite a bit more expensive than normal browsing since we cannot use a stable key which
        // allows us to easily find the relevant dataset. Instead, we must always fetch _all_ resources, sort and then
        // jump to the relevant part. This also means that there are no guarantees about the stability of the results.
        //
        // Because of this, we put a hard limit on the number of documents we want to traverse through. This is
        // currently set very arbitrarily below. We may need to tweak the limit since memory usage might simply be too
        // big at this value. If there are too many resources, then we fall back to the normal browse endpoint,
        // producing results sorted by ID.
        var storeCount = 0
        useBuckets(rootStore) { _ ->
            storeCount++
            ShouldContinue.YES
        }

        if (storeCount > 100) {
            return browse(idCard, outputBuffer, next, flags, outputBufferLimit, additionalFilters)
        }

        // NOTE(Dan): Technically, there is a race-condition here. Someone could in theory create a billion stores
        // between our check and here. I don't think this is even remotely likely to occur, however.

        val temp = ResourceDocument<T>()
        val entries = ArrayList<Entry>()

        val consumer = object : SearchConsumer {
            var currentStore: ResourceStoreBucket<T>? = null
            override fun call(arrIdx: Int) {
                val store = currentStore ?: return
                if (store.id[arrIdx] == 0L) return
                store.loadIntoDocument(temp, arrIdx)
                if (filter.filter(temp)) {
                    entries.add(Entry(temp.id, keyExtractor.extract(temp)))
                }
            }
        }

        useBuckets(rootStore) { store ->
            consumer.currentStore = store
            store.searchAndConsume(idCard, Permission.READ, consumer)
            ShouldContinue.YES
        }

        var entryComparator = Comparator<Entry> { a, b ->
            val res = comparator.compare(a.key, b.key)
            if (res == 0) {
                a.id.compareTo(b.id)
            } else {
                res
            }
        }

        if (sortDirection == SortDirection.descending) entryComparator = entryComparator.reversed()

        entries.sortWith(entryComparator)

        val offset = next?.toIntOrNull() ?: 0
        val idArray = entries
            .asSequence()
            .drop(offset)
            .take(outputBufferLimit)
            .map { it.id }
            .toList()
            .toLongArray()

        val count = retrieveBulk(idCard, idArray, outputBuffer)
        (count until outputBuffer.size).forEach { outputBuffer[it].id = 0L }

        val docComparator = Comparator<ResourceDocument<T>> { a, b ->
            when {
                a.id == b.id -> 0
                a.id == 0L -> 1
                b.id == 0L -> -1
                else -> {
                    val aKey = keyExtractor.extract(a)
                    val bKey = keyExtractor.extract(b)
                    val res = comparator.compare(aKey, bKey)
                    if (res == 0) {
                        if (sortDirection == SortDirection.descending) {
                            b.id.compareTo(a.id)
                        } else {
                            a.id.compareTo(b.id)
                        }
                    } else {
                        res
                    }
                }
            }
        }
        outputBuffer.sortWith(docComparator)

        val nextIndex = offset + outputBufferLimit
        val nextToken = if (nextIndex > entries.lastIndex) {
            null
        } else {
            nextIndex.toString()
        }

        return BrowseResult(count, nextToken)
    }

    suspend fun paginateUsingIdsFromCustomIndex(
        idCard: IdCard,
        output: Array<ResourceDocument<T>>,
        index: Collection<Long>,
        filter: FilterFunction<T>,
        outputBufferLimit: Int = output.size,
        next: String? = null,
    ): BrowseResult {
        // Phase 1: Figure out which of the running IDs the provider can access by looking them up
        val allIds = java.util.ArrayList<Long>()
        index.asSequence().chunked(output.size).map { it.toLongArray() }.forEach { chunk ->
            val count = retrieveBulk(idCard, chunk, output, Permission.READ)
            for (i in 0 until count) {
                if (!filter.filter(output[i])) continue
                allIds.add(output[i].id)
            }
        }

        // Phase 2: Sort them and paginate to the correct place
        allIds.sort()
        var idxToStartAt = 0
        val nextId = next?.toLongOrNull()
        if (nextId != null) {
            var i = 0

            while (i < allIds.size) {
                if (allIds[i] >= nextId) break
                i++
            }
            idxToStartAt = i
        }

        val idsToReturn = allIds
            .asSequence()
            .drop(idxToStartAt)
            .take(outputBufferLimit)
            .toList().toLongArray()

        // Phase 3: Lookup the relevant results and place them in the output page
        val count = retrieveBulk(idCard, idsToReturn, output, Permission.READ)
        val nextToken = if (allIds.size - idxToStartAt - outputBufferLimit > 0) {
            output[count - 1].id.toString()
        } else {
            null
        }

        return BrowseResult(count, nextToken)
    }

    suspend fun modify(
        idCard: IdCard,
        output: Array<ResourceDocument<T>>,
        ids: LongArray,
        permission: Permission,
        consumer: ResourceStoreBucket<T>.(arrIndex: Int, doc: ResourceDocument<T>) -> Unit,
    ): Int {
        if (ids.isEmpty()) return 0
        val storesVisited = HashSet<Pair<Int, Int>>()
        val filter = object : FilterFunction<T> {
            override fun filter(doc: ResourceDocument<T>): Boolean {
                return doc.id in ids
            }
        }

        val minimumIdExclusive = ids.min() - 1

        var offset = 0
        for (id in ids) {
            val initialStore = findStoreByResourceId(id) ?: continue

            val storeKey = Pair(initialStore.uid, initialStore.pid)
            if (storeKey in storesVisited) continue
            storesVisited.add(storeKey)

            useBuckets(initialStore) { currentStore ->
                offset += currentStore.modify(
                    idCard,
                    output,
                    offset,
                    startIdExclusive = minimumIdExclusive,
                    predicate = filter,
                    permission = permission,
                    consumer = consumer
                )
                ShouldContinue.ifThisIsTrue(offset < output.size)
            }
        }
        return offset
    }

    suspend fun addUpdate(
        idCard: IdCard,
        id: Long,
        updates: List<ResourceDocumentUpdate>,
        consumer: (ResourceStoreBucket<T>.(state: T, updateIdx: Int, arrIdx: Int) -> Boolean)? = null,
    ) {
        if (idCard !is IdCard.Provider && idCard !is IdCard.System) {
            throw RPCException("End-users are not allowed to add updates to a resource!", HttpStatusCode.Forbidden)
        }

        useBuckets(findStoreByResourceId(id) ?: return) { store ->
            ShouldContinue.ifThisIsTrue(!store.addUpdates(idCard, id, updates, consumer))
        }
    }

    suspend fun delete(
        idCard: IdCard,
        ids: LongArray,
        permission: Permission = Permission.EDIT,
    ): Int {
        if (ids.isEmpty()) return 0
        val storesVisited = HashSet<Pair<Int, Int>>()
        val filter = object : FilterFunction<T> {
            override fun filter(doc: ResourceDocument<T>): Boolean {
                return doc.id in ids
            }
        }

        val minimumIdExclusive = ids.min() - 1

        var offset = 0
        for (id in ids) {
            val initialStore = findStoreByResourceId(id) ?: continue

            val storeKey = Pair(initialStore.uid, initialStore.pid)
            if (storeKey in storesVisited) continue
            storesVisited.add(storeKey)

            useBuckets(initialStore) { currentStore ->
                offset += currentStore.delete(
                    idCard,
                    predicate = filter,
                    permission = permission,
                    startIdExclusive = minimumIdExclusive,
                )
                ShouldContinue.ifThisIsTrue(offset < ids.size)
            }
        }
        return offset
    }

    suspend fun retrieve(
        idCard: IdCard,
        id: Long,
        permission: Permission = Permission.READ,
    ): ResourceDocument<T>? {
        // NOTE(Dan): This is just a convenience wrapper around retrieveBulk()
        val output = arrayOf(ResourceDocument<T>())
        val success = retrieveBulk(idCard, longArrayOf(id), output, permission) == 1
        if (!success) return null
        return output[0]
    }

    suspend fun retrieveBulk(
        idCard: IdCard,
        ids: LongArray,
        output: Array<ResourceDocument<T>>,
        permission: Permission = Permission.READ,
    ): Int {
        if (ids.isEmpty()) return 0
        val storesVisited = HashSet<Pair<Int, Int>>()
        val filter = object : FilterFunction<T> {
            override fun filter(doc: ResourceDocument<T>): Boolean {
                return doc.id in ids
            }
        }

        val minimumIdExclusive = ids.min() - 1

        var offset = 0
        for (id in ids) {
            val initialStore = findStoreByResourceId(id) ?: continue

            val storeKey = Pair(initialStore.uid, initialStore.pid)
            if (storeKey in storesVisited) continue
            storesVisited.add(storeKey)

            useBuckets(initialStore) { currentStore ->
                offset += currentStore.search(
                    idCard,
                    output,
                    offset,
                    minimumIdExclusive,
                    predicate = filter,
                    permission = permission,
                )
                ShouldContinue.ifThisIsTrue(offset < output.size)
            }
        }
        return offset
    }

    suspend fun updateProviderId(idCard: IdCard, id: Long, providerId: String?) {
        useBuckets(findStoreByResourceId(id) ?: return) { store ->
            ShouldContinue.ifThisIsTrue(!store.updateProviderId(idCard, id, providerId))
        }
    }

    suspend fun updateAcl(
        idCard: IdCard,
        id: Long,
        deletions: List<NumericAclEntry>,
        additions: List<NumericAclEntry>
    ): Boolean {
        if (!additions.all { it.permission?.canBeGranted == true }) {
            throw RPCException("Invalid request supplied", HttpStatusCode.BadRequest)
        }

        var didFindAny = false
        useBuckets(findStoreByResourceId(id) ?: return false) { store ->
            val foundSomething = store.updateAcl(idCard, id, deletions, additions)
            if (foundSomething) didFindAny = true
            ShouldContinue.ifThisIsTrue(!foundSomething)
        }

        return didFindAny
    }

    // Evictions
    // =================================================================================================================
    // Buckets can be evicted from the store. All buckets of a workspace are always evicted together. Data of a bucket
    // is flushed to the database before eviction is final. If an operation arrives at a bucket which has been marked
    // for eviction, then an EvictionException is thrown. The useBuckets() function automatically retries an operation
    // if it arrives at a bucket which has been marked for eviction.
    suspend fun evict(uid: Int, pid: Int) {
        val bucket = if (uid == 0) pidShortcut[pid.toLong()] else uidShortcut[uid.toLong()]
        if (bucket == null) return // Nothing to do, it has essentially already been evicted

        bucket.evictAll()
    }


    fun initializeEvictionTriggersIfRelevant(qualifiedTable: String, resourceKeyColumn: String) {
        if (queries !is ResourceStoreDatabaseQueriesImpl<T>) return
        runBlocking {
            queries.initializeEvictionTriggers(qualifiedTable, resourceKeyColumn)
        }
    }

    // ID index
    // =================================================================================================================
    // NOTE(Dan): The IdIndex contains references to all resources that we have loaded by their ID. Indexes we have
    // stored must always be up-to-date with the real state, meaning that we do not store partial blocks but only
    // complete blocks. References stored within a block are allowed to point to invalid data and/or unloaded data.
    private val idIndex = NonBlockingHashMapLong<IdIndex>()

    private class IdIndex(val minimumId: Long) {
        val entries = IntArray(BLOCK_SIZE)
        val entryIsUid = BooleanArray(BLOCK_SIZE)

        fun register(id: Long, reference: Int, isUid: Boolean) {
            val slot = (id - minimumId).toInt()
            require(slot in 0 until BLOCK_SIZE) {
                "id is out of bounds for this block: minimum = $minimumId, id = $id"
            }

            entries[slot] = reference
            entryIsUid[slot] = isUid
        }

        companion object {
            const val BLOCK_SIZE = 1024 * 128
        }
    }

    private suspend fun initializeIdIndex(baseId: Long): IdIndex {
        val result = IdIndex(baseId)

        queries.withSession { session ->
            queries.loadIdIndex(session, type, baseId, baseId + IdIndex.BLOCK_SIZE) { id, ref, isUser ->
                result.register(id, ref, isUser)
            }
        }

        return result
    }

    private suspend fun loadIdIndexes(vararg ids: Long) {
        val blocksToLoad = LongArray(ids.size)
        for ((index, id) in ids.withIndex()) {
            blocksToLoad[index] = (id / IdIndex.BLOCK_SIZE) * IdIndex.BLOCK_SIZE
        }

        val maxValid = ResourceIdAllocator.maximumValid(queries)
        blocksToLoad.sort()
        var prev = -1L
        for (block in blocksToLoad) {
            if (block == prev) continue
            prev = block

            // NOTE(Dan): This check is required to avoid a DOS attack where a malicious user requests random
            // IDs to force allocations of empty indices.
            if (block > maxValid) continue

            val loadedIndex = initializeIdIndex(block)
            idIndex.putIfAbsent(loadedIndex.minimumId, loadedIndex)
        }
    }

    private fun findIdIndex(id: Long): IdIndex? {
        CreateCounters.idIndexFind.measureValue {
            val query = (id / IdIndex.BLOCK_SIZE) * IdIndex.BLOCK_SIZE
            return idIndex[query]
        }
    }

    private suspend fun findIdIndexOrLoad(id: Long): IdIndex? {
        var queryResult = findIdIndex(id)
        if (queryResult == null) {
            CreateCounters.idIndexLoad.measureValue {
                loadIdIndexes(id)
            }
            queryResult = findIdIndex(id)
        }
        return queryResult
    }

    private suspend fun findStoreByResourceId(id: Long): ResourceStoreBucket<T>? {
        val loaded = findIdIndexOrLoad(id) ?: return null
        val slot = (id - loaded.minimumId).toInt()
        val reference = loaded.entries[slot]
        val referenceIsUid = loaded.entryIsUid[slot]

        return findOrLoadBucket(if (referenceIsUid) reference else 0, if (referenceIsUid) 0 else reference)
    }

    // Provider index
    // =================================================================================================================
    // NOTE(Dan): This index contains references to all resources that are owned by a provider. It becomes automatically
    // populated when a provider contacts us about any resource/when a user requests a resource from them.
    private val providerIndex = arrayOfNulls<ProviderIndex>(MAX_PROVIDERS)
    private val providerIndexMutex = Mutex() // NOTE(Dan): Only needed to modify the list of indices

    private class ProviderIndex(val provider: String) {
        private val uidReferences = ShardedSortedIntegerSet()
        private val pidReferences = ShardedSortedIntegerSet()

        fun registerUsage(uid: Int, pid: Int) {
            if (pid != 0) {
                pidReferences.add(pid)
            } else {
                uidReferences.add(uid)
            }
        }

        fun findUidStores(output: IntList, minimumUid: Int, maxCount: Int) {
            uidReferences.findValues(output, minimumUid, maxCount)
        }

        fun findPidStores(output: IntList, minimumPid: Int, maxCount: Int) {
            pidReferences.findValues(output, minimumPid, maxCount)
        }
    }

    private suspend fun findOrLoadProviderIndex(provider: String): ProviderIndex {
        val initialResult = providerIndex.find { it?.provider == provider }
        if (initialResult != null) {
            return initialResult
        }

        providerIndexMutex.withLock {
            val resultAfterLock = providerIndex.find { it?.provider == provider }
            if (resultAfterLock != null) {
                return resultAfterLock
            }

            val emptySlot = providerIndex.indexOf(null)
            if (emptySlot == -1) error("Too many providers registered with UCloud")

            val result = ProviderIndex(provider)

            queries.withSession { session ->
                queries.loadProviderIndex(session, type, provider) { uid, pid ->
                    result.registerUsage(uid, pid)
                }
            }

            providerIndex[emptySlot] = result

            return result
        }
    }

    companion object : Loggable {
        const val MAX_PROVIDERS = 256
        override val log = logger()
    }
}

class ResourceStoreBucket<T>(
    val type: String,
    val uid: Int,
    val pid: Int,
    private val queries: ResourceStoreDatabaseQueries<T>,
    val callbacks: ResourceStore.Callbacks<T>,
) {
    // Internal data
    // =================================================================================================================
    // Each individual bucket contains data for a single workspace (identified by `uid` and `pid`). A bucket only
    // contains the data of a single resource `type`. The buckets know how to load and store the "user" data through
    // the `callbacks`.

    // The buckets are chained together to form a doubly linked-list using the `next` and `previous` properties.
    // The buckets can be expanded using `expand()`.
    var next: ResourceStoreBucket<T>? = null
        private set

    var previous: ResourceStoreBucket<T>? = null
        private set

    fun findTail(): ResourceStoreBucket<T> {
        if (next == null) return this
        return next!!.findTail()
    }

    suspend fun expand(loadRequired: Boolean = false, hasLock: Boolean = false): ResourceStoreBucket<T> {
        val currentNext = next
        if (currentNext != null) return currentNext

        if (!hasLock) mutex.lock()
        try {
            val nextAfterMutex = next
            if (nextAfterMutex != null) return nextAfterMutex

            val newStore = ResourceStoreBucket<T>(type, uid, pid, queries, callbacks)
            newStore.previous = this
            next = newStore
            if (!loadRequired) newStore.ready.set(true)
            return newStore
        } finally {
            if (!hasLock) mutex.unlock()
        }
    }

    // All data in a bucket is accessed exclusively using a single mutex. This means that there can only ever be one
    // read/write operation for every workspace happening concurrently. This hugely simplifies the design without
    // severely impacting the scalability. Internal statistics have shown that resources are fairly evenly spread across
    // multiple workspaces. Even the biggest workspaces are not that much bigger than the rest.
    private val mutex = Mutex()

    // The data itself is stored column-wise in arrays of a fixed size (`STORE_SIZE`). The "row-wise" version of this
    // data is stored in a ResourceDocument. We store this information in arrays to increase data locality when
    // searching through the entries in a bucket. This also enables us to use vectorized operations.
    val id = LongArray(STORE_SIZE)
    val createdAt = LongArray(STORE_SIZE)
    val createdBy = IntArray(STORE_SIZE)
    val project = IntArray(STORE_SIZE)
    val product = IntArray(STORE_SIZE)
    val providerId = arrayOfNulls<String>(STORE_SIZE)
    val aclEntities = arrayOfNulls<IntArray>(STORE_SIZE)
    val aclIsUser = arrayOfNulls<BooleanArray>(STORE_SIZE)
    val aclPermissions = arrayOfNulls<ByteArray>(STORE_SIZE)
    val updates = arrayOfNulls<CyclicArray<ResourceDocumentUpdate>>(STORE_SIZE)
    val flaggedForDelete = BooleanArray(STORE_SIZE)
    val data = arrayOfNulls<Any>(STORE_SIZE)
    fun data(idx: Int): T? {
        @Suppress("UNCHECKED_CAST")
        return data[idx] as T?
    }

    // We store the number of valid entries in the `size` property. Each time a new entry is created, we increment
    // size by one.
    var size: Int = 0

    // Each bucket keeps track of which entries have been changed since the last synchronization to the database. The
    // `dirtyFlag` array tracks this on a resource level. The `anyDirty` flag can be used to quickly determine if any
    // `dirtyFlag[x] = true`.
    private val dirtyFlag = BooleanArray(STORE_SIZE)
    private var anyDirty = false

    // Buckets can be evicted from the system. All buckets of a workspace are always evicted together. Once a bucket
    // has been marked for eviction then no more operations will be accepted. Operations sent to an evicted bucket will
    // throw an EvictionException which will cause the ResourceStore to retry the operation.
    var evicted = false
        private set

    suspend fun awaitEvictionComplete() {
        check(evicted) { "we have not been evicted?" }
        mutex.withLock {  }
    }

    suspend fun evictAll() {
        check(previous == null) { "evictions can only take place on the root!" }

        val allLocks = ArrayList<Mutex>()

        run {
            var current: ResourceStoreBucket<T> = this
            while (true) {
                allLocks.add(current.mutex)
                current.mutex.lock()
                current.evicted = true
                current = current.next ?: break
            }
        }

        queries.withSession { session ->
            var current: ResourceStoreBucket<T> = this
            while (true) {
                current.synchronizeToDatabase(session, hasLock = true)
                current = current.next ?: break
            }
        }

        allLocks.forEach { it.unlock() }
    }

    // Loading data from the database
    // =================================================================================================================
    // The data stored in a bucket is loaded from a database and stored in the internal representation. During the load
    // we may create multiple buckets if there is not enough space in a single bucket. The bucket capacity has been
    // selected in such a way that it aligns nicely with the page size of the system. Each bucket has been sized to
    // ensure that most workspaces will be able to fit within a single bucket.

    // Operations may arrive at a bucket before the data has finished loading. As a result, we keep track of our own
    // ready state. Operations uses `awaitReady()` to make sure that the data has loaded before they begin their
    // operation.
    private val ready = AtomicBoolean(false)
    private suspend fun awaitReady() {
        while (true) {
            if (ready.get() || evicted) break
            delay(1)
        }

        if (evicted) throw EvictionException()
    }

    // Each bucket is assigned a minimum resource id. For the root bucket, this minimum id is 0. If we do not have
    // enough space in a single bucket, then new buckets are added to the list and load starting at the ID of the
    // previous bucket.
    //
    // The `load()` function itself uses a straightforward function to retrieve the data in bulk from the database. The
    // function goes through three steps:
    //
    // 1. Declare a SQL cursor which will fetch the data
    // 2. Fetch data from the cursor and translate into our internal representation
    // 3. If we run out of space in the current bucket, `expand()` with a new bucket and begin a new `load()`
    suspend fun load(minimumId: Long = 0) {
        mutex.withLock {
            if (evicted) throw EvictionException()

            val nextId = queries.withSession { session ->
                queries.loadResources(session, this, minimumId)
            }

            if (nextId != null) {
                expand(loadRequired = true, hasLock = true).load(nextId)
            }

            ready.set(true)
        }
    }

    // Operations
    // =================================================================================================================
    // UCloud supports a relatively small number of operations on resources, all being CRUD-like operations.
    //
    // The core operations are:
    // - create: Adds a new resource into this bucket and returns the ID
    // - searchAndConsume: Searches for resources which the user can access and passes them to a consumer
    // - delete: Deletes a resource from the bucket
    //
    // A number of secondary operations are implemented using `searchAndConsume`.


    // The create operation adds a new resource to this bucket, if there is space. If there is no space, then it will
    // return a negative value. The caller is responsible for expanding the bucket.
    suspend fun create(
        createdByUid: Int,
        product: Int,
        data: T,
        providerGeneratedId: String?,
        output: ResourceDocument<T>?,
    ): Long {
        awaitReady()

        mutex.withLock {
            if (size >= STORE_SIZE - 1) return -1

            val idx = size++
            val id = ResourceIdAllocator.allocate(queries)

            this.id[idx] = id
            this.createdAt[idx] = System.currentTimeMillis()
            this.createdBy[idx] = createdByUid
            this.project[idx] = pid
            this.product[idx] = product
            this.data[idx] = data
            this.providerId[idx] = providerGeneratedId
            this.dirtyFlag[idx] = true
            anyDirty = true

            if (output != null) {
                output.id = id
                output.createdAt = this.createdAt[idx]
                output.createdBy = createdByUid
                output.project = pid
                output.product = product
                output.data = data
                output.providerId = providerGeneratedId
            }
            return id
        }
    }

    private fun SearchConsumer.withValidation(): SearchConsumer {
        val base = this
        return object : SearchConsumer {
            override fun shouldTerminate(): Boolean = base.shouldTerminate()
            override fun call(arrIdx: Int) {
                if (flaggedForDelete[arrIdx]) return
                if (id[arrIdx] == 0L) return
                return base.call(arrIdx)
            }
        }
    }

    // The searchAndConsume function is the primary function for finding data that a user/provider is allowed to access.
    // This function has several optimized paths for different edge-cases. The optimizations include:
    //
    // - If we know a minimum/maximum ID of the resource, then we perform a binary search through the `id` array to
    //   find the approximate starting location.
    // - If the user is an administrator, then we do not need to check any ACL, instead we can simply return everything
    //   (unless provider privileges are required).
    // - If we must use an ACL, then we use vectorized operations to quickly search through the ACLs.
    //
    // The function can consume resources in either direction. If `reverseOrder` is true then results will go from
    // newest to oldest. Similarly, if false then it will go from oldest to newest.
    //
    // Note that the `consumer` can be called with results that are not valid (e.g. arrIdx > size) or values which are
    // before/after the starting id. It is the responsibility of the `consumer` to ignore these.
    suspend fun searchAndConsume(
        idCard: IdCard,
        permissionRequired: Permission,
        consumer: SearchConsumer,
        startId: Long = -1L,
        reverseOrder: Boolean = false,
    ) {
        awaitReady()
        val validatedConsumer = consumer.withValidation()

        mutex.withLock {
            val startIndex =
                when {
                    startId == -1L && reverseOrder -> {
                        binarySearchForId(Long.MAX_VALUE, hasLock = true) - 2
                    }

                    reverseOrder -> {
                        binarySearchForId(startId, hasLock = true) - 2
                    }

                    startId == -1L && !reverseOrder -> {
                        0
                    }

                    else -> {
                        max(0, binarySearchForId(startId, hasLock = true) - 1)
                    }
                }

            val isOwnerOfEverything =
                idCard == IdCard.System ||
                        (
                                idCard is IdCard.User &&
                                        ((uid != 0 && idCard.uid == uid) || (pid != 0 && idCard.adminOf.contains(pid)))
                                )

            if (isOwnerOfEverything) {
                val requiresOwnerPrivileges = permissionRequired == Permission.READ ||
                        permissionRequired == Permission.EDIT ||
                        permissionRequired == Permission.ADMIN

                if (requiresOwnerPrivileges || idCard == IdCard.System) {
                    if (reverseOrder) {
                        var idx = startIndex
                        while (idx >= 0 && idx % 4 != 0 && !validatedConsumer.shouldTerminate()) {
                            validatedConsumer.call(idx--)
                        }

                        while (idx >= 4 && !validatedConsumer.shouldTerminate()) {
                            validatedConsumer.call(idx)
                            validatedConsumer.call(idx - 1)
                            validatedConsumer.call(idx - 2)
                            validatedConsumer.call(idx - 3)

                            idx -= 4
                        }

                        while (idx >= 0 && !validatedConsumer.shouldTerminate()) {
                            validatedConsumer.call(idx--)
                        }
                    } else {
                        var idx = startIndex

                        while (idx < STORE_SIZE && idx % 4 != 0 && !validatedConsumer.shouldTerminate()) {
                            if (this.id[idx] == 0L) break
                            validatedConsumer.call(idx++)
                        }

                        while (idx < STORE_SIZE - 3 && !validatedConsumer.shouldTerminate()) {
                            if (this.id[idx] == 0L) break
                            validatedConsumer.call(idx)
                            validatedConsumer.call(idx + 1)
                            validatedConsumer.call(idx + 2)
                            validatedConsumer.call(idx + 3)

                            idx += 4
                        }

                        while (idx < STORE_SIZE && !validatedConsumer.shouldTerminate()) {
                            if (this.id[idx] == 0L) break
                            validatedConsumer.call(idx++)
                        }
                    }
                }
            } else if (idCard is IdCard.User) {
                val entityConsumer = object : SearchConsumer {
                    var shouldBeUser = false
                    var entityIdx = 0
                    var match = -1

                    override fun shouldTerminate(): Boolean = match != -1
                    override fun call(arrIdx: Int) {
                        if (match != -1) return

                        if (aclIsUser[entityIdx]!![arrIdx] == shouldBeUser &&
                            aclPermissions[entityIdx]!![arrIdx] == permissionRequired.toByte()
                        ) {
                            match = arrIdx
                        }
                    }
                }

                val userNeedle = intArrayOf(idCard.uid)

                var i = startIndex
                val delta = if (reverseOrder) -1 else 1
                while (i >= 0 && i < STORE_SIZE && !validatedConsumer.shouldTerminate()) {
                    val entities = aclEntities[i]
                    if (id[i] == 0L || entities == null) {
                        i += delta
                        continue
                    }

                    entityConsumer.entityIdx = i

                    // Match groups
                    entityConsumer.match = -1
                    entityConsumer.shouldBeUser = false
                    searchInArray(idCard.groups, entities, entityConsumer)
                    if (entityConsumer.match != -1) {
                        validatedConsumer.call(i)
                    } else {
                        // If no groups match, try uid
                        entityConsumer.match = -1
                        entityConsumer.shouldBeUser = true
                        searchInArray(userNeedle, entities, entityConsumer)
                        if (entityConsumer.match != -1) validatedConsumer.call(i)
                    }


                    i += delta
                }
            } else if (idCard is IdCard.Provider && idCard.providerOf.isNotEmpty()) {
                val requiresProviderPrivileges =
                    permissionRequired == Permission.READ ||
                            permissionRequired == Permission.EDIT ||
                            permissionRequired == Permission.PROVIDER

                if (requiresProviderPrivileges) {
                    searchInArray(idCard.providerOf, product, validatedConsumer, startIndex)
                }
            }
        }
    }

    /**
     * Finds the index you should start at if you are looking for a value which is greater than [minimumId].
     *
     * The return value will be equal to the length (i.e. out of bounds) if the value cannot exist in the array.
     */
    private suspend fun binarySearchForId(minimumId: Long, hasLock: Boolean): Int {
        if (!hasLock) mutex.lock()
        try {
            var min = 0
            var max = id.size - 1
            while (min <= max) {
                val middle = (min + max) / 2
                val middleId = id[middle]

                if (middleId != 0L && middleId < minimumId) {
                    min = middle + 1
                } else if (middleId == 0L || middleId > minimumId) {
                    max = middle - 1
                } else {
                    return middle + 1
                }
            }
            if (min == 0 && id[min] != minimumId) return 0
            return min + 1
        } finally {
            if (!hasLock) mutex.unlock()
        }
    }

    suspend fun search(
        idCard: IdCard,
        outputBuffer: Array<ResourceDocument<T>>,
        outputBufferOffset: Int,
        startIdExclusive: Long = -1L,
        reverseOrder: Boolean = false,
        predicate: FilterFunction<T>,
        permission: Permission = Permission.READ,
    ): Int {
        var outIdx = outputBufferOffset

        val self = this
        val emitter = object : SearchConsumer {
            override fun shouldTerminate(): Boolean = outIdx >= outputBuffer.size
            override fun call(arrIdx: Int) {
                if (outIdx >= outputBuffer.size) return
                if (self.id[arrIdx] == 0L) return

                if (startIdExclusive != -1L) {
                    if (!reverseOrder && self.id[arrIdx] <= startIdExclusive) return
                    else if (reverseOrder && self.id[arrIdx] >= startIdExclusive) return
                }

                val res = outputBuffer[outIdx++]
                loadIntoDocument(res, arrIdx)

                if (!predicate.filter(res)) {
                    outIdx--
                }
            }
        }

        searchAndConsume(idCard, permission, emitter, startIdExclusive, reverseOrder)

        return outIdx - outputBufferOffset
    }

    // Utility function to load the data stored at `arrIdx` into `res`
    fun loadIntoDocument(
        res: ResourceDocument<T>,
        arrIdx: Int
    ) {
        res.id = this.id[arrIdx]
        res.createdAt = this.createdAt[arrIdx]
        res.createdBy = this.createdBy[arrIdx]
        res.project = this.project[arrIdx]
        res.product = this.product[arrIdx]
        res.providerId = this.providerId[arrIdx]
        @Suppress("UNCHECKED_CAST")
        res.data = this.data[arrIdx] as T
        run {
            Arrays.fill(res.update, null)
            val resourceUpdates = this.updates[arrIdx] ?: return@run
            for ((index, update) in resourceUpdates.withIndex()) {
                if (index >= res.update.size) break
                res.update[index] = update
            }
        }
        run {
            res.acl.clear()
            val entities = this.aclEntities[arrIdx]
            val isUser = this.aclIsUser[arrIdx]
            val perms = this.aclPermissions[arrIdx]

            if (entities != null && isUser != null && perms != null) {
                for (i in entities.indices) {
                    if (entities[i] == 0) break
                    res.acl.add(
                        ResourceDocument.AclEntry(
                            entities[i],
                            isUser[i],
                            Permission.fromByte(perms[i])
                        )
                    )
                }
            }
        }
    }

    suspend fun addUpdates(
        idCard: IdCard,
        id: Long,
        newUpdates: List<ResourceDocumentUpdate>,
        consumer: (ResourceStoreBucket<T>.(state: T, updateIdx: Int, arrIdx: Int) -> Boolean)? = null,
    ): Boolean {
        val self = this
        var isDone = false
        searchAndConsume(idCard, Permission.PROVIDER, object : SearchConsumer {
            override fun shouldTerminate(): Boolean = isDone
            override fun call(arrIdx: Int) {
                if (self.id[arrIdx] != id) return
                self.dirtyFlag[arrIdx] = true
                anyDirty = true
                val updates = self.updates[arrIdx] ?: CyclicArray<ResourceDocumentUpdate>(MAX_UPDATES).also {
                    self.updates[arrIdx] = it
                }

                for ((index, update) in newUpdates.withIndex()) {
                    @Suppress("UNCHECKED_CAST")
                    val shouldAddUpdate =
                        if (consumer != null) consumer(self.data[arrIdx] as T, index, arrIdx) else true
                    if (shouldAddUpdate) updates.add(update)
                }

                isDone = true
            }
        })
        return isDone
    }

    suspend fun modify(
        idCard: IdCard,
        outputBuffer: Array<ResourceDocument<T>>,
        outputBufferOffset: Int,
        predicate: FilterFunction<T>,
        permission: Permission,
        startIdExclusive: Long = -1L,
        consumer: ResourceStoreBucket<T>.(arrIndex: Int, doc: ResourceDocument<T>) -> Unit,
    ): Int {
        var outIdx = outputBufferOffset
        val self = this
        searchAndConsume(
            idCard,
            permission,
            startId = startIdExclusive,
            consumer = object : SearchConsumer {
                override fun shouldTerminate(): Boolean = outIdx >= outputBuffer.size
                override fun call(arrIdx: Int) {
                    if (outIdx >= outputBuffer.size) return
                    if (self.id[arrIdx] == 0L) return

                    if (startIdExclusive != -1L) {
                        if (self.id[arrIdx] <= startIdExclusive) return
                    }

                    val res = outputBuffer[outIdx++]
                    loadIntoDocument(res, arrIdx)

                    if (!predicate.filter(res)) {
                        outIdx--
                    } else {
                        consumer.invoke(this@ResourceStoreBucket, arrIdx, res)

                        self.dirtyFlag[arrIdx] = true
                        anyDirty = true
                    }
                }
            }
        )

        return outIdx - outputBufferOffset
    }

    suspend fun updateProviderId(
        idCard: IdCard,
        id: Long,
        newProviderId: String?,
    ): Boolean {
        val self = this
        var isDone = false
        val consumer = object : SearchConsumer {
            override fun shouldTerminate(): Boolean = isDone
            override fun call(arrIdx: Int) {
                if (self.id[arrIdx] != id) return
                self.providerId[arrIdx] = newProviderId
                isDone = true
            }
        }
        searchAndConsume(idCard, Permission.PROVIDER, consumer, id - 1)
        return isDone
    }

    suspend fun updateAcl(
        idCard: IdCard,
        id: Long,
        deletions: List<NumericAclEntry>,
        additions: List<NumericAclEntry>,
    ): Boolean {
        // NOTE(Dan): This is a bit of a hack since we cannot look where we match one of a set of permissions.
        val permRequired = if (idCard is IdCard.Provider) {
            Permission.PROVIDER
        } else {
            Permission.ADMIN
        }

        val self = this
        val consumer = object : SearchConsumer {
            var done = false
            override fun shouldTerminate(): Boolean = done

            override fun call(arrIdx: Int) {
                if (self.id[arrIdx] != id) return

                self.dirtyFlag[arrIdx] = true
                anyDirty = true

                // NOTE(Dan): This makes no attempts to have great performance. It is unlikely to be a problem since
                // we are not running this code in a loop (right now).

                val uidPerms = HashMap<Int, HashSet<Permission>>()
                val gidPerms = HashMap<Int, HashSet<Permission>>()

                val origEntities = aclEntities[arrIdx] ?: IntArray(0)
                val origUserStatus = aclIsUser[arrIdx] ?: BooleanArray(0)
                val origPerms = aclPermissions[arrIdx] ?: ByteArray(0)

                for (i in origEntities.indices) {
                    if (origEntities[i] == 0) continue

                    if (origUserStatus[i]) {
                        val uid = origEntities[i]
                        val set = uidPerms[uid] ?: HashSet<Permission>().also { uidPerms[uid] = it }
                        set.add(Permission.fromByte(origPerms[i]))
                    } else {
                        val gid = origEntities[i]
                        val set = gidPerms[gid] ?: HashSet<Permission>().also { gidPerms[gid] = it }
                        set.add(Permission.fromByte(origPerms[i]))
                    }
                }

                for (deleted in deletions) {
                    require(deleted.permission == null) { "You must always delete an entity in full" }

                    if (deleted.gid != null) gidPerms.remove(deleted.gid)
                    else if (deleted.uid != null) uidPerms.remove(deleted.uid)
                }

                for (addition in additions) {
                    if (addition.permission == null) continue

                    if (addition.gid != null) {
                        val set = gidPerms[addition.gid] ?: HashSet<Permission>().also { gidPerms[addition.gid] = it }
                        set.add(addition.permission)
                    } else if (addition.uid != null) {
                        val set = uidPerms[addition.uid] ?: HashSet<Permission>().also { uidPerms[addition.uid] = it }
                        set.add(addition.permission)
                    }
                }

                val requiredSize =
                    uidPerms.entries.fold(0) { a, entry -> a + entry.value.size } +
                            gidPerms.entries.fold(0) { a, entry -> a + entry.value.size }

                var entities = aclEntities[arrIdx]
                var userStatus = aclIsUser[arrIdx]
                var perms = aclPermissions[arrIdx]

                val initialSize = entities?.size ?: 0
                if (initialSize < requiredSize) {
                    val size = ((requiredSize / 4) + 1) * 4
                    entities = IntArray(size)
                    userStatus = BooleanArray(size)
                    perms = ByteArray(size)

                    aclEntities[arrIdx] = entities
                    aclIsUser[arrIdx] = userStatus
                    aclPermissions[arrIdx] = perms
                }

                check(entities != null && userStatus != null && perms != null)

                Arrays.fill(entities, 0)

                var ptr = 0
                for ((uid, entryPerms) in uidPerms) {
                    for (perm in entryPerms) {
                        entities[ptr] = uid
                        userStatus[ptr] = true
                        perms[ptr] = perm.toByte()
                        ptr++
                    }
                }
                for ((gid, entryPerms) in gidPerms) {
                    for (perm in entryPerms) {
                        entities[ptr] = gid
                        userStatus[ptr] = false
                        perms[ptr] = perm.toByte()
                        ptr++
                    }
                }

                done = true
            }
        }

        searchAndConsume(idCard, permRequired, consumer, id - 1)
        return consumer.done
    }

    suspend fun delete(
        idCard: IdCard,
        predicate: FilterFunction<T>,
        permission: Permission,
        startIdExclusive: Long = -1L,
    ): Int {
        val temp = ResourceDocument<T>()
        val self = this
        var count = 0
        searchAndConsume(
            idCard,
            permission,
            object : SearchConsumer {
                override fun call(arrIdx: Int) {
                    if (startIdExclusive != -1L) {
                        if (self.id[arrIdx] <= startIdExclusive) return
                    }

                    loadIntoDocument(temp, arrIdx)
                    if (predicate.filter(temp)) {
                        count++
                        flaggedForDelete[arrIdx] = true
                        createdAt[arrIdx] = 0L
                        createdBy[arrIdx] = 0
                        project[arrIdx] = 0
                        product[arrIdx] = 0
                        providerId[arrIdx] = null
                        data[arrIdx] = null
                        updates[arrIdx]?.clear()
                        updates[arrIdx] = null

                        run {
                            val arr = aclEntities
                            val elem = arr[arrIdx]
                            if (elem != null) Arrays.fill(elem, 0)
                            arr[arrIdx] = null
                        }
                        run {
                            val arr = aclIsUser
                            val elem = arr[arrIdx]
                            if (elem != null) Arrays.fill(elem, false)
                            arr[arrIdx] = null
                        }
                        run {
                            val arr = aclPermissions
                            val elem = arr[arrIdx]
                            if (elem != null) Arrays.fill(elem, 0.toByte())
                            arr[arrIdx] = null
                        }

                        dirtyFlag[arrIdx] = true
                        anyDirty = true
                    }
                }
            },
            startIdExclusive,
        )
        return count
    }

    // Saving data to the database
    // =================================================================================================================
    // Data is periodically pushed from the cache to the database through `synchronizeToDatabase`. This function is
    // called by the ResourceStore in a background task. The first stage attempts to determine which resources from
    // the bucket needs to be synchronized. The second stage, which takes place in `sync` actually writes the data to
    // the database. This will also invoke the callbacks to save the user data.
    suspend fun synchronizeToDatabase(transaction: Any, hasLock: Boolean = false) {
        if (!anyDirty) return

        if (!hasLock) mutex.lock()
        try {
            var idx = 0
            val needle = ByteVector.broadcast(bvSpecies, 1.toByte())

            val indicesToSync = IntArray(bvLength * 8) // 128 elements when using AVX-512
            var ptr = 0

            while (idx < STORE_SIZE) {
                val vector = ByteVector.fromBooleanArray(bvSpecies, dirtyFlag, idx)
                val cr1 = vector.compare(VectorOperators.EQ, needle)

                run {
                    var offset = idx
                    var bits = cr1.toLong()
                    while (bits != 0L) {
                        val trailing = java.lang.Long.numberOfTrailingZeros(bits)
                        indicesToSync[ptr++] = offset + trailing
                        offset += trailing + 1
                        bits = bits shr (trailing + 1)
                    }
                }

                idx += bvLength

                if (indicesToSync.size - ptr < bvLength) {
                    sync(transaction, indicesToSync, ptr)
                    ptr = 0
                }
            }

            sync(transaction, indicesToSync, ptr)

            for (i in 0 until STORE_SIZE) {
                dirtyFlag[i] = false
            }

            anyDirty = false
        } finally {
            if (!hasLock) mutex.unlock()
        }
    }

    private suspend fun sync(transaction: Any, indices: IntArray, len: Int) {
        if (len <= 0) return
        queries.saveResources(transaction, this, indices, len)
    }

    override fun toString(): String {
        return buildString {
            append("ResourceStoreByOwner(")
            append("type = $type, ")
            append("uid = $uid, ")
            append("pid = $pid, ")
            append("firstId = ${id[0]}, ")
            if (size > 0) {
                append("lastId = ${id[size - 1]}, ")
            }
            append(")")
        }
    }

    companion object : Loggable {
        override val log = logger()

        const val MAX_UPDATES = 64

        // NOTE(Dan): 99% of all workspaces have less than ~1300 entries, 1024 seem like a good target.
        const val STORE_SIZE = 1024

        private val ivSpecies = IntVector.SPECIES_PREFERRED
        private val ivLength = ivSpecies.length().also { len ->
            check(STORE_SIZE % len == 0) { "The code needs to be adjusted to run on this platform ($len)." }
        }

        private val bvSpecies = ByteVector.SPECIES_PREFERRED
        private val bvLength = bvSpecies.length().also { len ->
            check(STORE_SIZE % len == 0) { "The code needs to be adjusted to run on this platform ($len)." }
        }

        // NOTE(Dan, 04/07/23): This function can easily search through several gigabytes of data per second. But its
        // performance _heavily_ depends on a JVM which supports the Vector module and subsequently correctly produces
        // sane assembly from the C2 JIT. To verify the JIT is doing its job, you may want to add the following JVM flags:
        //
        // -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly -XX:PrintAssemblyOptions=intel
        //
        // You must have the hsdis extension installed for your JVM. Instructions can be found here:
        // https://www.chrisnewland.com/updated-instructions-for-building-hsdis-on-osx-417
        //
        // You should verify that the searchInArrayVector function contains the PCMPEQQ instruction (prefix will vary by
        // CPU architecture). In other words, you should verify that the compare calls are actually using SIMD. In
        // case we ever stop using x64, then the instruction will be something else entirely.
        //
        // We purposefully warm up this function in the `init {}` block of this class to force proper JIT compilation of
        // the function.
        @Suppress("DuplicatedCode")
        private fun searchInArray(
            needles: IntArray,
            haystack: IntArray,
            emit: SearchConsumer,
            startIndex: Int = 0,
        ) {
            var idx = (startIndex / (ivLength * 4)) * (ivLength * 4)
            while (idx < haystack.size - ivLength * 4) {
                for (i in needles) {
                    val needle = IntVector.broadcast(ivSpecies, i)

                    val haystack1 = IntVector.fromArray(ivSpecies, haystack, idx)
                    val haystack2 = IntVector.fromArray(ivSpecies, haystack, idx + ivLength)
                    val haystack3 = IntVector.fromArray(ivSpecies, haystack, idx + ivLength * 2)
                    val haystack4 = IntVector.fromArray(ivSpecies, haystack, idx + ivLength * 3)

                    val cr1 = haystack1.compare(VectorOperators.EQ, needle)
                    val cr2 = haystack2.compare(VectorOperators.EQ, needle)
                    val cr3 = haystack3.compare(VectorOperators.EQ, needle)
                    val cr4 = haystack4.compare(VectorOperators.EQ, needle)

                    run {
                        var offset = idx
                        var bits = cr1.toLong()
                        while (bits != 0L) {
                            val trailing = java.lang.Long.numberOfTrailingZeros(bits)
                            emit.call(offset + trailing)
                            offset += trailing + 1
                            bits = bits shr (trailing + 1)
                        }
                    }

                    run {
                        var offset = idx + ivLength
                        var bits = cr2.toLong()
                        while (bits != 0L) {
                            val trailing = java.lang.Long.numberOfTrailingZeros(bits)
                            emit.call(offset + trailing)
                            offset += trailing + 1
                            bits = bits shr (trailing + 1)
                        }
                    }

                    run {
                        var offset = idx + ivLength * 2
                        var bits = cr3.toLong()
                        while (bits != 0L) {
                            val trailing = java.lang.Long.numberOfTrailingZeros(bits)
                            emit.call(offset + trailing)
                            offset += trailing + 1
                            bits = bits shr (trailing + 1)
                        }
                    }

                    run {
                        var offset = idx + ivLength * 3
                        var bits = cr4.toLong()
                        while (bits != 0L) {
                            val trailing = java.lang.Long.numberOfTrailingZeros(bits)
                            emit.call(offset + trailing)
                            offset += trailing + 1
                            bits = bits shr (trailing + 1)
                        }
                    }
                }
                idx += ivLength * 4
            }

            while (idx < haystack.size) {
                val hay = haystack[idx]
                for (needle in needles) {
                    if (hay == needle) {
                        emit.call(idx)
                        break
                    }
                }
                idx++
            }
        }

        init {
            run {
                // NOTE(Dan): Warmup the JIT for the searchInArray function.

                val needles = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
                val haystack = IntArray(1024) { Random.nextInt(1, 15) }
                val warmupCount = 10_000
                val discard = object : SearchConsumer {
                    override fun call(arrIdx: Int) {}
                }

                repeat(warmupCount) {
                    searchInArray(needles, haystack, discard)
                }
            }
        }
    }
}

// TODO(Dan): This implementation means that we can only have one instance running this
object ResourceIdAllocator {
    private val idAllocator = AtomicLong(-1_000_000_000L)
    private val initMutex = Mutex()

    suspend fun maximumValid(queries: ResourceStoreDatabaseQueries<*>): Long {
        val currentId = idAllocator.get()
        if (currentId < 0L) {
            init(queries)
            return maximumValid(queries)
        }
        return currentId
    }

    suspend fun allocate(queries: ResourceStoreDatabaseQueries<*>): Long {
        val allocatedId = idAllocator.incrementAndGet()
        if (allocatedId < 0L) {
            init(queries)
            return allocate(queries)
        }
        return allocatedId
    }

    private suspend fun init(queries: ResourceStoreDatabaseQueries<*>) {
        initMutex.withLock {
            if (idAllocator.get() >= 0L) return
            queries.withSession { session ->
                idAllocator.set(max(5_000_000L, queries.loadMaximumId(session)))
            }
        }
    }
}

interface FilterFunction<T> {
    fun filter(doc: ResourceDocument<T>): Boolean

    companion object {
        fun <T> noFilter() = object : FilterFunction<T> {
            override fun filter(doc: ResourceDocument<T>): Boolean = true
        }
    }
}

fun <T> FilterFunction<T>.and(other: FilterFunction<T>): FilterFunction<T> {
    val self = this
    return object : FilterFunction<T> {
        override fun filter(doc: ResourceDocument<T>): Boolean {
            return self.filter(doc) && other.filter(doc)
        }
    }
}

interface DocKeyExtractor<T, K> {
    fun extract(doc: ResourceDocument<T>): K
}

// NOTE(Dan, 04/07/23): DO NOT CHANGE THIS TO A LAMBDA. Performance of calling lambdas versus calling normal
// functions through interfaces are dramatically different. My own benchmarks have shown a difference of at
// least 10x just by switching to an interface instead of a lambda.
interface SearchConsumer {
    fun shouldTerminate(): Boolean = false // NOTE(Dan): Do not make this suspend
    fun call(arrIdx: Int) // NOTE(Dan): Do not make this suspend
}

@Serializable
private data class SerializedUpdate(
    val extra: JsonElement? = null,
    val message: String? = null,
    val createdAt: Long? = null,
)

@Serializable
data class NumericAclEntry(
    val uid: Int? = null,
    val gid: Int? = null,
    val permission: Permission? = null,
) {
    companion object {
        suspend fun fromAclEntry(cards: IIdCardService, entry: ResourceAclEntry): List<NumericAclEntry> {
            val uid = when (val entity = entry.entity) {
                is AclEntity.ProjectGroup -> null
                is AclEntity.User -> cards.lookupUidFromUsername(entity.username)
            }

            val gid = when (val entity = entry.entity) {
                is AclEntity.ProjectGroup -> cards.lookupGidFromGroupId(entity.group)
                is AclEntity.User -> null
            }

            return entry.permissions.map { NumericAclEntry(uid, gid, it) }
        }

        suspend fun fromAclEntity(cards: IIdCardService, entity: AclEntity): NumericAclEntry {
            val uid = when (entity) {
                is AclEntity.ProjectGroup -> null
                is AclEntity.User -> cards.lookupUidFromUsername(entity.username)
            }

            val gid = when (entity) {
                is AclEntity.ProjectGroup -> cards.lookupGidFromGroupId(entity.group)
                is AclEntity.User -> null
            }

            return NumericAclEntry(uid, gid, null)
        }
    }
}

fun Permission.Companion.fromByte(value: Byte): Permission {
    return when (value.toInt()) {
        1 -> Permission.READ
        2 -> Permission.EDIT
        3 -> Permission.ADMIN
        4 -> Permission.PROVIDER
        else -> error("unknown value: $value")
    }
}

fun Permission.toByte(): Byte {
    return when (this) {
        Permission.READ -> 1
        Permission.EDIT -> 2
        Permission.ADMIN -> 3
        Permission.PROVIDER -> 4
    }
}

interface ResourceStoreDatabaseQueries<T> {
    suspend fun startTransaction(): Any
    suspend fun commitTransaction(transaction: Any)
    suspend fun abortTransaction(transaction: Any)
    suspend fun loadResources(transaction: Any, bucket: ResourceStoreBucket<T>, minimumId: Long): Long?
    suspend fun saveResources(transaction: Any, bucket: ResourceStoreBucket<T>, indices: IntArray, len: Int)
    suspend fun loadProviderIndex(
        transaction: Any,
        type: String,
        providerId: String,
        register: suspend (uid: Int, pid: Int) -> Unit
    )

    suspend fun loadIdIndex(
        transaction: Any,
        type: String,
        minimumId: Long,
        maximumIdExclusive: Long,
        register: (id: Long, ref: Int, isUser: Boolean) -> Unit
    )

    suspend fun loadMaximumId(transaction: Any): Long
}

suspend inline fun <R> ResourceStoreDatabaseQueries<*>.withSession(block: (Any) -> R): R {
    val session = startTransaction()
    var success = true
    try {
        return block(session)
    } catch (ex: Throwable) {
        success = false
        abortTransaction(session)
        throw ex
    } finally {
        if (success) commitTransaction(session)
    }
}

class EvictionException : RuntimeException("This bucket has been evicted. Try again.")

class ResourceStoreDatabaseQueriesImpl<T>(
    private val db: AsyncDBSessionFactory,
    private val backgroundScope: BackgroundScope,
) : ResourceStoreDatabaseQueries<T> {
    fun init(store: ResourceStore<T>, ) {
        backgroundScope.launch {
            // NOTE(Dan): Annoyingly, it seems like we just have to hold this session open forever
            val session = db.openSession()
            session.sendPreparedStatement("listen resource_eviction")
            session.registerNotifyListener { _, payload ->
                val message = payload.split("-").takeIf { it.size == 3 }
                val type = message?.get(0)
                val pid = message?.get(1)?.toIntOrNull()
                val uid = message?.get(2)?.toIntOrNull()
                if (type != null && uid != null && pid != null) {
                    if (type == store.type) {
                        backgroundScope.launch {
                            log.info("Evicting $uid, $pid")
                            store.evict(uid, pid)
                        }
                    } else {
                        log.debug("Ignoring $type")
                    }
                } else {
                    log.info("Invalid message received: $payload")
                }
            }
        }
    }

    suspend fun initializeEvictionTriggers(qualifiedTable: String, resourceKeyColumn: String) {
        // NOTE(Dan): User-input should never be passed to this function. But we do a sanity check regardless.
        if (!resourceKeyColumn.all { it.isJavaIdentifierPart() }) error("Illegal resource key: $resourceKeyColumn")

        // NOTE(Dan): We do not need to check the table since it will fail the hasTrigger check if it contains an
        // invalid reference.

        db.withSession { session ->
            val hasTrigger = session.sendPreparedStatement(
                { setParameter("table", qualifiedTable) },
                """
                    select tgname
                    from pg_catalog.pg_trigger
                    where
                        not tgisinternal
                        and tgname = 'resource_trigger'
                        and tgrelid = :table::regclass;        
                """
            ).rows.isNotEmpty()

            if (!hasTrigger) {
                session.sendPreparedStatement(
                    {},
                    """
                        create function ${qualifiedTable}_resource_updated() returns trigger language plpgsql as ${'$'}${'$'}
                        begin
                            perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
                            from
                                provider.resource r
                                join auth.principals u on u.id = r.created_by
                                left join project.projects p on r.project = p.id
                            where
                                r.id = new.$resourceKeyColumn;
                            return new;
                        end;
                        ${'$'}${'$'}; 
                    """
                )

                session.sendPreparedStatement(
                    {},
                    """
                        create function ${qualifiedTable}_resource_deleted() returns trigger language plpgsql as ${'$'}${'$'}
                        begin
                            perform pg_notify('resource_eviction', r.type || '-' || coalesce(p.pid, 0) || '-' || u.uid)
                            from
                                provider.resource r
                                join auth.principals u on u.id = r.created_by
                                left join project.projects p on r.project = p.id
                            where
                                r.id = old.$resourceKeyColumn;
                            return old;
                        end;
                        ${'$'}${'$'}; 
                    """
                )

                session.sendPreparedStatement(
                    {},
                    """
                        create trigger resource_trigger
                            before insert or update on $qualifiedTable
                            for each row
                            when (current_setting('ucloud.performed_by_cache', true) is distinct from 'true')
                            execute function ${qualifiedTable}_resource_updated();
                    """
                )

                session.sendPreparedStatement(
                    {},
                    """
                        create trigger resource_trigger_delete
                            before delete on $qualifiedTable
                            for each row
                            when (current_setting('ucloud.performed_by_cache', true) is distinct from 'true')
                            execute function ${qualifiedTable}_resource_deleted();
                    """
                )
            }
        }
    }

    override suspend fun startTransaction(): Any {
        val session = db.openSession()
        db.openTransaction(session)
        session.sendQuery("set local ucloud.performed_by_cache to 'true';")
        return session
    }

    override suspend fun abortTransaction(transaction: Any) {
        val session = (transaction as AsyncDBConnection)
        db.rollback(session)
        db.closeSession(session)
    }

    override suspend fun commitTransaction(transaction: Any) {
        val session = (transaction as AsyncDBConnection)
        db.commit(session)
        db.closeSession(session)
    }

    override suspend fun loadResources(
        transaction: Any,
        bucket: ResourceStoreBucket<T>,
        minimumId: Long
    ): Long? {
        val session = (transaction as AsyncDBConnection)
        with(bucket) {
            var idx = 0
            val textualId = if (uid != 0) {
                session.sendPreparedStatement(
                    { setParameter("uid", uid) },
                    """
                        select id as user_id
                        from auth.principals p
                        where p.uid = :uid;
                    """
                ).rows.singleOrNull()?.getString(0)
            } else {
                session.sendPreparedStatement(
                    { setParameter("pid", pid) },
                    """
                        select id as project_id
                        from project.projects p
                        where p.pid = :pid;
                    """
                ).rows.singleOrNull()?.getString(0)
            } ?: error("could not find $uid $pid in the database")

            session.sendPreparedStatement(
                {
                    setParameter("type", type)
                    setParameter("minimum", minimumId)
                    setParameter("project_id", if (pid != 0) textualId else null)
                    setParameter("username", if (uid != 0) textualId else null)
                },
                """
                    declare c cursor for
                    with
                        resource_ids as (
                            select
                                r.id
                            from
                                provider.resource r
                            where
                                r.type = :type
                                and r.id > :minimum
                                and (
                                    (:project_id::text is not null and r.project = :project_id)
                                    or (:username::text is not null and r.created_by = :username and r.project is null)
                                )
                            order by r.id
                            limit ${ResourceStoreBucket.STORE_SIZE}
                        ),
                        resource_with_updates as (
                            select
                                r.id,
                                u.created_at,
                                u.status,
                                u.extra
                            from
                                resource_ids r
                                left join provider.resource_update u on r.id = u.resource
                            order by u.created_at
                        ),
                        updates_aggregated as (
                            select
                                u.id,
                                jsonb_agg(jsonb_build_object(
                                    'createdAt', floor(extract(epoch from u.created_at) * 1000)::int8,
                                    'message', status,
                                    'extra', extra
                                )) as update
                            from resource_with_updates u
                            group by u.id
                        ),
                        resource_with_acl_entries as (
                            select
                                r.id,
                                u.uid,
                                g.gid,
                                e.permission
                            from
                                resource_ids r
                                left join provider.resource_acl_entry e on e.resource_id = r.id
                                left join auth.principals u on e.username = u.id
                                left join project.groups g on e.group_id = g.id
                        ),
                        acl_aggregated as (
                            select
                                e.id,
                                jsonb_agg(jsonb_build_object(
                                    'uid', e.uid,
                                    'gid', e.gid,
                                    'permission', e.permission
                                )) as acl
                            from resource_with_acl_entries e
                            group by e.id
                        )
                    select
                        u.id,
                        floor(extract(epoch from r.created_at) * 1000)::int8,
                        p.uid,
                        r.product,
                        r.provider_generated_id,
                        u.update,
                        a.acl
                    from
                        updates_aggregated u
                        join acl_aggregated a on u.id = a.id
                        join provider.resource r on u.id = r.id
                        join auth.principals p on r.created_by = p.id
                    order by u.id
                """
            )

            val batchCount = 128
            val batchIds = LongArray(batchCount)
            while (idx < ResourceStoreBucket.STORE_SIZE) {
                val rows = session.sendPreparedStatement({}, "fetch $batchCount from c").rows
                var batchIndex = 0
                for (row in rows) {
                    batchIds[batchIndex++] = row.getLong(0)!!
                    this.id[idx] = row.getLong(0)!!
                    this.createdAt[idx] = row.getLong(1)!!
                    this.createdBy[idx] = row.getInt(2)!!
                    this.product[idx] = row.getLong(3)!!.toInt()
                    this.providerId[idx] = row.getString(4)
                    val updatesText = row.getString(5)!!
                    val aclText = row.getString(6)!!

                    try {
                        val updates = defaultMapper.decodeFromString(
                            ListSerializer(SerializedUpdate.serializer()),
                            updatesText
                        )

                        val arr = CyclicArray<ResourceDocumentUpdate>(ResourceStoreBucket.MAX_UPDATES)
                        for (update in updates) {
                            if (update.createdAt == null) continue
                            arr.add(ResourceDocumentUpdate(update.message, update.extra, update.createdAt))
                        }
                        this.updates[idx] = arr
                    } catch (ex: Throwable) {
                        ResourceStoreBucket.log.warn("Caught an exception while deserializing updates. This should not happen!")
                        ResourceStoreBucket.log.warn(ex.toReadableStacktrace().toString())
                    }
                    try {
                        val aclEntries = defaultMapper.decodeFromString(
                            ListSerializer(NumericAclEntry.serializer()),
                            aclText
                        )

                        val arraySize =
                            if (aclEntries.size == 1 && aclEntries[0].permission == null) 0
                            else ((aclEntries.size / 4) + 1) * 4

                        if (arraySize > 0) {
                            val entities = IntArray(arraySize)
                            val isUser = BooleanArray(arraySize)
                            val permissions = ByteArray(arraySize)

                            for ((index, entry) in aclEntries.withIndex()) {
                                if (entry.permission == null) break
                                entities[index] = entry.gid ?: entry.uid ?: Int.MAX_VALUE
                                isUser[index] = entry.gid == null
                                permissions[index] = entry.permission.toByte()
                            }

                            this.aclEntities[idx] = entities
                            this.aclIsUser[idx] = isUser
                            this.aclPermissions[idx] = permissions
                        }
                    } catch (ex: Throwable) {
                        ResourceStoreBucket.log.warn("Caught an exception while deserializing the ACL. This should not happen!")
                        ResourceStoreBucket.log.warn(ex.toReadableStacktrace().toString())
                    }
                    idx++
                }

                val loadedState = callbacks.loadState(session, batchIndex, batchIds)
                idx -= rows.size
                batchIndex = 0
                for (row in rows) {
                    this.data[idx++] = loadedState[batchIndex++]
                }
                size += rows.size

                if (rows.size < batchCount) break
            }
            if (idx == ResourceStoreBucket.STORE_SIZE) {
                return this.id[ResourceStoreBucket.STORE_SIZE - 1]
            }
        }
        return null
    }

    override suspend fun saveResources(
        transaction: Any,
        bucket: ResourceStoreBucket<T>,
        indices: IntArray,
        len: Int
    ) {
        val session = (transaction as AsyncDBConnection)
        with(bucket) {
            val resourcesToDelete = ArrayList<Long>()
            for (i in 0 until len) {
                val arrIdx = indices[i]
                if (bucket.flaggedForDelete[arrIdx]) resourcesToDelete.add(bucket.id[arrIdx])
            }

            if (resourcesToDelete.size != len) {
                session.sendPreparedStatement(
                    {
                        setParameter("type", type)
                        val id = ArrayList<Long>().also { setParameter("id", it) }
                        val createdAt = ArrayList<Long>().also { setParameter("created_at", it) }
                        val createdBy = ArrayList<Int>().also { setParameter("created_by", it) }
                        val project = ArrayList<Int>().also { setParameter("project", it) }
                        val product = ArrayList<Int>().also { setParameter("product", it) }
                        val providerId = ArrayList<String?>().also { setParameter("provider_id", it) }

                        for (ii in 0 until len) {
                            val arrIdx = indices[ii]
                            if (bucket.flaggedForDelete[arrIdx]) continue
                            id.add(bucket.id[arrIdx])
                            createdAt.add(bucket.createdAt[arrIdx])
                            createdBy.add(bucket.createdBy[arrIdx])
                            project.add(bucket.project[arrIdx])
                            product.add(bucket.product[arrIdx])
                            providerId.add(bucket.providerId[arrIdx])
                        }
                    },
                    """
                        with
                            data as (
                                select
                                    unnest(:id::int8[]) id,
                                    unnest(:created_at::int8[]) created_at,
                                    unnest(:created_by::int[]) created_by,
                                    unnest(:project::int[]) project,
                                    unnest(:product::int[]) product,
                                    unnest(:provider_id::text[]) provider_id
                            )
                        insert into provider.resource (type, id, created_at, created_by, project, product, provider_generated_id, confirmed_by_provider)
                        select :type, d.id, to_timestamp(d.created_at / 1000), u.id, p.id, d.product, d.provider_id, true
                        from
                            data d
                            join auth.principals u on d.created_by = u.uid
                            left join project.projects p on d.project = p.pid
                        on conflict (id) do update set
                            created_at = excluded.created_at,
                            created_by = excluded.created_by,
                            project = excluded.project,
                            product = excluded.product,
                            provider_generated_id = excluded.provider_generated_id
                    """
                )

                session.sendPreparedStatement(
                    {
                        setParameter(
                            "id",
                            (0 until len).flatMap { i ->
                                val arrIdx = indices[i]
                                updates[arrIdx]?.map { id[arrIdx] } ?: emptyList()
                            }
                        )

                        setParameter(
                            "created_at",
                            (0 until len).flatMap { i ->
                                val arrIdx = indices[i]
                                updates[arrIdx]?.map { it.createdAt } ?: emptyList()
                            }
                        )

                        setParameter(
                            "message",
                            (0 until len).flatMap { i ->
                                val arrIdx = indices[i]
                                updates[arrIdx]?.map { it.update } ?: emptyList()
                            }
                        )

                        setParameter(
                            "extra",
                            (0 until len).flatMap { i ->
                                val arrIdx = indices[i]
                                updates[arrIdx]?.map { it.extra } ?: emptyList()
                            }
                        )
                    },
                    """
                        with
                            data as (
                                select
                                    unnest(:id::int8[]) id,
                                    to_timestamp(unnest(:created_at::int8[]) / 1000) created_at,
                                    unnest(:message::text[]) message,
                                    unnest(:extra::jsonb[]) extra
                            ),
                            deleted_updates as (
                                delete from provider.resource_update
                                using data d
                                where resource = d.id
                            )
                        insert into provider.resource_update(resource, created_at, status, extra) 
                        select distinct d.id, d.created_at, d.message, d.extra
                        from data d
                    """
                )

                session.sendPreparedStatement(
                    {
                        val ids = setParameterList<Long>("ids")
                        for (i in 0 until len) {
                            val arrIdx = indices[i]
                            ids.add(id[arrIdx])
                        }
                    },
                    """
                        with all_ids as (
                            select unnest(:ids::int8[]) id
                        )
                        delete from provider.resource_acl_entry
                        using all_ids d
                        where resource_id = d.id
                    """
                )

                session.sendPreparedStatement(
                    {
                        val ids = ArrayList<Long>()
                        val entity = ArrayList<Int>()
                        val entityIsUser = ArrayList<Boolean>()
                        val permission = ArrayList<String>()

                        for (i in 0 until len) {
                            val arrIdx = indices[i]

                            val entities = aclEntities[arrIdx] ?: continue
                            val isUser = aclIsUser[arrIdx] ?: continue
                            val perm = aclPermissions[arrIdx] ?: continue

                            for ((j, e) in entities.withIndex()) {
                                if (e == 0) break

                                ids.add(id[arrIdx])
                                entity.add(e)
                                entityIsUser.add(isUser[j])
                                permission.add(Permission.fromByte(perm[j]).name)
                            }
                        }

                        setParameter("id", ids)
                        setParameter("entity", entity)
                        setParameter("is_user", entityIsUser)
                        setParameter("perm", permission)
                    },
                    """
                        with
                            data as (
                                select
                                    unnest(:id::int8[]) id,
                                    unnest(:entity::int[]) entity,
                                    unnest(:is_user::bool[]) is_user,
                                    unnest(:perm::text[]) perm
                            )
                        insert into provider.resource_acl_entry(group_id, username, permission, resource_id)
                        select distinct g.id, u.id, d.perm, d.id
                        from
                            data d
                            left join auth.principals u on d.is_user and d.entity = u.uid
                            left join project.groups g on not d.is_user and d.entity = g.gid
                                           
                    """
                )
            }

            // The callback will delete its own data and must happen before we delete data from the other tables
            callbacks.saveState(session, this, indices, len)

            if (resourcesToDelete.isNotEmpty()) {
                session.sendPreparedStatement(
                    { setParameter("resource_ids", resourcesToDelete) },
                    "delete from provider.resource_acl_entry where resource_id = some(:resource_ids::bigint[])"
                )

                session.sendPreparedStatement(
                    { setParameter("resource_ids", resourcesToDelete) },
                    "delete from provider.resource_update where resource = some(:resource_ids::bigint[])"
                )

                session.sendPreparedStatement(
                    { setParameter("resource_ids", resourcesToDelete) },
                    "delete from provider.resource where id = some(:resource_ids::bigint[])"
                )
            }
        }
    }

    override suspend fun loadProviderIndex(
        transaction: Any,
        type: String,
        providerId: String,
        register: suspend (uid: Int, pid: Int) -> Unit
    ) {
        val session = (transaction as AsyncDBConnection)
        session.sendPreparedStatement(
            {
                setParameter("type", type)
                setParameter("provider", providerId)
            },
            """
                    select distinct u.uid, project.pid 
                    from
                        provider.resource r
                        join accounting.products p on r.product = p.id
                        join accounting.product_categories pc on p.category = pc.id
                        join auth.principals u on r.created_by = u.id
                        left join project.projects project on r.project = project.id
                    where
                        pc.provider = :provider
                        and r.type = :type
                """
        ).rows.forEach { row ->
            val uid = row.getInt(0)!!
            val pid = row.getInt(1)

            register(uid, pid ?: 0)
        }
    }

    override suspend fun loadIdIndex(
        transaction: Any,
        type: String,
        minimumId: Long,
        maximumIdExclusive: Long,
        register: (id: Long, ref: Int, isUser: Boolean) -> Unit
    ) {
        val session = (transaction as AsyncDBConnection)
        session.sendPreparedStatement(
            {
                setParameter("min", minimumId)
                setParameter("max", maximumIdExclusive)
                setParameter("type", type)
            },
            """
                select r.id, u.uid, p.pid
                from
                    provider.resource r
                    join auth.principals u on r.created_by = u.id
                    left join project.projects p on r.project = p.id
                where
                    r.id >= :min
                    and r.id < :max
                    and r.type = :type
            """
        ).rows.forEach { row ->
            val uid = row.getInt(1)!!
            val pid = row.getInt(2)
            register(row.getLong(0)!!, pid ?: uid, pid == null)
        }
    }

    override suspend fun loadMaximumId(transaction: Any): Long {
        val session = (transaction as AsyncDBConnection)
        return session.sendPreparedStatement(
            {},
            """
                select max(id)
                from provider.resource
            """
        ).rows.singleOrNull()?.getLong(0) ?: 0L
    }

    companion object : Loggable {
        override val log = logger()
    }
}
