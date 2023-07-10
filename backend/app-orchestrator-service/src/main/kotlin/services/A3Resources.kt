package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.ResourceDocument
import dk.sdu.cloud.accounting.util.ResourceDocumentUpdate
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.provider.api.ResourceIncludeFlags
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
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
import kotlin.collections.HashSet
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

interface FilterFunction<T> {
    fun filter(doc: ResourceDocument<T>): Boolean
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

class ResourceStoreByOwner<T>(
    val type: String,
    val uid: Int,
    val pid: Int,
    private val db: DBContext,
    private val callbacks: ResourceStore.Callbacks<T>,
) {
    private val mutex = Mutex()

    val id = LongArray(STORE_SIZE)
    val createdAt = LongArray(STORE_SIZE)
    val createdBy = IntArray(STORE_SIZE)
    val project = IntArray(STORE_SIZE)
    val product = IntArray(STORE_SIZE)
    private val _data = arrayOfNulls<Any>(STORE_SIZE)
    val providerId = arrayOfNulls<String>(STORE_SIZE)

    val aclEntities = arrayOfNulls<IntArray>(STORE_SIZE)
    val aclIsUser = arrayOfNulls<BooleanArray>(STORE_SIZE)
    val aclPermissions = arrayOfNulls<ByteArray>(STORE_SIZE)

    val updates = arrayOfNulls<CyclicArray<ResourceDocumentUpdate>>(STORE_SIZE)
    private val dirtyFlag = BooleanArray(STORE_SIZE)
    private var anyDirty = false

    private var size: Int = 0

    fun data(idx: Int): T? {
        @Suppress("UNCHECKED_CAST")
        return _data[idx] as T?
    }

    var next: ResourceStoreByOwner<T>? = null
        private set

    var previous: ResourceStoreByOwner<T>? = null
        private set

    private val ready = AtomicBoolean(false)

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
            this._data[idx] = data
            this.dirtyFlag[idx] = true
            anyDirty = true

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

    private suspend fun searchAndConsume(
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
                        binarySearchForId(startId, hasLock = true)
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

    suspend fun search(
        idCard: IdCard,
        outputBuffer: Array<ResourceDocument<T>>,
        outputBufferOffset: Int,
        startIdExclusive: Long = -1L,
        reverseOrder: Boolean = false,
        predicate: FilterFunction<T>,
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
                res.id = self.id[arrIdx]
                res.createdAt = self.createdAt[arrIdx]
                res.createdBy = self.createdBy[arrIdx]
                res.project = self.project[arrIdx]
                res.product = self.product[arrIdx]
                res.providerId = self.providerId[arrIdx]
                run {
                    Arrays.fill(res.update, null)
                    val resourceUpdates = self.updates[arrIdx] ?: return@run
                    for ((index, update) in resourceUpdates.withIndex()) {
                        if (index >= res.update.size) break
                        res.update[index] = update
                    }
                }
                run {
                    Arrays.fill(res.acl, null)
                    val entities = self.aclEntities[arrIdx]
                    val isUser = self.aclIsUser[arrIdx]
                    val perms = self.aclPermissions[arrIdx]

                    if (entities != null && isUser != null && perms != null) {
                        for (i in entities.indices) {
                            if (entities[i] == 0) break
                            res.acl[i] =
                                ResourceDocument.AclEntry(entities[i], isUser[i], Permission.fromByte(perms[i]))
                        }
                    }
                }

                @Suppress("UNCHECKED_CAST")
                res.data = self._data[arrIdx] as T

                if (!predicate.filter(res)) {
                    outIdx--
                }
            }
        }

        searchAndConsume(idCard, Permission.READ, emitter, startIdExclusive, reverseOrder)

        return outIdx - outputBufferOffset
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

    fun findTail(): ResourceStoreByOwner<T> {
        if (next == null) return this
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
            newStore.previous = this
            next = newStore
            if (!loadRequired) {
                newStore.ready.set(true)
            }
            return newStore
        } finally {
            if (!hasLock) mutex.unlock()
        }
    }

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

