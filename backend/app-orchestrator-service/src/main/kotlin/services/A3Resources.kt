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
import jdk.incubator.vector.IntVector
import jdk.incubator.vector.VectorOperators
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
import kotlin.random.Random

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

private class ResourceStoreByOwner<T>(
    val type: String,
    val uid: Int,
    val pid: Int,
    private val db: DBContext,
    private val callbacks: ResourceStore.Callbacks<T>,
) {
    private val mutex = Mutex()

    private val id = LongArray(STORE_SIZE)
    private val createdAt = LongArray(STORE_SIZE)
    private val createdBy = IntArray(STORE_SIZE)
    private val project = IntArray(STORE_SIZE)
    private val product = IntArray(STORE_SIZE)
    private val data = arrayOfNulls<Any>(STORE_SIZE)
    private val providerId = arrayOfNulls<String>(STORE_SIZE)

    private val aclEntities = arrayOfNulls<IntArray>(STORE_SIZE)
    private val aclIsUser = arrayOfNulls<BooleanArray>(STORE_SIZE)
    private val aclPermissions = arrayOfNulls<ByteArray>(STORE_SIZE)

    private val updates = arrayOfNulls<CyclicArray<ResourceDocumentUpdate>>(STORE_SIZE)

    private var size: Int = 0

    var next: ResourceStoreByOwner<T>? = null
        private set

    private val ready = AtomicBoolean(false)

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
                                        or (:username::text is not null and r.created_by = :username)
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
                        this.data[idx++] = loadedState[batchIndex++]
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

    private suspend fun searchAndConsume(
        idCard: IdCard,
        permissionRequired: Permission,
        consumer: SearchConsumer,
    ) {
        awaitReady()

        mutex.withLock {
            val isOwnerOfEverything = idCard is IdCard.User &&
                    ((uid != 0 && idCard.uid == uid) || (pid != 0 && idCard.adminOf.contains(pid)))

            if (isOwnerOfEverything) {
                val requiresOwnerPrivileges = permissionRequired == Permission.READ ||
                        permissionRequired == Permission.EDIT ||
                        permissionRequired == Permission.ADMIN

                if (requiresOwnerPrivileges) {
                    var idx = 0
                    while (idx < STORE_SIZE && !consumer.shouldTerminate()) {
                        if (this.id[idx] == 0L) break
                        consumer.call(idx)
                        consumer.call(idx + 1)
                        consumer.call(idx + 2)
                        consumer.call(idx + 3)

                        idx += 4
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

                var i = 0
                while (i < STORE_SIZE && !consumer.shouldTerminate()) {
                    if (id[i] == 0L) break
                    val entities = aclEntities[i]
                    if (entities == null) {
                        i++
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

                    i++
                }
            } else if (idCard is IdCard.Provider && idCard.providerOf.isNotEmpty()) {
                val requiresProviderPrivileges =
                    permissionRequired == Permission.READ ||
                            permissionRequired == Permission.EDIT ||
                            permissionRequired == Permission.PROVIDER

                if (requiresProviderPrivileges) {
                    searchInArray(idCard.providerOf, product, consumer)
                }
            }
        }
    }

    suspend fun search(
        idCard: IdCard,
        outputBuffer: Array<ResourceDocument<T>>,
        outputBufferOffset: Int,
        predicate: (ResourceDocument<T>) -> Boolean
    ): Int {
        var outIdx = outputBufferOffset

        val self = this
        val emitter = object : SearchConsumer {
            override fun shouldTerminate(): Boolean = outIdx >= outputBuffer.size
            override fun call(arrIdx: Int) {
                if (outIdx >= outputBuffer.size) return
                if (self.id[arrIdx] == 0L) return

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
                res.data = self.data[arrIdx] as T

                if (!predicate(res)) {
                    outIdx--
                }
            }
        }

        searchAndConsume(idCard, Permission.READ, emitter)

        return outIdx
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
                val updates = self.updates[arrIdx] ?: CyclicArray<ResourceDocumentUpdate>(MAX_UPDATES).also {
                    self.updates[arrIdx] = it
                }

                for ((index, update) in newUpdates.withIndex()) {
                    @Suppress("UNCHECKED_CAST")
                    val shouldAddUpdate =
                        if (consumer != null) consumer(self.data[arrIdx] as T, index, update) else true
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
        searchAndConsume(idCard, Permission.PROVIDER, consumer)
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

        searchAndConsume(idCard, permRequired, consumer)
        return consumer.done
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

    companion object : Loggable {
        override val log = logger()

        const val MAX_UPDATES = 64

        // NOTE(Dan): 99% of all workspaces have less than ~1300 entries, 1024 seem like a good target.
        const val STORE_SIZE = 1024

        private val ivSpecies = IntVector.SPECIES_PREFERRED
        private val ivLength = ivSpecies.length()

        private fun searchInArray(needles: IntArray, haystack: IntArray, emit: SearchConsumer) {
            searchInArrayVector(needles, haystack, emit)
        }

        // NOTE(Dan, 04/07/23): DO NOT CHANGE THIS TO A LAMBDA. Performance of calling lambdas versus calling normal
        // functions through interfaces are dramatically different. My own benchmarks have shown a difference of at
        // least 10x just by switching to an interface instead of a lambda.
        private interface SearchConsumer {
            fun shouldTerminate(): Boolean = false
            fun call(arrIdx: Int)
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
        private fun searchInArrayVector(needles: IntArray, haystack: IntArray, emit: SearchConsumer) {
            require(haystack.size % 4 == 0) { "haystack must have a size which is a multiple of 4! (${haystack.size})" }

            if (haystack.size < ivLength * 4) {
                for ((index, hay) in haystack.withIndex()) {
                    for (needle in needles) {
                        if (hay == needle) {
                            emit.call(index)
                            break
                        }
                    }
                }
            } else {
                var idx = 0
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
                    searchInArrayVector(needles, haystack, discard)
                }
            }
        }
    }
}

class ResourceStore<T>(
    val type: String,
    private val db: DBContext,
    private val productCache: ProductCache,
    private val callbacks: Callbacks<T>,
) {
    data class BrowseResult(val count: Int, val next: String?)

    class Callbacks<T>(
        val loadState: suspend (session: DBTransaction, count: Int, resources: LongArray) -> Array<T>
    )

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
        return findStore(uid, pid) ?: loadStore(uid, pid)
    }

    // Since the stores are linked-list like structure, you should make sure to iterate through all the stores. You need
    // to do this using the `useStores` function. Note that you can terminate the search early once you have performed
    // the request you need to do:
    private inline fun useStores(root: ResourceStoreByOwner<T>, consumer: (ResourceStoreByOwner<T>) -> ShouldContinue) {
        var current: ResourceStoreByOwner<T>? = root
        while (current != null) {
            if (consumer(current) == ShouldContinue.NO) break
            current = current.next
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

    suspend fun create(
        idCard: IdCard,
        product: ProductReference,
        data: T,
        output: ResourceDocument<T>?
    ): Long {
        if (idCard !is IdCard.User) TODO()
        val productId = productCache.referenceToProductId(product)
            ?: throw RPCException("Invalid product supplied", HttpStatusCode.BadRequest)

        val uid = if (idCard.activeProject < 0) 0 else idCard.uid
        val pid = idCard.activeProject

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
    ): ResourceStore.BrowseResult {
        // TODO(Dan): Pagination is not yet working
        // TODO(Dan): Sorting is not yet working
        // TODO(Dan): Filtering is not yet working
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
                    val resultCount = providerIndex.findUidStores(storeArray, minimumUid)

                    for (i in 0..<resultCount) {
                        useStores(findOrLoadStore(storeArray[i], 0)) { store ->
                            offset += store.search(idCard, outputBuffer, offset) { true }
                            ShouldContinue.ifThisIsTrue(offset < outputBuffer.size)
                        }
                    }

                    if (resultCount == 0) {
                        didCompleteUsers = true
                        break
                    }
                    minimumUid = storeArray[resultCount - 1]
                }

                while (minimumPid < Int.MAX_VALUE && offset < outputBuffer.size) {
                    val resultCount = providerIndex.findPidStores(storeArray, minimumUid)
                    for (i in 0..<resultCount) {
                        useStores(findOrLoadStore(0, storeArray[i])) { store ->
                            offset += store.search(idCard, outputBuffer, offset) { true }
                            ShouldContinue.ifThisIsTrue(offset < outputBuffer.size)
                        }
                    }

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

                var offset = 0
                useStores(findOrLoadStore(uid, pid)) { store ->
                    offset += store.search(idCard, outputBuffer, offset) { true }
                    ShouldContinue.ifThisIsTrue(offset < outputBuffer.size)
                }
                return ResourceStore.BrowseResult(offset, null)
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
        val storesVisited = HashSet<Pair<Int, Int>>()

        var offset = 0
        for (id in ids) {
            val initialStore = findStoreByResourceId(id) ?: continue

            val storeKey = Pair(initialStore.uid, initialStore.pid)
            if (storeKey in storesVisited) continue
            storesVisited.add(storeKey)

            useStores(initialStore) { currentStore ->
                offset += currentStore.search(idCard, output, offset) { it.id in ids }
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
                for (entry in uidReferences.tailSet(minimumUid, false)) {
                    output[ptr++] = entry
                    if (ptr >= output.size) break
                }
                return ptr
            }
        }

        suspend fun findPidStores(output: IntArray, minimumPid: Int): Int {
            mutex.withReader {
                var ptr = 0
                for (entry in pidReferences.tailSet(minimumPid, false)) {
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

    companion object {
        const val MAX_PROVIDERS = 256
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
                ).rows.map { it.getInt(0)!!.also { require(it != Int.MAX_VALUE) } }.toIntArray()

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
                ).rows.map { it.getInt(0)!!.also { require(it != Int.MAX_VALUE) } }.toIntArray()

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

    suspend fun fetchIdCard(actorAndProject: ActorAndProject): IdCard {
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

    suspend fun lookupUid(uid: Int): String? {
        return reverseUidCache.get(uid)
    }

    suspend fun lookupPid(pid: Int): String? {
        return reversePidCache.get(pid)
    }

    suspend fun lookupGid(gid: Int): AclEntity.ProjectGroup? {
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
    private val nextFill = AtomicLong(0L)

    suspend fun fillCache() {
        if (Time.now() < nextFill.get()) return
        mutex.withWriter {
            if (Time.now() < nextFill.get()) return

            referenceToProductId.clear()
            productIdToReference.clear()
            productInformation.clear()

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
