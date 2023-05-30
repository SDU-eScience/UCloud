package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.IdCardService
import dk.sdu.cloud.accounting.util.ResourceDocument
import dk.sdu.cloud.accounting.util.ResourceStore
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.provider.api.ResourceIncludeFlags
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.DBTransaction
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
                    expand(loadRequired = true).load(this.id[STORE_SIZE - 1])
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
        idCard: IdCard,
        product: Int,
        data: T,
        output: ResourceDocument<T>?,
    ): Long {
        awaitReady()

        mutex.withLock {
            if (size >= STORE_SIZE - 1) return -1

            val idx = size++
            val id = /*baseId*/ 0L + idx

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

            val isOwnerOfEverything = (uid != 0 && idCard.uid == uid) || (pid != 0 && idCard.adminOf.contains(pid))

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
            } else if (idCard.activeProject == 0) {
                /*
                // TODO(Dan): This branch is useless, since isOwnerOfEverything will always be taken instead. Leaving
                //  it here to use as a base for group ACL checks.
                val uidVector = IntVector.broadcast(ivSpecies, idCard.uid)

                val resultArray = BooleanArray(ivLength)
                var idx = 0
                while (idx < STORE_SIZE && outIdx < outputBuffer.size) {
                    val cv1 = IntVector.fromArray(ivSpecies, createdBy, idx)
                    val cv2 = IntVector.fromArray(ivSpecies, createdBy, idx + (ivLength * 1))
                    val cv3 = IntVector.fromArray(ivSpecies, createdBy, idx + (ivLength * 2))
                    val cv4 = IntVector.fromArray(ivSpecies, createdBy, idx + (ivLength * 3))

                    val cr1 = uidVector.compare(VectorOperators.EQ, cv1)
                    val cr2 = uidVector.compare(VectorOperators.EQ, cv2)
                    val cr3 = uidVector.compare(VectorOperators.EQ, cv3)
                    val cr4 = uidVector.compare(VectorOperators.EQ, cv4)

                    val t1 = cr1.firstTrue()
                    val t2 = cr2.firstTrue()
                    val t3 = cr3.firstTrue()
                    val t4 = cr4.firstTrue()

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

                    idx += ivLength * 4
                }
                 */
            }
            return outIdx
        }
    }

    suspend fun findTail(): ResourceStoreByOwner<T> {
        mutex.withLock {
            if (next == null) return this
        }
        return next!!.findTail()
    }

    suspend fun expand(loadRequired: Boolean = false): ResourceStoreByOwner<T> {
        val currentNext = next
        if (currentNext != null) return currentNext

        mutex.withLock {
            val nextAfterMutex = next
            if (nextAfterMutex != null) return nextAfterMutex

            val newStore = ResourceStoreByOwner<T>(type, uid, pid, db, callbacks)
            next = newStore
            if (!loadRequired) {
                newStore.ready.set(true)
            }
            return newStore
        }
    }

    companion object {
        // NOTE(Dan): 99% have less than ~1300, we might want to decrease the store size to 1024.
        const val STORE_SIZE = 1024

//        private val ivSpecies = IntVector.SPECIES_PREFERRED
//        private val ivLength = ivSpecies.length()

        // TODO(Dan): This would require us to re-shuffle (very hard) existing IDs around or implement a work-around
        //  within the store.
        private val baseIdAllocator = AtomicLong(0L)
        fun allocateBaseId(): Long {
            return baseIdAllocator.getAndAdd(STORE_SIZE.toLong())
        }
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

    override suspend fun create(
        idCard: IdCard,
        product: Int,
        data: T,
        output: ResourceDocument<T>?
    ): Long {
        val uid = if (idCard.activeProject < 0) 0 else idCard.uid
        val pid = idCard.activeProject

        val root = findRootStore(uid, pid) ?: createRootStore(uid, pid)

        var tail = root.findTail()
        while (true) {
            val id = tail.create(idCard, product, data, output)
            if (id < 0L) {
                tail = tail.expand()
            } else {
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
        val uid = if (idCard.activeProject < 0) 0 else idCard.uid
        val pid = idCard.activeProject
        val root = findRootStore(uid, pid) ?: createRootStore(uid, pid)

        var store: ResourceStoreByOwner<T>? = root
        var offset = 0
        while (store != null) {
            offset += store.search(idCard, outputBuffer, offset) { true }
            store = store.next
        }
        return ResourceStore.BrowseResult(offset, null)
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
        val uid = if (idCard.activeProject < 0) 0 else idCard.uid
        val pid = idCard.activeProject
        val root = findRootStore(uid, pid) ?: return 0

        var store: ResourceStoreByOwner<T>? = root
        var offset = 0
        while (store != null) {
            offset += store.search(idCard, output, offset) { it.id in ids }
            store = store.next
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
}


class IdCardServiceImpl(private val db: DBContext) : IdCardService {
    private val cached = SimpleCache<String, IdCard>(maxAge = 60_000) { username ->
        db.withSession { session ->
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

            IdCard(uid, groups, adminOf, 0)
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
        if (project != null) {
            card = card.copy(
                activeProject = projectCache.get(project)
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            )
        }

        return card
    }
}