        // NOTE(Dan, 04/07/23): DO NOT CHANGE THIS TO A LAMBDA. Performance of calling lambdas versus calling normal
        // functions through interfaces are dramatically different. My own benchmarks have shown a difference of at
        // least 10x just by switching to an interface instead of a lambda.
        private interface SearchConsumer {
            fun shouldTerminate(): Boolean = false // NOTE(Dan): Do not make this suspend
            fun call(arrIdx: Int) // NOTE(Dan): Do not make this suspend
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
        suspend fun saveState(session: DBTransaction, store: ResourceStoreByOwner<T>, indices: IntArray, length: Int)
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
        val stores: Array<ResourceStoreByOwner<T>?> = arrayOfNulls(4096),

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
    private suspend fun findOrLoadStore(uid: Int, pid: Int): ResourceStoreByOwner<T> {
        require(uid == 0 || pid == 0)
        return findStore(uid, pid) ?: loadStore(uid, pid)
    }

    // Since the stores are linked-list like structure, you should make sure to iterate through all the stores. You need
    // to do this using the `useStores` function. Note that you can terminate the search early once you have performed
    // the request you need to do:
    private inline fun useStores(
        root: ResourceStoreByOwner<T>,
        reverseOrder: Boolean = false,
        consumer: (ResourceStoreByOwner<T>) -> ShouldContinue
    ) {
        if (!reverseOrder) {
            var current: ResourceStoreByOwner<T>? = root
            while (current != null) {
                if (consumer(current) == ShouldContinue.NO) break
                current = current.next
            }
        } else {
            var current: ResourceStoreByOwner<T>? = root.findTail()
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
    private fun findStore(uid: Int, pid: Int): ResourceStoreByOwner<T>? {
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
    private suspend fun loadStore(uid: Int, pid: Int): ResourceStoreByOwner<T> {
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

            val store = ResourceStoreByOwner<T>(type, uid, pid, db, callbacks)
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
        return scope.launch {
            while (coroutineContext.isActive) {
                try {
                    val start = Time.now()
                    db.withSession { session ->
                        var block = root
                        while (true) {
                            for (entry in block.stores) {
                                if (entry == null) continue
                                entry.synchronizeToDatabase(session)
                            }
                            block = block.next ?: break
                        }
                    }
                    val end = Time.now()

                    delay(max(1000, 10_000 - (end - start)))
                } catch (ex: Throwable) {
                    log.warn(ex.toReadableStacktrace().toString())
                }
            }
        }
    }

    suspend fun create(
        idCard: IdCard,
        product: ProductReference,
        data: T,
        output: ResourceDocument<T>?
    ): Long {
        if (idCard !is IdCard.User) TODO()
        val productId = productCache.referenceToProductId(product)
            ?: throw RPCException("Invalid product supplied", HttpStatusCode.BadRequest)

        val uid = if (idCard.activeProject <= 0) idCard.uid else 0
        val pid = if (uid == 0) idCard.activeProject else 0

        val root = findOrLoadStore(uid, pid)

        var tail = root.findTail()
        while (true) {
            val id = tail.create(idCard, productId, data, output)
            if (id < 0L) {
                tail = tail.expand()
            } else {
                val index = findIdIndexOrLoad(id) ?: error("Index was never initialized. findIdIndex is buggy? $id")
                index.register(id, if (pid != 0) pid else uid, pid == 0)

                val providerId =
                    productCache.productIdToReference(productId)?.provider ?: error("Unknown product? $product")
                findOrLoadProviderIndex(providerId).registerUsage(uid, pid)

                return id
            }
        }
    }

    suspend fun browse(
        idCard: IdCard,
        outputBuffer: Array<ResourceDocument<T>>,
        next: String?,
        flags: ResourceIncludeFlags,
        outputBufferLimit: Int = outputBuffer.size,
    ): BrowseResult {
        // TODO(Dan): Sorting is not yet working
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

        val filter = object : FilterFunction<T> {
            override fun filter(doc: ResourceDocument<T>): Boolean {
                var success = true

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

                return success
            }
        }

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

                var offset = 0
                useStores(findOrLoadStore(uid, pid), reverseOrder = true) { store ->
                    offset += store.search(
                        idCard,
                        outputBuffer,
                        offset,
                        startIdExclusive = startId,
                        reverseOrder = true,
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
    ): ResourceDocument<T>? {
        // NOTE(Dan): This is just a convenience wrapper around retrieveBulk()
        val output = arrayOf(ResourceDocument<T>())
        val success = retrieveBulk(idCard, longArrayOf(id), output) == 1
        if (!success) return null
        return output[0]
    }

    suspend fun retrieveBulk(
        idCard: IdCard,
        ids: LongArray,
        output: Array<ResourceDocument<T>>
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
                offset += currentStore.search(idCard, output, offset, minimumIdExclusive, predicate = filter)
                ShouldContinue.ifThisIsTrue(offset < output.size)
            }
        }
        return offset
    }

    suspend fun search(
        idCard: IdCard,
        outputBuffer: Array<ResourceDocument<T>>,
        query: String,
        next: String?,
        flags: ResourceIncludeFlags
    ): ResourceStore.BrowseResult {
        return ResourceStore.BrowseResult(0, null)
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

    private suspend fun findStoreByResourceId(id: Long): ResourceStoreByOwner<T>? {
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

class IdCardService(private val db: DBContext) {
    private val reverseUidCache = SimpleCache<Int, String>(maxAge = SimpleCache.DONT_EXPIRE) { uid ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("uid", uid) },
                """
                    select id 
                    from auth.principals p
                    where
                        p.uid = :uid
                """
            ).rows.singleOrNull()?.getString(0)
        }
    }

    private val reversePidCache = SimpleCache<Int, String>(maxAge = SimpleCache.DONT_EXPIRE) { pid ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("pid", pid) },
                """
                    select id 
                    from project.projects p
                    where
                        p.pid = :pid
                """
            ).rows.singleOrNull()?.getString(0)
        }
    }

