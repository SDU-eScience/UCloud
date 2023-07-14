package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.SortDirection
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.ResourceDocument
import dk.sdu.cloud.accounting.util.ResourceDocumentUpdate
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobsProvider
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.provider.api.ResourceIncludeFlags
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.DBTransaction
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
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
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLong
import kotlin.Comparator
import kotlin.collections.HashSet
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class ResourceStore<T>(
    val type: String,
    private val db: DBContext,
    private val productCache: ProductCache,
    private val idCardService: IdCardService,
    private val callbacks: Callbacks<T>,
) {
    data class BrowseResult(val count: Int, val next: String?)

    interface Callbacks<T> {
        suspend fun loadState(session: DBTransaction, count: Int, resources: LongArray): Array<T>
        suspend fun saveState(session: DBTransaction, store: ResourceStoreBucket<T>, indices: IntArray, length: Int)
    }

    // Blocks and stores
    // =================================================================================================================
    // The following calls deal with the blocks and the stores inside of them. This is where we store all the data
    // relevant to this ResourceService. The main entrypoint to the data is a linked-list of blocks. These blocks
    // contain references to "stores". Inside a store, you will are guaranteed to find _all_ of the resources owned by
    // a single workspace. A store is by itself a linked-list structure, where each store contain a chunk of resources.
    //
    // The block data-structure is defined below:
    private data class Block<T>(
        // NOTE(Dan): We only store the root stores in a block. Subsequent stores are accessed through the
        // store's next property.
        val stores: Array<ResourceStoreBucket<T>?> = arrayOfNulls(4096),

        // TODO(Dan): This might need to be @Volatile
        var next: Block<T>? = null,
    )

    // Whenever we need to make mutations to it, such as inserting a new store, we use the `blockMutationMutex`. We
    // do not need to use the `blockMutationMutex` when we are just reading the data.
    private val blockMutationMutex = Mutex()

    // We store a reference to the head of the blockchain (sorry) in the `root` variable:
    private val root: Block<T> = Block()

    // Stores are always owned by a workspace. We identify a workspace by a tuple containing the user ID (`uid`) and
    // the project ID (`pid`). We only use the numeric IDs for this, since it improves performance inside the stores by
    // quite a bit. The translation from textual IDs to numeric IDs translation is done by the IdCardService. If the
    // `pid` is non-zero then the `uid` will always be ignored. This allows the callers to simply pass in the
    // information an ActorAndProject easily.

    // To use the store of a workspace, you will need to call `findOrLoadStore`:
    private suspend fun findOrLoadStore(uid: Int, pid: Int): ResourceStoreBucket<T> {
        require(uid == 0 || pid == 0)
        return findStore(uid, pid) ?: loadStore(uid, pid)
    }

    // Since the stores are linked-list like structure, you should make sure to iterate through all the stores. You need
    // to do this using the `useStores` function. Note that you can terminate the search early once you have performed
    // the request you need to do:
    private inline fun useStores(
        root: ResourceStoreBucket<T>,
        reverseOrder: Boolean = false,
        consumer: (ResourceStoreBucket<T>) -> ShouldContinue
    ) {
        if (!reverseOrder) {
            var current: ResourceStoreBucket<T>? = root
            while (current != null) {
                if (consumer(current) == ShouldContinue.NO) break
                current = current.next
            }
        } else {
            var current: ResourceStoreBucket<T>? = root.findTail()
            while (current != null) {
                if (consumer(current) == ShouldContinue.NO) break
                current = current.previous
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

    // In order to implement `findOrLoadStore` we must of course implement the two subcomponents. Finding a loaded store
    // is relatively straight-forward, we simply iterate through the blocks until we find it:
    private fun findStore(uid: Int, pid: Int): ResourceStoreBucket<T>? {
        require(uid == 0 || pid == 0)

        var block: Block<T>? = root
        while (block != null) {
            for (store in block.stores) {
                if (store == null) break
                if (uid != 0 && store.uid == uid) return store
                if (pid != 0 && store.pid == pid) return store
            }
            block = block.next
        }

        return null
    }

    // Loading the store is also fairly straightforward. We need to use the `blockMutationMutex` since we will be
    // mutating the block list. The function will quickly reserve a spot in the block list and then trigger a load()
    // inside the store. Note that the store itself is capable of handling the situation where it receives requests
    // even though it hasn't finished loading.
    private suspend fun loadStore(uid: Int, pid: Int): ResourceStoreBucket<T> {
        require(uid == 0 || pid == 0)

        val result = blockMutationMutex.withLock {
            // NOTE(Dan): We need to make sure that someone else didn't get here before we did.
            val existingBlock = findStore(uid, pid)
            if (existingBlock != null) return existingBlock

            var block = root
            while (block.next != null) {
                block = block.next!!
            }

            var emptySlotIdx = block.stores.indexOf(null)
            if (emptySlotIdx == -1) {
                val oldTail = block
                block = Block()
                oldTail.next = block
                emptySlotIdx = 0
            }

            val store = ResourceStoreBucket<T>(type, uid, pid, db, callbacks)
            block.stores[emptySlotIdx] = store
            store
        }

        result.load()
        return result
    }

    // The blocks and stores are consumed by the public API.

    // Public API
    // =================================================================================================================
    // This is where we actually make stuff happen! These functions are consumed by the services of the different
    // resource types. Almost all the functions in this section will follow the same pattern of:
    //
    // 1. Locate (and potentially initialize) the appropriate stores which contain the resources. This will often use
    //    one of the indices we have available to us (see the following sections).
    // 2. Iterate through all the relevant stores (via `useStores`) and perform the relevant operation.
    // 3. If relevant, update one or more indices.
    // 4. Aggregate and filter the results. Finally return it to the caller.
    //
    // In other words, you won't find a lot of business logic in these calls, since this is mostly implemented in the
    // individual stores.

    fun startSynchronizationJob(
        scope: CoroutineScope,
    ): Job {
        Runtime.getRuntime().addShutdownHook(Thread {
            runBlocking { doCompleteSync() }
        })

        return scope.launch {
            while (coroutineContext.isActive) {
                try {
                    val start = Time.now()
                    doCompleteSync()
                    val end = Time.now()

                    delay(max(1000, 10_000 - (end - start)))
                } catch (ex: Throwable) {
                    log.warn(ex.toReadableStacktrace().toString())
                }
            }
        }
    }

    private suspend fun doCompleteSync() {
        db.withSession { session ->
            var block = root
            while (true) {
                for (entry in block.stores) {
                    if (entry == null) continue
                    useStores(entry) {
                        it.synchronizeToDatabase(session)
                        ShouldContinue.YES
                    }
                }
                block = block.next ?: break
            }
        }
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

        val productId = productCache.referenceToProductId(product)
            ?: throw RPCException("Invalid product supplied", HttpStatusCode.BadRequest)

        val uid = if (idCard.activeProject <= 0) idCard.uid else 0
        val pid = if (uid == 0) idCard.activeProject else 0

        return createDocument(uid, pid, productId, data, null, output)
    }

    suspend fun createViaProvider(
        idCard: IdCard,
        product: ProductReference,
        data: T,
        proxyBlock: suspend (doc: ResourceDocument<T>) -> String?,
    ): Long {
        val doc = ResourceDocument<T>()
        val allocatedId = create(idCard, product, data, output = doc)

        val providerId = try {
            proxyBlock(doc)
        } catch (ex: Throwable) {
            JobResourceService2.log.warn(ex.toReadableStacktrace().toString())
            // TODO(Dan): This is not guaranteed to run ever. We will get stuck if never "confirmed" by the provider.
            delete(idCard, longArrayOf(allocatedId))
            throw ex
        }

        if (providerId != null) {
            updateProviderId(idCard, allocatedId, providerId)
        }

        return allocatedId
    }

    suspend fun register(
        idCard: IdCard,
        product: ProductReference,
        uid: Int,
        pid: Int,
        data: T,
        providerId: String?,
        output: ResourceDocument<T>?,
    ): Long {
        if (idCard !is IdCard.Provider) {
            throw RPCException("Only providers can use this endpoint", HttpStatusCode.Forbidden)
        }

        val productId = productCache.referenceToProductId(product)
            ?: throw RPCException("Invalid product supplied", HttpStatusCode.Forbidden)

        if (productId !in idCard.providerOf) {
            throw RPCException("Invalid product supplied", HttpStatusCode.Forbidden)
        }

        return createDocument(uid, pid, productId, data, providerId, output)
    }

    private suspend fun createDocument(
        uid: Int,
        pid: Int,
        productId: Int,
        data: T,
        providerGeneratedId: String?,
        output: ResourceDocument<T>?
    ): Long {
        val root = findOrLoadStore(if (pid == 0) uid else 0, pid)

        var tail = root.findTail()
        while (true) {
            val id = tail.create(uid, productId, data, providerGeneratedId, output)
            if (id < 0L) {
                tail = tail.expand()
            } else {
                val index = findIdIndexOrLoad(id) ?: error("Index was never initialized. findIdIndex is buggy? $id")
                index.register(id, if (pid != 0) pid else uid, pid == 0)

                val providerId =
                    productCache.productIdToReference(productId)?.provider ?: error("Unknown product?")
                findOrLoadProviderIndex(providerId).registerUsage(uid, pid)

                return id
            }
        }
    }

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
        val sortDirection = sortDirection ?: SortDirection.ascending

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
            is IdCard.Provider -> {
                val storeArray = IntArray(128)
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
                    val resultCount = providerIndex.findUidStores(storeArray, minimumUid)
                    for (i in 0..<resultCount) {
                        if (offset >= outputBufferLimit) break
                        minimumUid = storeArray[i]

                        useStores(findOrLoadStore(storeArray[i], 0)) { store ->
                            offset += store.search(idCard, outputBuffer, offset, initialId, predicate = filter)
                            ShouldContinue.ifThisIsTrue(offset < outputBuffer.size)
                        }

                        initialId = -1L
                    }

                    if (resultCount < storeArray.size) {
                        if (offset < outputBufferLimit) didCompleteUsers = true
                        break
                    }
                }

                while (minimumPid < Int.MAX_VALUE && offset < outputBufferLimit) {
                    val resultCount = providerIndex.findPidStores(storeArray, minimumUid)
                    for (i in 0..<resultCount) {
                        if (offset >= outputBufferLimit) break
                        useStores(findOrLoadStore(0, storeArray[i])) { store ->
                            offset += store.search(idCard, outputBuffer, offset, initialId, predicate = filter)
                            initialId = -1L
                            ShouldContinue.ifThisIsTrue(offset < outputBuffer.size)
                        }
                    }

                    if (resultCount < storeArray.size) {
                        if (offset < outputBufferLimit) didCompleteProjects = true
                        break
                    }
                    minimumPid = storeArray[resultCount - 1]
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
                useStores(findOrLoadStore(uid, pid), reverseOrder = reverseOrder) { store ->
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

        val rootStore = findOrLoadStore(uid, pid)

        // NOTE(Dan): Sorting is quite a bit more expensive than normal browsing since we cannot use a stable key which
        // allows us to easily find the relevant dataset. Instead, we must always fetch _all_ resources, sort and then
        // jump to the relevant part. This also means that there are no guarantees about the stability of the results.
        //
        // Because of this, we put a hard limit on the number of documents we want to traverse through. This is
        // currently set very arbitrarily below. We may need to tweak the limit since memory usage might simply be too
        // big at this value. If there are too many resources, then we fall back to the normal browse endpoint,
        // producing results sorted by ID.
        var storeCount = 0
        useStores(rootStore) { store ->
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

        useStores(rootStore) { store ->
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

    suspend fun modify(
        idCard: IdCard,
        output: Array<ResourceDocument<T>>,
        ids: LongArray,
        permission: Permission,
        consumer: (arrIndex: Int, doc: ResourceDocument<T>) -> Unit,
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

            useStores(initialStore) { currentStore ->
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
        consumer: ((state: T, updateIdx: Int, update: ResourceDocumentUpdate) -> Boolean)? = null,
    ) {
        if (idCard !is IdCard.Provider) {
            throw RPCException("End-users are not allowed to add updates to a resource!", HttpStatusCode.Forbidden)
        }

        useStores(findStoreByResourceId(id) ?: return) { store ->
            ShouldContinue.ifThisIsTrue(!store.addUpdates(idCard, id, updates, consumer))
        }
    }

    suspend fun delete(idCard: IdCard, ids: LongArray) {
        TODO("Not yet implemented")
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

            useStores(initialStore) { currentStore ->
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
        useStores(findStoreByResourceId(id) ?: return) { store ->
            ShouldContinue.ifThisIsTrue(!store.updateProviderId(idCard, id, providerId))
        }
    }

    suspend fun updateAcl(
        idCard: IdCard,
        id: Long,
        deletions: List<NumericAclEntry>,
        additions: List<NumericAclEntry>
    ) {
        if (!additions.all { it.permission?.canBeGranted == true }) {
            throw RPCException("Invalid request supplied", HttpStatusCode.BadRequest)
        }

        useStores(findStoreByResourceId(id) ?: return) { store ->
            ShouldContinue.ifThisIsTrue(!store.updateAcl(idCard, id, deletions, additions))
        }
    }

    // ID index
    // =================================================================================================================
    // NOTE(Dan): The IdIndex contains references to all resources that we have loaded by their ID. Indexes we have
    // stored must always be up-to-date with the real state, meaning that we do not store partial blocks but only
    // complete blocks. References stored within a block are allowed to point to invalid data and/or unloaded data.
    private val idIndex = runBlocking { initializeIdIndex(0L) }

    private class IdIndex(val minimumId: Long) {
        @Volatile
        var next: IdIndex? = null

        val entries = AtomicIntegerArray(BLOCK_SIZE)

        // NOTE(Dan): There is no AtomicBooleanArray, so we encode it in an integer array instead. 1 = true, 0 = false.
        val entryIsUid = AtomicIntegerArray(BLOCK_SIZE)

        fun register(id: Long, reference: Int, isUid: Boolean) {
            val slot = (id - minimumId).toInt()
            require(slot in 0 until BLOCK_SIZE) {
                "id is out of bounds for this block: minimum = $minimumId, id = $id"
            }

            entries[slot] = reference
            entryIsUid[slot] = if (isUid) 1 else 0
        }

        fun clear(id: Long) {
            val slot = (id - minimumId).toInt()
            require(slot in 0 until BLOCK_SIZE) {
                "id is out of bounds for this block: minimum = $minimumId, id = $id"
            }

            entries[slot] = 0
            entryIsUid[slot] = 0
        }

        companion object {
            const val BLOCK_SIZE = 4096
        }
    }

    private suspend fun initializeIdIndex(baseId: Long): IdIndex {
        val result = IdIndex(baseId)

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("min", baseId)
                    setParameter("max", baseId + IdIndex.BLOCK_SIZE)
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
                result.register(row.getLong(0)!!, pid ?: uid, pid == null)
            }
        }

        return result
    }

    private val idIndexCreationMutex = Mutex()
    private suspend fun loadIdIndexes(vararg ids: Long) {
        val blocksToLoad = LongArray(ids.size)
        for ((index, id) in ids.withIndex()) {
            blocksToLoad[index] = (id / IdIndex.BLOCK_SIZE) * IdIndex.BLOCK_SIZE
        }

        val maxValid = ResourceIdAllocator.maximumValid(db)
        blocksToLoad.sort()
        var prev = -1L
        for (block in blocksToLoad) {
            if (block == prev) continue
            prev = block

            // NOTE(Dan): This check is required to avoid a DOS attack where a malicious user requests random
            // IDs to force allocations of empty indices.
            if (block > maxValid) continue

            val loadedIndex = initializeIdIndex(block)
            idIndexCreationMutex.withLock {
                val queryResult = findIdIndex(block)
                if (queryResult.result == null) {
                    val currentNext = queryResult.previous.next
                    loadedIndex.next = currentNext
                    queryResult.previous.next = loadedIndex
                }
            }
        }
    }

    private data class IdIndexFindResult(val previous: IdIndex, val result: IdIndex?)

    private fun findIdIndex(id: Long): IdIndexFindResult {
        val query = (id / IdIndex.BLOCK_SIZE) * IdIndex.BLOCK_SIZE

        var previous: IdIndex? = null
        var element: IdIndex? = idIndex
        while (element != null) {
            if (element.minimumId == query) {
                return IdIndexFindResult(previous ?: idIndex, element)
            } else if (element.minimumId > query) {
                return IdIndexFindResult(previous ?: idIndex, null)
            }

            previous = element
            element = element.next
        }
        return IdIndexFindResult(previous ?: idIndex, null)
    }

    private suspend fun findIdIndexOrLoad(id: Long): IdIndex? {
        var queryResult = findIdIndex(id).result
        if (queryResult == null) {
            loadIdIndexes(id)
            queryResult = findIdIndex(id).result
        }
        return queryResult
    }

    private suspend fun findStoreByResourceId(id: Long): ResourceStoreBucket<T>? {
        val loaded = findIdIndexOrLoad(id) ?: return null
        val slot = (id - loaded.minimumId).toInt()
        val reference = loaded.entries[slot]
        val referenceIsUid = loaded.entryIsUid[slot] == 1

        return findOrLoadStore(if (referenceIsUid) reference else 0, if (referenceIsUid) 0 else reference)
    }

    // Provider index
    // =================================================================================================================
    // NOTE(Dan): This index contains references to all resources that are owned by a provider. It becomes automatically
    // populated when a provider contacts us about any resource/when a user requests a resource from them.
    private val providerIndex = arrayOfNulls<ProviderIndex>(MAX_PROVIDERS)
    private val providerIndexMutex = Mutex() // NOTE(Dan): Only needed to modify the list of indices

    private class ProviderIndex(val provider: String) {
        private val uidReferences = TreeSet<Int>()
        private val pidReferences = TreeSet<Int>()
        private val mutex = ReadWriterMutex()

        suspend fun registerUsage(uid: Int, pid: Int) {
            mutex.withWriter {
                if (pid != 0) {
                    pidReferences.add(pid)
                } else {
                    uidReferences.add(uid)
                }
            }
        }

        suspend fun findUidStores(output: IntArray, minimumUid: Int): Int {
            mutex.withReader {
                var ptr = 0
                for (entry in uidReferences.tailSet(minimumUid, true)) {
                    output[ptr++] = entry
                    if (ptr >= output.size) break
                }
                return ptr
            }
        }

        suspend fun findPidStores(output: IntArray, minimumPid: Int): Int {
            mutex.withReader {
                var ptr = 0
                for (entry in pidReferences.tailSet(minimumPid, true)) {
                    output[ptr++] = entry
                    if (ptr >= output.size) break
                }
                return ptr
            }
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
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("type", type)
                        setParameter("provider", provider)
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

                    result.registerUsage(uid, pid ?: 0)
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
    private val db: DBContext,
    private val callbacks: ResourceStore.Callbacks<T>,
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

            val newStore = ResourceStoreBucket<T>(type, uid, pid, db, callbacks)
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
    private val _data = arrayOfNulls<Any>(STORE_SIZE)
    fun data(idx: Int): T? {
        @Suppress("UNCHECKED_CAST")
        return _data[idx] as T?
    }

    // We store the number of valid entries in the `size` property. Each time a new entry is created, we increment
    // size by one.
    private var size: Int = 0

    // Each bucket keeps track of which entries have been changed since the last synchronization to the database. The
    // `dirtyFlag` array tracks this on a resource level. The `anyDirty` flag can be used to quickly determine if any
    // `dirtyFlag[x] = true`.
    private val dirtyFlag = BooleanArray(STORE_SIZE)
    private var anyDirty = false

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
            if (ready.get()) break
            delay(1)
        }
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
            var idx = 0
            db.withSession { session ->
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
                                limit $STORE_SIZE
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
                while (idx < STORE_SIZE) {
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

                            val arr = CyclicArray<ResourceDocumentUpdate>(MAX_UPDATES)
                            for (update in updates) {
                                if (update.createdAt == null) continue
                                arr.add(ResourceDocumentUpdate(update.message, update.extra, update.createdAt))
                            }
                            this.updates[idx] = arr
                        } catch (ex: Throwable) {
                            log.warn("Caught an exception while deserializing updates. This should not happen!")
                            log.warn(ex.toReadableStacktrace().toString())
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
                            log.warn("Caught an exception while deserializing the ACL. This should not happen!")
                            log.warn(ex.toReadableStacktrace().toString())
                        }
                        idx++
                    }

                    val loadedState = callbacks.loadState(session, batchIndex, batchIds)
                    idx -= rows.size
                    batchIndex = 0
                    for (row in rows) {
                        this._data[idx++] = loadedState[batchIndex++]
                    }
                    size += rows.size

                    if (rows.size < batchCount) break
                }
            }

            if (idx == STORE_SIZE) {
                expand(loadRequired = true, hasLock = true).load(this.id[STORE_SIZE - 1])
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
            val id = ResourceIdAllocator.allocate(db)

            this.id[idx] = id
            this.createdAt[idx] = System.currentTimeMillis()
            this.createdBy[idx] = createdByUid
            this.project[idx] = pid
            this.product[idx] = product
            this._data[idx] = data
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

            val isOwnerOfEverything = idCard is IdCard.User &&
                    ((uid != 0 && idCard.uid == uid) || (pid != 0 && idCard.adminOf.contains(pid)))

            if (isOwnerOfEverything) {
                val requiresOwnerPrivileges = permissionRequired == Permission.READ ||
                        permissionRequired == Permission.EDIT ||
                        permissionRequired == Permission.ADMIN

                if (requiresOwnerPrivileges) {
                    if (reverseOrder) {
                        var idx = startIndex
                        while (idx >= 0 && idx % 4 != 0 && !consumer.shouldTerminate()) {
                            consumer.call(idx--)
                        }

                        while (idx >= 4 && !consumer.shouldTerminate()) {
                            consumer.call(idx)
                            consumer.call(idx - 1)
                            consumer.call(idx - 2)
                            consumer.call(idx - 3)

                            idx -= 4
                        }

                        while (idx >= 0 && !consumer.shouldTerminate()) {
                            consumer.call(idx--)
                        }
                    } else {
                        var idx = startIndex

                        while (idx < STORE_SIZE && idx % 4 != 0 && !consumer.shouldTerminate()) {
                            if (this.id[idx] == 0L) break
                            consumer.call(idx++)
                        }

                        while (idx < STORE_SIZE - 3 && !consumer.shouldTerminate()) {
                            if (this.id[idx] == 0L) break
                            consumer.call(idx)
                            consumer.call(idx + 1)
                            consumer.call(idx + 2)
                            consumer.call(idx + 3)

                            idx += 4
                        }

                        while (idx < STORE_SIZE && !consumer.shouldTerminate()) {
                            if (this.id[idx] == 0L) break
                            consumer.call(idx++)
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
                while (i >= 0 && i < STORE_SIZE && !consumer.shouldTerminate()) {
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
                        consumer.call(i)
                    } else {
                        // If no groups match, try uid
                        entityConsumer.match = -1
                        entityConsumer.shouldBeUser = true
                        searchInArray(userNeedle, entities, entityConsumer)
                        if (entityConsumer.match != -1) consumer.call(i)
                    }

                    i += delta
                }
            } else if (idCard is IdCard.Provider && idCard.providerOf.isNotEmpty()) {
                val requiresProviderPrivileges =
                    permissionRequired == Permission.READ ||
                            permissionRequired == Permission.EDIT ||
                            permissionRequired == Permission.PROVIDER

                if (requiresProviderPrivileges) {
                    searchInArray(idCard.providerOf, product, consumer, startIndex)
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

                @Suppress("UNCHECKED_CAST")
                res.data = self._data[arrIdx] as T

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
        run {
            Arrays.fill(res.update, null)
            val resourceUpdates = this.updates[arrIdx] ?: return@run
            for ((index, update) in resourceUpdates.withIndex()) {
                if (index >= res.update.size) break
                res.update[index] = update
            }
        }
        run {
            Arrays.fill(res.acl, null)
            val entities = this.aclEntities[arrIdx]
            val isUser = this.aclIsUser[arrIdx]
            val perms = this.aclPermissions[arrIdx]

            if (entities != null && isUser != null && perms != null) {
                for (i in entities.indices) {
                    if (entities[i] == 0) break
                    res.acl[i] =
                        ResourceDocument.AclEntry(entities[i], isUser[i], Permission.fromByte(perms[i]))
                }
            }
        }
    }

    suspend fun addUpdates(
        idCard: IdCard,
        id: Long,
        newUpdates: List<ResourceDocumentUpdate>,
        consumer: ((state: T, updateIdx: Int, update: ResourceDocumentUpdate) -> Boolean)? = null,
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
                        if (consumer != null) consumer(self._data[arrIdx] as T, index, update) else true
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
        consumer: (arrIndex: Int, doc: ResourceDocument<T>) -> Unit,
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
                        if (self.id[arrIdx] >= startIdExclusive) return
                    }

                    val res = outputBuffer[outIdx++]
                    loadIntoDocument(res, arrIdx)

                    @Suppress("UNCHECKED_CAST")
                    res.data = self._data[arrIdx] as T

                    if (!predicate.filter(res)) {
                        outIdx--
                    } else {
                        consumer(arrIdx, res)

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


    // Saving data to the database
    // =================================================================================================================
    // Data is periodically pushed from the cache to the database through `synchronizeToDatabase`. This function is
    // called by the ResourceStore in a background task. The first stage attempts to determine which resources from
    // the bucket needs to be synchronized. The second stage, which takes place in `sync` actually writes the data to
    // the database. This will also invoke the callbacks to save the user data.
    suspend fun synchronizeToDatabase(session: DBTransaction) {
        if (!anyDirty) return

        mutex.withLock {
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
                    sync(session, indicesToSync, ptr)
                    ptr = 0
                }
            }

            sync(session, indicesToSync, ptr)

            for (i in 0 until STORE_SIZE) {
                dirtyFlag[i] = false
            }

            anyDirty = false
        }
    }

    private suspend fun sync(session: DBTransaction, indices: IntArray, len: Int) {
        if (len <= 0) return

        session.sendPreparedStatement(
            {
                setParameter("type", type)
                setParameter("id", (0 until len).map { id[indices[it]] })
                setParameter("created_at", (0 until len).map { createdAt[indices[it]] })
                setParameter("created_by", (0 until len).map { createdBy[indices[it]] })
                setParameter("project", (0 until len).map { project[indices[it]] })
                setParameter("product", (0 until len).map { product[indices[it]] })
                setParameter("provider_id", (0 until len).map { providerId[indices[it]] })
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
                    ),
                    deleted_entries as (
                        delete from provider.resource_acl_entry
                        using data d
                        where resource_id = d.id
                        returning resource_id
                    )
                insert into provider.resource_acl_entry(group_id, username, permission, resource_id)
                select distinct g.id, u.id, d.perm, d.id
                from
                    data d join
                    deleted_entries de on d.id = de.resource_id
                    left join auth.principals u on d.is_user and d.entity = u.uid
                    left join project.groups g on not d.is_user and d.entity = g.gid
                                   
            """
        )

        callbacks.saveState(session, this, indices, len)
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
        private fun searchInArray(
            needles: IntArray,
            haystack: IntArray,
            emit: SearchConsumer,
            startIndex: Int = 0,
        ) {
            require(haystack.size % 4 == 0) { "haystack must have a size which is a multiple of 4! (${haystack.size})" }

            if (haystack.size < ivLength * 4) {
                for ((index, hay) in haystack.withIndex()) {
                    if (index < startIndex) continue

                    for (needle in needles) {
                        if (hay == needle) {
                            emit.call(index)
                            break
                        }
                    }
                }
            } else {
                var idx = (startIndex / (ivLength * 4)) * (ivLength * 4)
                while (idx < haystack.size) {
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

    suspend fun maximumValid(ctx: DBContext): Long {
        val currentId = idAllocator.get()
        if (currentId < 0L) {
            init(ctx)
            return maximumValid(ctx)
        }
        return currentId
    }

    suspend fun allocate(ctx: DBContext): Long {
        val allocatedId = idAllocator.incrementAndGet()
        if (allocatedId < 0L) {
            init(ctx)
            return allocate(ctx)
        }
        return allocatedId
    }

    private suspend fun init(ctx: DBContext) {
        initMutex.withLock {
            if (idAllocator.get() >= 0L) return

            ctx.withSession { session ->
                idAllocator.set(
                    session.sendPreparedStatement(
                        {},
                        """
                            select max(id)
                            from provider.resource
                        """
                    ).rows.singleOrNull()?.getLong(0) ?: 0L
                )
            }
        }
    }
}

interface FilterFunction<T> {
    fun filter(doc: ResourceDocument<T>): Boolean
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
        suspend fun fromAclEntry(cards: IdCardService, entry: ResourceAclEntry): List<NumericAclEntry> {
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

        suspend fun fromAclEntity(cards: IdCardService, entity: AclEntity): NumericAclEntry {
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

private fun Permission.Companion.fromByte(value: Byte): Permission {
    return when (value.toInt()) {
        1 -> Permission.READ
        2 -> Permission.EDIT
        3 -> Permission.ADMIN
        4 -> Permission.PROVIDER
        else -> error("unknown value: $value")
    }
}

private fun Permission.toByte(): Byte {
    return when (this) {
        Permission.READ -> 1
        Permission.EDIT -> 2
        Permission.ADMIN -> 3
        Permission.PROVIDER -> 4
    }
}
