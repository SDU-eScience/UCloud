package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.IdCardService
import dk.sdu.cloud.accounting.util.ResourceDocument
import dk.sdu.cloud.accounting.util.ResourceStore
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.provider.api.ResourceIncludeFlags
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.DBTransaction
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import jdk.incubator.vector.IntVector
import jdk.incubator.vector.VectorOperators
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class ResourceStoreByOwner<T>(
    val type: String,
    val uid: Int,
    val pid: Int,
    private val db: DBContext,
    private val callbacks: ResourceManagerByOwner.Callbacks<T>,
) {
    private val mutex = Mutex()

    private val id = LongArray(STORE_SIZE)
    private val createdAt = LongArray(STORE_SIZE)
    private val createdBy = IntArray(STORE_SIZE)
    private val project = IntArray(STORE_SIZE)
    private val product = IntArray(STORE_SIZE)
    private val data = arrayOfNulls<Any>(STORE_SIZE)
    private val providerId = arrayOfNulls<String>(STORE_SIZE)
    private val aclEntity = arrayOfNulls<IntArray>(STORE_SIZE)
    private val aclPermission = arrayOfNulls<ByteArray>(STORE_SIZE)

    private var size: Int = 0

    var next: ResourceStoreByOwner<T>? = null
        private set

    private val ready = AtomicBoolean(false)

    suspend fun load(minimumId: Long = 0) {
        mutex.withLock {
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
                        select
                            resource.id,
                            floor(extract(epoch from resource.created_at) * 1000)::int8,
                            p.uid,
                            resource.product,
                            resource.provider_generated_id
                        from
                            provider.resource
                            join auth.principals p on resource.created_by = p.id
                        where
                            resource.type = :type
                            and resource.id > :minimum
                            and (
                                (:project_id::text is not null and resource.project = :project_id)
                                or (:username::text is not null and resource.created_by = :username)
                            );
                    """
                )

                val batchCount = 128
                var idx = 0
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
                        idx++
                    }

                    val loadedState = callbacks.loadState(session, batchIndex, batchIds)
                    idx -= rows.size
                    batchIndex = 0
                    for (row in rows) {
                        this.data[idx++] = loadedState[batchIndex++]
                    }

                    if (rows.size < batchCount) break
                }

                if (idx == STORE_SIZE) {
                    expand(loadRequired = true, hasLock = true).load(this.id[STORE_SIZE - 1])
                }
            }

            ready.set(true)
        }
    }

    private suspend fun awaitReady() {
        while (true) {
            if (ready.get()) break
            delay(1)
        }
    }

    suspend fun create(
        idCard: IdCard.User,
        product: Int,
        data: T,
        output: ResourceDocument<T>?,
    ): Long {
        awaitReady()

        mutex.withLock {
            if (size >= STORE_SIZE - 1) return -1

            val idx = size++
            val id = ResourceIdAllocator.allocate(db)

            this.id[idx] = id
            this.createdAt[idx] = System.currentTimeMillis()
            this.createdBy[idx] = idCard.uid
            this.project[idx] = idCard.activeProject
            this.product[idx] = product
            this.data[idx] = data

            if (output != null) {
                output.id = id
                output.createdAt = this.createdAt[idx]
                output.createdBy = idCard.uid
                output.project = idCard.activeProject
                output.product = product
                output.data = data
            }
            return id
        }
    }

    suspend fun search(
        idCard: IdCard,
        outputBuffer: Array<ResourceDocument<T>>,
        outputBufferOffset: Int,
        predicate: (ResourceDocument<T>) -> Boolean
    ): Int {
        awaitReady()

        mutex.withLock {
            var outIdx = outputBufferOffset

            fun emit(arrIdx: Int) {
                if (outIdx >= outputBuffer.size) return
                if (this.id[arrIdx] == 0L) return

                val res = outputBuffer[outIdx++]
                res.id = this.id[arrIdx]
                res.createdAt = this.createdAt[arrIdx]
                res.createdBy = this.createdBy[arrIdx]
                res.project = this.project[arrIdx]
                res.product = this.product[arrIdx]
                res.providerId = this.providerId[arrIdx]
                @Suppress("UNCHECKED_CAST")
                res.data = this.data[arrIdx] as T

                if (!predicate(res)) {
                    outIdx--
                }
            }

            val isOwnerOfEverything = idCard is IdCard.User &&
                    ((uid != 0 && idCard.uid == uid) || (pid != 0 && idCard.adminOf.contains(pid)))

            if (isOwnerOfEverything) {
                var idx = 0
                while (idx < STORE_SIZE && outIdx < outputBuffer.size) {
                    if (this.id[idx] == 0L) break
                    emit(idx)
                    emit(idx + 1)
                    emit(idx + 2)
                    emit(idx + 3)

                    idx += 4
                }
            } else if (idCard is IdCard.User && idCard.activeProject != 0) {
                // TODO(Dan): This branch needs to use the ACL
            } else if (idCard is IdCard.Provider && idCard.providerOf.isNotEmpty()) {
                searchInArray(idCard.providerOf, product, ::emit)
            }
            return outIdx
        }
    }

    private fun searchInArray(needles: IntArray, haystack: IntArray, emit: (Int) -> Unit) {
        val resultArray = BooleanArray(ivLength)

        var idx = 0
        while (idx < STORE_SIZE) {
            for (pIdx in 0 until needles.size) {
                val needle = IntVector.broadcast(ivSpecies, needles[pIdx])

                val haystack1 = IntVector.fromArray(ivSpecies, haystack, idx)
                val haystack2 = IntVector.fromArray(ivSpecies, haystack, idx + (ivLength))
                val haystack3 = IntVector.fromArray(ivSpecies, haystack, idx + (ivLength * 2))
                val haystack4 = IntVector.fromArray(ivSpecies, haystack, idx + (ivLength * 3))

                val cr1 = haystack1.compare(VectorOperators.EQ, needle)
                val cr2 = haystack2.compare(VectorOperators.EQ, needle)
                val cr3 = haystack3.compare(VectorOperators.EQ, needle)
                val cr4 = haystack4.compare(VectorOperators.EQ, needle)

                val t1 = cr1.firstTrue()
                val t2 = cr1.firstTrue()
                val t3 = cr1.firstTrue()
                val t4 = cr1.firstTrue()

                if (t1 != ivLength) {
                    cr1.intoArray(resultArray, 0)
                    for (i in t1 until resultArray.size) {
                        if (resultArray[i]) emit(idx + 0 + i)
                    }
                }
                if (t2 != ivLength) {
                    cr2.intoArray(resultArray, 0)
                    for (i in t2 until resultArray.size) {
                        if (resultArray[i]) emit(idx + (ivLength) + i)
                    }
                }
                if (t3 != ivLength) {
                    cr3.intoArray(resultArray, 0)
                    for (i in t3 until resultArray.size) {
                        if (resultArray[i]) emit(idx + (ivLength * 2) + i)
                    }
                }
                if (t4 != ivLength) {
                    cr4.intoArray(resultArray, 0)
                    for (i in t4 until resultArray.size) {
                        if (resultArray[i]) emit(idx + (ivLength * 3) + i)
                    }
                }
            }
            idx += ivLength * 4
        }
    }

    suspend fun findTail(): ResourceStoreByOwner<T> {
        mutex.withLock {
            if (next == null) return this
        }
        return next!!.findTail()
    }

    suspend fun expand(loadRequired: Boolean = false, hasLock: Boolean = false): ResourceStoreByOwner<T> {
        val currentNext = next
        if (currentNext != null) return currentNext

        if (!hasLock) mutex.lock()
        try {
            val nextAfterMutex = next
            if (nextAfterMutex != null) return nextAfterMutex

            val newStore = ResourceStoreByOwner<T>(type, uid, pid, db, callbacks)
            next = newStore
            if (!loadRequired) {
                newStore.ready.set(true)
            }
            return newStore
        } finally {
            if (!hasLock) mutex.unlock()
        }
    }

    companion object {
        // NOTE(Dan): 99% of all workspaces have less than ~1300 entries, 1024 seem like a good target.
        const val STORE_SIZE = 1024

        private val ivSpecies = IntVector.SPECIES_PREFERRED
        private val ivLength = ivSpecies.length()
    }
}

class ResourceManagerByOwner<T>(
    val type: String,
    private val db: DBContext,
    private val callbacks: Callbacks<T>,
) : ResourceStore<T> {
    class Callbacks<T>(
        val loadState: suspend (session: DBTransaction, count: Int, resources: LongArray) -> Array<T>
    )

    private data class Block<T>(
        // NOTE(Dan): We only store the root stores in a block. Subsequent stores are accessed through the
        // store's next property.
        val stores: Array<ResourceStoreByOwner<T>?> = arrayOfNulls(4096),

        // TODO(Dan): This might need to be @Volatile
        var next: Block<T>? = null,
    )

    private val blockMutationMutex = Mutex()
    private val root: Block<T> = Block()

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

    private suspend fun findStoreByResourceId(id: Long): ResourceStoreByOwner<T>? {
        val loaded = findIdIndexOrLoad(id) ?: return null
        val slot = (id - loaded.minimumId).toInt()
        val reference = loaded.entries[slot]
        val referenceIsUid = loaded.entryIsUid[slot] == 1

        return findOrCreateStore(if (referenceIsUid) reference else 0, if (referenceIsUid) 0 else reference)
    }

    // Provider index
    // =================================================================================================================
    // NOTE(Dan): This index contains references to all resources that are owned by a provider. It becomes automatically
    // populated when a provider contacts us about any resource/when a user requests a resource from them.
    private val providerIndex = AtomicReferenceArray<ProviderIndex>(MAX_PROVIDERS)
    private val providerIndexMutex = Mutex() // NOTE(Dan): Only needed to modify the list of indices
    private val providerOfProducts = SimpleCache<Int, String>(maxAge = SimpleCache.DONT_EXPIRE) { productId ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("product_id", productId) },
                """
                    select pc.provider
                    from
                        accounting.products p
                        join accounting.product_categories pc on p.category = pc.id
                    where
                        p.id = :product_id::bigint
                """
            ).rows.singleOrNull()?.getString(0)
        }
    }

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
                for (entry in uidReferences.tailSet(minimumUid)) {
                    output[ptr++] = entry
                    if (ptr >= output.size) break
                }
                return ptr
            }
        }

        suspend fun findPidStores(output: IntArray, minimumPid: Int): Int {
            mutex.withReader {
                var ptr = 0
                for (entry in pidReferences.tailSet(minimumPid)) {
                    output[ptr++] = entry
                    if (ptr >= output.size) break
                }
                return ptr
            }
        }
    }

    private suspend fun findOrLoadProviderIndex(provider: String): ProviderIndex {
        val initialResult = (0..<MAX_PROVIDERS).find { idx -> providerIndex[idx]?.provider == provider }
        if (initialResult != null) {
            return providerIndex[initialResult]
        }

        providerIndexMutex.withLock {
            val resultAfterLock = (0..<MAX_PROVIDERS).find { idx -> providerIndex[idx]?.provider == provider }
            if (resultAfterLock != null) return providerIndex[resultAfterLock]

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

            return result
        }
    }

    override suspend fun create(
        idCard: IdCard,
        product: Int,
        data: T,
        output: ResourceDocument<T>?
    ): Long {
        if (idCard !is IdCard.User) TODO()
        val uid = if (idCard.activeProject < 0) 0 else idCard.uid
        val pid = idCard.activeProject

        val root = findOrCreateStore(uid, pid)

        var tail = root.findTail()
        while (true) {
            val id = tail.create(idCard, product, data, output)
            if (id < 0L) {
                tail = tail.expand()
            } else {
                val index = findIdIndexOrLoad(id) ?: error("Index was never initialized. findIdIndex is buggy? $id")
                index.register(id, if (pid != 0) pid else uid, pid == 0)

                val providerId = providerOfProducts.get(product) ?: error("Unknown product? $product")
                findOrLoadProviderIndex(providerId).registerUsage(uid, pid)

                return id
            }
        }
    }

    override suspend fun browse(
        idCard: IdCard,
        outputBuffer: Array<ResourceDocument<T>>,
        next: String?,
        flags: ResourceIncludeFlags,
    ): ResourceStore.BrowseResult {
        when (idCard) {
            is IdCard.Provider -> {
                val storeArray = IntArray(128)
                val providerIndex = findOrLoadProviderIndex(idCard.name)

                var minimumUid = 0
                var minimumPid = 0

                if (next != null && next.startsWith("p-")) {
                    minimumUid = Int.MAX_VALUE
                    minimumPid = next.removePrefix("p-").toIntOrNull() ?: Int.MAX_VALUE
                } else if (next != null && next.startsWith("u-")) {
                    minimumUid = next.removePrefix("u-").toIntOrNull() ?: Int.MAX_VALUE
                }

                var didCompleteUsers = minimumUid == Int.MAX_VALUE
                var didCompleteProjects = minimumPid == Int.MAX_VALUE

                var offset = 0
                while (minimumUid < Int.MAX_VALUE && offset < outputBuffer.size) {
                    println("UID: $minimumUid")
                    val resultCount = providerIndex.findUidStores(storeArray, minimumUid)
                    println("resultCount: $resultCount")

                    for (i in 0..<resultCount) {
                        val store = findOrCreateStore(storeArray[i], 0)
                        offset += store.search(idCard, outputBuffer, offset) { true }
                    }
                    println("offset: $offset")

                    if (resultCount == 0) {
                        didCompleteUsers = true
                        break
                    }
                    minimumUid = storeArray[resultCount - 1]
                }

                while (minimumPid < Int.MAX_VALUE && offset < outputBuffer.size) {
                    println("PID: $minimumPid")
                    val resultCount = providerIndex.findPidStores(storeArray, minimumUid)
                    println("resultCount: $resultCount")

                    for (i in 0..<resultCount) {
                        val store = findOrCreateStore(0, storeArray[i])
                        offset += store.search(idCard, outputBuffer, offset) { true }
                    }
                    println("offset: $offset")

                    if (resultCount == 0) {
                        didCompleteProjects = true
                        break
                    }
                    minimumPid = storeArray[resultCount - 1]
                }

                return ResourceStore.BrowseResult(
                    offset,
                    when {
                        didCompleteProjects -> null
                        didCompleteUsers -> "p-$minimumPid"
                        else -> "u-$minimumUid"
                    }
                )
            }

            is IdCard.User -> {
                val uid = if (idCard.activeProject < 0) 0 else idCard.uid
                val pid = idCard.activeProject
                val root = findOrCreateStore(uid, pid)

                var store: ResourceStoreByOwner<T>? = root
                var offset = 0
                while (store != null) {
                    offset += store.search(idCard, outputBuffer, offset) { true }
                    store = store.next
                }
                return ResourceStore.BrowseResult(offset, null)
            }
        }
    }

    override suspend fun addUpdate(idCard: IdCard, id: Long, updates: List<ResourceStore.Update>) {
        TODO("Not yet implemented")
    }

    override suspend fun delete(idCard: IdCard, ids: LongArray) {
        TODO("Not yet implemented")
    }

    override suspend fun retrieveBulk(
        idCard: IdCard,
        ids: LongArray,
        output: Array<ResourceDocument<T>>
    ): Int {
        val storesVisited = HashSet<Pair<Int, Int>>()

        var offset = 0
        for (id in ids) {
            val store = findStoreByResourceId(id) ?: continue

            val storeKey = Pair(store.uid, store.pid)
            if (storeKey in storesVisited) continue
            storesVisited.add(storeKey)

            offset += store.search(idCard, output, offset) { it.id in ids }
        }
        return offset
    }

    override suspend fun search(
        idCard: IdCard,
        outputBuffer: Array<ResourceDocument<T>>,
        query: String,
        next: String?,
        flags: ResourceIncludeFlags
    ): ResourceStore.BrowseResult {
        return ResourceStore.BrowseResult(0, null)
    }

    override suspend fun updateProviderId(id: Long, providerId: String?) {
        TODO("Not yet implemented")
    }

    private suspend fun findOrCreateStore(uid: Int, pid: Int): ResourceStoreByOwner<T> {
        return findRootStore(uid, pid) ?: createRootStore(uid, pid)
    }

    private fun findRootStore(uid: Int, pid: Int): ResourceStoreByOwner<T>? {
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

    private suspend fun createRootStore(uid: Int, pid: Int): ResourceStoreByOwner<T> {
        val result = blockMutationMutex.withLock {
            val existingBlock = findRootStore(uid, pid)
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

            val store = ResourceStoreByOwner<T>(type, uid, pid, db, callbacks)
            block.stores[emptySlotIdx] = store
            store
        }

        result.load()
        return result
    }

    companion object {
        const val MAX_PROVIDERS = 256
    }
}

class IdCardServiceImpl(private val db: DBContext) : IdCardService {
    private val cached = SimpleCache<String, IdCard>(maxAge = 60_000) { username ->
        db.withSession { session ->
            if (username.startsWith(AuthProviders.PROVIDER_PREFIX)) {
                val providerName = username.removePrefix(AuthProviders.PROVIDER_PREFIX)
                val rows = session.sendPreparedStatement(
                    { setParameter("provider", providerName) },
                    """
                        select p.id
                        from
                            accounting.product_categories pc
                            left join accounting.products p on pc.id = p.category
                        where
                            pc.provider = :provider
                    """
                ).rows

                val providerOf = IntArray(rows.size)
                for ((index, row) in rows.withIndex()) {
                    providerOf[index] = row.getLong(0)!!.toInt()
                }

                IdCard.Provider(providerName, providerOf)
            } else {
                val uid = session.sendPreparedStatement(
                    { setParameter("username", username) },
                    "select uid from auth.principals where id = :username"
                ).rows.singleOrNull()?.getInt(0) ?: error("no uid for $username?")

                val adminOf = session.sendPreparedStatement(
                    { setParameter("username", username) },
                    """
                        select p.pid
                        from
                            project.project_members pm join
                            project.projects p on pm.project_id = p.id
                        where 
                            pm.username = :username
                            and (pm.role = 'ADMIN' or pm.role = 'PI')
                    """
                ).rows.map { it.getInt(0)!! }.toIntArray()

                val groups = session.sendPreparedStatement(
                    { setParameter("username", username) },
                    """
                        select g.gid
                        from
                            project.group_members gm
                            join project.groups g on gm.group_id = g.id
                        where
                            gm.username = :username
                    """
                ).rows.map { it.getInt(0)!! }.toIntArray()

                IdCard.User(uid, groups, adminOf, 0)
            }
        }
    }

    private val projectCache = SimpleCache<String, Int>(SimpleCache.DONT_EXPIRE) { projectId ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("project_id", projectId) },
                "select p.pid from project.projects p where id = :project_id"
            ).rows.map { it.getInt(0)!! }.single()
        }
    }

    override suspend fun fetchIdCard(actorAndProject: ActorAndProject): IdCard {
        var card = cached.get(actorAndProject.actor.safeUsername())
            ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val project = actorAndProject.project
        if (project != null && card is IdCard.User) {
            card = card.copy(
                activeProject = projectCache.get(project)
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            )
        }

        return card
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

// Section 4.2 of the "Little Book of Semaphores"
// https://greenteapress.com/semaphores/LittleBookOfSemaphores.pdf
class ReadWriterMutex {
    private val turnstile = Mutex()

    private var readers = 0
    private val readerMutex = Mutex()

    private val roomEmpty = Mutex()

    suspend fun acquireRead() {
        turnstile.withLock { /* do nothing */ }
        readerMutex.withLock {
            readers++
            if (readers == 1) {
                roomEmpty.lock()
            }
        }
    }

    suspend fun releaseRead() {
        readerMutex.withLock {
            readers--
            if (readers == 0) {
                roomEmpty.unlock()
            }
        }
    }

    suspend fun acquireWrite() {
        turnstile.lock()
        roomEmpty.lock()
    }

    suspend fun releaseWrite() {
        turnstile.unlock()
        roomEmpty.unlock()
    }

    @OptIn(ExperimentalContracts::class)
    suspend inline fun <R> withReader(block: () -> R): R {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        acquireRead()
        try {
            return block()
        } finally {
            releaseRead()
        }
    }

    @OptIn(ExperimentalContracts::class)
    suspend inline fun <R> withWriter(block: () -> R): R {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        acquireWrite()
        try {
            return block()
        } finally {
            releaseWrite()
        }
    }
}