    private val reverseGidCache = SimpleCache<Int, AclEntity.ProjectGroup>(maxAge = SimpleCache.DONT_EXPIRE) { gid ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("gid", gid) },
                """
                    select project, id 
                    from project.groups g
                    where
                        g.gid = :gid
                """
            ).rows.singleOrNull()?.let {
                AclEntity.ProjectGroup(it.getString(0)!!, it.getString(1)!!)
            }
        }
    }

    private val uidCache = SimpleCache<String, Int>(maxAge = SimpleCache.DONT_EXPIRE) { username ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("username", username) },
                """
                    select uid::int4 
                    from auth.principals p
                    where
                        p.id = :username
                """
            ).rows.singleOrNull()?.getInt(0)
        }
    }

    private val gidCache = SimpleCache<String, Int>(maxAge = SimpleCache.DONT_EXPIRE) { groupId ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("group_id", groupId) },
                """
                    select gid::int4 
                    from project.groups g
                    where
                        g.id = :group_id
                """
            ).rows.singleOrNull()?.getInt(0)
        }
    }


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

                // NOTE(Dan): The ACL checks use Int.MAX_VALUE as a list terminator. The terminator element is ignored
                // during the search and simply assumed to never appear in the data. To make sure this is true, we
                // assert that no user/group has that ID.
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
                ).rows.map { it.getInt(0)!!.also { require(it != Int.MAX_VALUE && it != 0) } }.toIntArray()

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
                ).rows.map { it.getInt(0)!!.also { require(it != Int.MAX_VALUE && it != 0) } }.toIntArray()

                IdCard.User(uid, groups, adminOf, 0)
            }
        }
    }

    private val projectCache = SimpleCache<String, Int>(SimpleCache.DONT_EXPIRE) { projectId ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("project_id", projectId) },
                "select p.pid from project.projects p where id = :project_id"
            ).rows.map { it.getInt(0)!! }.singleOrNull()
        }
    }

    suspend fun fetchIdCard(actorAndProject: ActorAndProject): IdCard {
        var card = cached.get(actorAndProject.actor.safeUsername())
            ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val project = actorAndProject.project
        if (project != null && card is IdCard.User) {
            card = card.copy(
                activeProject = projectCache.get(project) ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            )
        }

        return card
    }

    suspend fun lookupUid(uid: Int): String? {
        if (uid == 0) return null
        return reverseUidCache.get(uid)
    }

    suspend fun lookupPid(pid: Int): String? {
        if (pid == 0) return null
        return reversePidCache.get(pid)
    }

    suspend fun lookupGid(gid: Int): AclEntity.ProjectGroup? {
        if (gid == 0) return null
        return reverseGidCache.get(gid)
    }

    suspend fun lookupUidFromUsername(username: String): Int? {
        return uidCache.get(username)
    }

    suspend fun lookupGidFromGroupId(groupId: String): Int? {
        return gidCache.get(groupId)
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

class ProductCache(private val db: DBContext) {
    private val mutex = ReadWriterMutex()
    private val referenceToProductId = HashMap<ProductReference, Int>()
    private val productIdToReference = HashMap<Int, ProductReference>()
    private val productInformation = HashMap<Int, Product>()
    private val productNameToProductIds = HashMap<String, ArrayList<Int>>()
    private val productCategoryToProductIds = HashMap<String, ArrayList<Int>>()
    private val productProviderToProductIds = HashMap<String, ArrayList<Int>>()
    private val nextFill = AtomicLong(0L)

    suspend fun fillCache() {
        if (Time.now() < nextFill.get()) return
        mutex.withWriter {
            if (Time.now() < nextFill.get()) return

            referenceToProductId.clear()
            productIdToReference.clear()
            productInformation.clear()
            productNameToProductIds.clear()
            productCategoryToProductIds.clear()
            productProviderToProductIds.clear()

            db.withSession { session ->
                session.sendPreparedStatement(
                    {},
                    """
                        declare product_load cursor for
                        select accounting.product_to_json(p, pc, 0), p.id
                        from
                            accounting.products p join
                            accounting.product_categories pc on
                                p.category = pc.id
                    """
                )

                while (true) {
                    val rows = session.sendPreparedStatement({}, "fetch forward 100 from product_load").rows
                    if (rows.isEmpty()) break

                    rows.forEach { row ->
                        val product = defaultMapper.decodeFromString(Product.serializer(), row.getString(0)!!)
                        val id = row.getLong(1)!!.toInt()

                        val reference = ProductReference(product.name, product.category.name, product.category.provider)
                        referenceToProductId[reference] = id
                        productIdToReference[id] = reference
                        productInformation[id] = product

                        productNameToProductIds.getOrPut(product.name) { ArrayList() }.add(id)
                        productCategoryToProductIds.getOrPut(product.category.name) { ArrayList() }.add(id)
                        productProviderToProductIds.getOrPut(product.category.provider) { ArrayList() }.add(id)
                    }
                }
            }

            nextFill.set(Time.now() + 60_000 * 5)
        }
    }

    suspend fun referenceToProductId(ref: ProductReference): Int? {
        fillCache()
        return mutex.withReader {
            referenceToProductId[ref]
        }
    }

    suspend fun productIdToReference(id: Int): ProductReference? {
        fillCache()
        return mutex.withReader {
            productIdToReference[id]
        }
    }

    suspend fun productIdToProduct(id: Int): Product? {
        fillCache()
        return mutex.withReader {
            productInformation[id]
        }
    }

    suspend fun productNameToProductIds(name: String): List<Int>? {
        fillCache()
        return mutex.withReader {
            productNameToProductIds[name]
        }
    }

    suspend fun productCategoryToProductIds(category: String): List<Int>? {
        fillCache()
        return mutex.withReader {
            productCategoryToProductIds[category]
        }
    }

    suspend fun productProviderToProductIds(provider: String): List<Int>? {
        fillCache()
        return mutex.withReader {
            productProviderToProductIds[provider]
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

    fun releaseWrite() {
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

/**
 * A cyclic array containing the latest [capacity] elements.
 *
 * Once a new element is added, beyond the [capacity], then the oldest element is removed from the array. The oldest
 * element is stored at index 0, while the newest element is stored at index [capacity] - 1.
 *
 * @param capacity The maximum number of elements the array can hold.
 * @param T The type of elements stored in the array.
 */
class CyclicArray<T>(val capacity: Int) : Iterable<T> {
    private val data = arrayOfNulls<Any>(capacity)
    private var head = 0
    var size: Int = 0
        private set

    fun add(element: T) {
        if (size < capacity) {
            data[size] = element
            size++
        } else {
            data[head] = element
            head = (head + 1) % capacity
        }
    }

    operator fun get(index: Int): T {
        require(index in 0 until size) { "index out of bounds $index !in 0..<$size" }
        @Suppress("UNCHECKED_CAST")
        return data[(head + index) % capacity] as T
    }

    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            var offset = 0

            override fun hasNext(): Boolean = offset < size

            override fun next(): T {
                @Suppress("UNCHECKED_CAST")
                return data[(head + (offset++)) % capacity] as T
            }
        }
    }
}
