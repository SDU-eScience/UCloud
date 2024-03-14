package dk.sdu.cloud.accounting.services.accounting

import com.google.common.primitives.Longs.min
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.util.IIdCardService
import dk.sdu.cloud.accounting.util.IProductCache
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.DistributedLock
import dk.sdu.cloud.service.DistributedLockFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.toReadableStacktrace
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.random.Random

class AccountingSystem(
    private val productCache: IProductCache,
    private val persistence: AccountingPersistence,
    private val idCardService: IIdCardService,
    private val distributedLocks: DistributedLockFactory,
    private val disableMasterElection: Boolean,
) {
    private data class Request(val id: Long, val message: AccountingRequest<*>)
    private data class Response<Resp>(
        val status: HttpStatusCode,
        val success: Resp? = null,
        val error: String? = null,
        var id: Long = 0L,
    ) {
        fun unwrap(): Resp =
            if (status.isSuccess()) success ?: error("Invalid message")
            else throw RPCException(error ?: "Internal error", status)

        companion object {
            fun <Resp> ok(resp: Resp): Response<Resp> = Response(HttpStatusCode.OK, resp)
            fun <Resp> error(status: HttpStatusCode, message: String): Response<Resp> =
                Response(status, error = message)
        }
    }

    // NOTE(Dan): Without replays, we risk the async listener missing the response if the coroutine is too slow to start
    private val requests = Channel<Request>(Channel.BUFFERED)
    private val responses = MutableSharedFlow<Response<*>>(replay = 16)

    private val turnstile = Mutex()
    private var isActiveProcessor = AtomicBoolean(false)

    private var requestIdGenerator = AtomicLong(0)

    suspend fun <Resp> sendRequest(request: AccountingRequest<Resp>): Resp {
        val id = requestIdGenerator.getAndIncrement()

        return coroutineScope {
            @Suppress("UNCHECKED_CAST")
            val collector = async {
                var result: Response<Resp>? = null
                responses.takeWhile {
                    if (it.id == id) result = it as Response<Resp>
                    it.id != id
                }.collect()
                result ?: error("No response was ever received")
            }
            requests.send(Request(id, request))

            collector.await()
        }.unwrap()
    }

    suspend fun sendRequestNoUnwrap(request: AccountingRequest<Unit>): String? {
        val id = requestIdGenerator.getAndIncrement()

        return coroutineScope {
            @Suppress("UNCHECKED_CAST")
            val collector = async {
                var result: Response<Unit>? = null
                responses.takeWhile {
                    if (it.id == id) result = it as Response<Unit>
                    it.id != id
                }.collect()
                result ?: error("No response was ever received")
            }
            requests.send(Request(id, request))

            collector.await()
        }.error
    }

    fun start(scope: CoroutineScope): Job {
        return scope.launch {
            val lock = distributedLocks.create("accounting_processor", duration = 60_000)

            Runtime.getRuntime().addShutdownHook(
                Thread {
                    runBlocking {
                        if (!isActiveProcessor.get()) {
                            return@runBlocking
                        }

                        turnstile.withLock {
                            if (!disableMasterElection && !lock.renew(60_000)) {
                                return@runBlocking
                            }

//                            attemptSynchronize(true)
                            lock.release()
                        }
                    }
                }
            )

            isActiveProcessor.set(true)
            while (isActive && isActiveProcessor.get()) {
                try {
                    processMessages(lock)
                } catch (ex: Throwable) {
                    log.info("Error happened when attempting to lock service: $ex")
                }

                if (isActiveProcessor.get()) {
                    delay(15000 + Random.nextLong(5000))
                }
            }
        }
    }

    private val toCheck = ArrayList<Int>()

    private suspend fun processMessages(lock: DistributedLock) {
        val didAcquire = disableMasterElection || lock.acquire()
        if (!didAcquire) return

        // Resetting state, so we do not attempt to load into already existing in-mem DB resulting in conflicts
        // resetState()

        log.info("This service has become the master responsible for handling Accounting processor events!")
        // activeProcessor.set(AccountingProcessor.ActiveProcessor(addressToSelf))

        persistence.initialize()

        var nextSynchronization = Time.now() + 0

        while (currentCoroutineContext().isActive && isActiveProcessor.get()) {
            try {
                turnstile.withLock {
                    select {
                        requests.onReceive { request ->
                            // NOTE(Dan): We attempt a synchronization here in case we receive so many requests that the
                            // timeout is never triggered.
                            // attemptSynchronize()

                            toCheck.clear()
                            var response = try {
                                when (val msg = request.message) {
                                    is AccountingRequest.Charge -> charge(msg)
                                    is AccountingRequest.RootAllocate -> rootAllocate(msg)
                                    is AccountingRequest.SubAllocate -> subAllocate(msg)
                                    is AccountingRequest.ScanRetirement -> scanRetirement(msg)
                                    is AccountingRequest.MaxUsable -> maxUsable(msg)
                                    is AccountingRequest.BrowseWallets -> browseWallets(msg)
                                    is AccountingRequest.StopSystem -> {
                                        isActiveProcessor.set(false)
                                        Response.ok(Unit)
                                    }
                                }
                            } catch (e: Throwable) {
                                Response.error(HttpStatusCode.InternalServerError, e.toReadableStacktrace().toString())
                            }

                            try {
                                toCheck.forEach { checkWalletHierarchy(it) }
                            } catch (e: Throwable) {
                                response = Response.error(HttpStatusCode.InternalServerError, e.toReadableStacktrace().toString())
                            }

                            response.id = request.id
                            responses.emit(response)
                        }

                        onTimeout(500) {
                            // attemptSynchronize()
                        }
                    }
                    /*
                    if (!renewLock(lock)) {
                        isAlive = false
                        isActiveProcessor = false
                    }
                     */
                }
            } catch (ex: Throwable) {
                log.info(ex.toReadableStacktrace().toString())
            }
        }
    }

    private enum class ActionType {
        CHARGE,
        SUB_ALLOCATE,
        ROOT_ALLOCATE,
        READ,
    }

    private suspend fun findOwner(owner: String): InternalOwner? {
        val ref = ownersByReference[owner]
        if (ref != null) return ref
        val isValid = if (InternalOwner.PROJECT_REGEX.matches(owner)) {
            idCardService.lookupPidFromProjectId(owner) != null
        } else {
            idCardService.lookupUidFromUsername(owner) != null
        }
        if (!isValid) return null

        val newId = ownersIdAccumulator.getAndIncrement()
        val newOwner = InternalOwner(newId, owner)
        ownersByReference[owner] = newOwner
        ownersById[newId] = newOwner
        return newOwner
    }

    private suspend fun authorizeAndLocateWallet(
        idCard: IdCard,
        owner: String,
        categoryId: ProductCategoryIdV2,
        type: ActionType,
    ): InternalWallet? {
        val internalOwner = findOwner(owner) ?: return null
        val productCategory = productCache.productCategory(categoryId) ?: return null
        val wallets = walletsByOwner.getOrPut(internalOwner.id) { ArrayList() }
        val existingWallet = wallets.find { it.category.toId() == categoryId }

        val ownerUid = if (internalOwner.isProject()) {
            null
        } else {
            idCardService.lookupUidFromUsername(internalOwner.reference)
        }

        val ownerPid = if (internalOwner.isProject()) {
            idCardService.lookupPidFromProjectId(internalOwner.reference)
        } else {
            null
        }

        if (ownerUid == null && ownerPid == null) return null

        when (type) {
            ActionType.READ -> {
                when (idCard) {
                    IdCard.System -> {
                        // OK
                    }

                    is IdCard.Provider -> {
                        if (idCard.name != categoryId.provider) return null
                    }

                    is IdCard.User -> {
                        if (ownerUid != null && idCard.uid != ownerUid) return null
                        if (ownerPid != null && !idCard.adminOf.contains(ownerPid)) return null
                    }
                }
            }

            ActionType.CHARGE -> {
                if (idCard != IdCard.System) {
                    if (idCard !is IdCard.Provider) return null
                    if (idCard.name != categoryId.provider) return null
                }
            }

            ActionType.SUB_ALLOCATE -> {
                if (idCard != IdCard.System) {
                    if (idCard !is IdCard.User) return null
                    if (ownerUid != null && idCard.uid != ownerUid) return null
                    if (ownerPid != null && !idCard.adminOf.contains(ownerPid)) return null
                }
            }

            ActionType.ROOT_ALLOCATE -> {
                if (idCard != IdCard.System) {
                    val providerPid = idCardService.retrieveProviderProjectPid(categoryId.provider)
                    if (providerPid == null) return null
                    if (idCard !is IdCard.User) return null
                    if (!idCard.adminOf.contains(providerPid)) return null
                }
            }
        }

        val wallet = if (existingWallet != null) {
            existingWallet
        } else {
            val id = walletsIdAccumulator.getAndIncrement()
            InternalWallet(
                id = id,
                ownedBy = internalOwner.id,
                category = productCategory,
                localUsage = 0L,
                allocationsByParent = HashMap(),
                childrenUsage = HashMap(),
                localRetiredUsage = 0L,
                childrenRetiredUsage = HashMap(),
                localOverspending = 0L,
                totalAllocated = 0L,
                totalRetiredAllocated = 0L,
            ).also {
                wallets.add(it)
                walletsById[id] = it
            }
        }

        toCheck.add(wallet.id)
        return wallet
    }

    private suspend fun rootAllocate(request: AccountingRequest.RootAllocate): Response<Int> {
        val idCard = request.idCard
        if (idCard !is IdCard.User) {
            return Response.error(HttpStatusCode.Forbidden, "You are not allowed to create a root allocation!")
        }

        if (idCard.activeProject == 0) {
            return Response.error(HttpStatusCode.Forbidden, "Cannot perform a root allocation to a personal workspace!")
        }

        val projectId = idCardService.lookupPid(idCard.activeProject)
            ?: return Response.error(HttpStatusCode.InternalServerError, "Could not lookup project from id card")

        val wallet = authorizeAndLocateWallet(request.idCard, projectId, request.category, ActionType.ROOT_ALLOCATE)
            ?: return Response.error(HttpStatusCode.Forbidden, "You are not allowed to create a root allocation!")

        val currentParents = wallet.allocationsByParent.keys
        if (currentParents.isNotEmpty() && currentParents.singleOrNull() != 0) {
            return Response.error(
                HttpStatusCode.Forbidden,
                "Refusing to create a root allocation in this wallet (must only contain root allocations)"
            )
        }

        if (request.start > request.end) {
            return Response.error(
                HttpStatusCode.BadRequest,
                "Start must occur before the end of an allocation ${request.start} > ${request.end}!"
            )
        }

        if (request.amount < 0) {
            return Response.error(
                HttpStatusCode.BadRequest,
                "Cannot create a root allocation with a negative quota!"
            )
        }

        return Response.ok(insertAllocation(wallet, parentWalletId = 0, request.amount, request.start, request.end))
    }

    private suspend fun subAllocate(request: AccountingRequest.SubAllocate): Response<Int> {
        val idCard = request.idCard
        val parentOwner = lookupOwner(idCard)
            ?: return Response.error(HttpStatusCode.Forbidden, "Could not find information about your project")
        val internalParent = authorizeAndLocateWallet(idCard, parentOwner, request.category, ActionType.READ)
            ?: return Response.error(HttpStatusCode.Forbidden, "You are not allowed to create this sub-allocation")

        toCheck.add(internalParent.id)

        val owner = ownersById.getValue(internalParent.ownedBy)
        if (!owner.isProject()) {
            return Response.error(HttpStatusCode.Forbidden, "Only projects are allowed to create sub-allocations")
        }

        val ownerPid = idCardService.lookupPidFromProjectId(owner.reference)
            ?: return Response.error(HttpStatusCode.InternalServerError, "Unknown project")
        val ownerProjectInfo = idCardService.lookupProjectInformation(ownerPid)
            ?: return Response.error(HttpStatusCode.InternalServerError, "Unknown project")

        authorizeAndLocateWallet(idCard, owner.reference, internalParent.category.toId(), ActionType.SUB_ALLOCATE)
            ?: return Response.error(HttpStatusCode.Forbidden, "You are not allowed to create this sub-allocation")

        val internalChild = authorizeAndLocateWallet(IdCard.System, request.owner, internalParent.category.toId(), ActionType.READ)
            ?: return Response.error(HttpStatusCode.BadRequest, "Unknown recipient: ${request.owner}")

        val childOwner = ownersById.getValue(internalChild.ownedBy)
        if (ownerProjectInfo.canConsumeResources) {
            if (!childOwner.isProject()) {
                return Response.error(HttpStatusCode.BadRequest, "You are not allowed to sub-allocate to a personal workspace")
            }

            val childPid = idCardService.lookupPidFromProjectId(childOwner.reference)
                ?: return Response.error(HttpStatusCode.InternalServerError, "Unknown project")
            val childInfo = idCardService.lookupProjectInformation(childPid)
                ?: return Response.error(HttpStatusCode.InternalServerError, "Unknown project")

            if (childInfo.parentProject != owner.reference) {
                return Response.error(
                    HttpStatusCode.BadRequest,
                    "You are only allowed to sub-allocate to your own sub-projects"
                )
            }
        }

        if (request.start > request.end) {
            return Response.error(
                HttpStatusCode.BadRequest,
                "Start must occur before the end of an allocation ${request.start} > ${request.end}!"
            )
        }

        if (request.quota < 0) {
            return Response.error(
                HttpStatusCode.BadRequest,
                "Cannot create a root allocation with a negative quota!"
            )
        }

        return Response.ok(
            insertAllocation(internalChild, internalParent.id, request.quota, request.start, request.end)
        )
    }

    private suspend fun insertAllocation(
        wallet: InternalWallet,
        parentWalletId: Int,
        quota: Long,
        start: Long,
        end: Long,
    ): Int {
        check(start <= end)
        check(parentWalletId >= 0)
        check(quota >= 0)
        val parentWallet = walletsById[parentWalletId]
        check(parentWalletId == 0 || parentWallet != null)


        val now = Time.now()
        val id = allocationsIdAccumulator.getAndIncrement()
        val newAllocation = InternalAllocation(
            id = id,
            belongsTo = wallet.id,
            parentWallet = parentWalletId,
            quota = quota,
            start = start,
            end = end,
            retired = false,
        )

        allocations[id] = newAllocation

        val allocationGroup = wallet.allocationsByParent.getOrPut(parentWalletId) {
            InternalAllocationGroup(
                treeUsage = 0L,
                retiredTreeUsage = 0L,
                earliestExpiration = Long.MAX_VALUE,
                allocationSet = HashMap()
            )
        }

        val isActiveNow = now >= start
        allocationGroup.allocationSet[id] = isActiveNow
        if (isActiveNow && allocationGroup.earliestExpiration > end) {
            allocationGroup.earliestExpiration = end
        }

        if (parentWallet != null && isActiveNow) {
            parentWallet.totalAllocated += quota
        }

        // Rebalance excess usage
        val excessUsage = wallet.totalUsage() - wallet.totalTreeUsage()
        if (excessUsage > 0) {
            val oldLocalUsage = wallet.localUsage
            val oldOverspending = wallet.localOverspending
            val oldTreeUsage = wallet.totalTreeUsage() + excessUsage
            chargeWallet(wallet, excessUsage, true)
            val newTreeUsage = wallet.totalTreeUsage()
            val diff = newTreeUsage - oldTreeUsage
            wallet.localUsage = oldLocalUsage + diff
            wallet.localOverspending = oldOverspending - diff
            if (wallet.localUsage < 0) {
                wallet.localOverspending += wallet.localUsage
                wallet.localUsage = 0
            }
        }

        return id
    }

    private suspend fun charge(request: AccountingRequest.Charge): Response<Unit> {
        val wallet = authorizeAndLocateWallet(request.idCard, request.owner, request.category, ActionType.CHARGE)
            ?: return Response.error(HttpStatusCode.Forbidden, "Not allowed to perform this charge")
        return chargeWallet(wallet, request.amount, request.isDelta, request.scope, request.scope)
    }

    private suspend fun chargeWallet(
        wallet: InternalWallet,
        amount: Long,
        isDelta: Boolean,
        scope: String? = null,
        scopeExplanation: String? = null,
    ): Response<Unit> {
        val now = Time.now()
        val delta =
            if (isDelta) amount else amount - wallet.localUsage // TODO This is probably wrong when isDelta = false

        if (delta + wallet.localUsage + wallet.localOverspending < 0) {
            return Response.error(
                HttpStatusCode.BadRequest,
                "Refusing to process negative charge exceeding local wallet usage"
            )
        }

        val (deltaToApply, newOverspending) = balanceOverspending(delta, wallet.localOverspending)
        wallet.localOverspending = newOverspending
        if (deltaToApply == 0L) {
            return Response.ok(Unit)
        }

        // Plan charge
        val (totalChargeableAmount, chargeGraph) = run {
            val graph = buildGraph(wallet, now, true)
            val rootIndex = graph.indexInv.getValue(0)
            val maxUsable = if (deltaToApply < 0) {
                graph.minCostFlow(0, rootIndex, -deltaToApply)
            } else {
                graph.minCostFlow(rootIndex, 0, deltaToApply)
            }

            Pair(maxUsable, graph)
        }

        if (deltaToApply > 0) {
            wallet.localUsage += totalChargeableAmount
            val overExpense = deltaToApply - totalChargeableAmount
            if (overExpense > 0) {
                wallet.localOverspending += overExpense
            }
        } else {
            wallet.localUsage -= totalChargeableAmount
        }

        val walletsUpdated = HashMap<Int, Boolean>()
        walletsUpdated[wallet.id] = true

        if (totalChargeableAmount != 0L) {
            for (senderIndex in 0 until (chargeGraph.vertexCount / 2)) {
                val senderId = chargeGraph.index[senderIndex]
                for (receiverIndex in 0 until (chargeGraph.vertexCount / 2)) {
                    val amount = chargeGraph.adjacent[senderIndex][receiverIndex]
                    if (chargeGraph.original[receiverIndex][senderIndex]) {
                        val receiverId = chargeGraph.index[receiverIndex]

                        val senderWallet = walletsById.getValue(senderId)
                        val receiverWallet = walletsById[receiverId]

                        val group = senderWallet.allocationsByParent.getValue(receiverId)
                        group.treeUsage = amount
                        if (receiverWallet != null) {
                            receiverWallet.childrenUsage[senderId] = amount
                            walletsUpdated[receiverId] = true
                        }
                    }
                }
            }
        }

        for (walletId in chargeGraph.index) {
            if (walletsUpdated[walletId] != true) continue
            if (walletsById.getValue(walletId).isOverspending()) {
                return Response.error(HttpStatusCode.PaymentRequired, "$walletId is overspending")
            }
        }

        return Response.ok(Unit)
    }

    private suspend fun browseWallets(request: AccountingRequest.BrowseWallets): Response<List<WalletV2>> {
        val owner = lookupOwner(request.idCard)
            ?: return Response.error(HttpStatusCode.Forbidden, "You do not have any wallets")
        val internalOwner = ownersByReference[owner]
            ?: return Response.ok(emptyList())
        val allWallets = walletsByOwner[internalOwner.id] ?: emptyList()

        val apiWallets = allWallets.map { wallet ->
            WalletV2(
                internalOwner.toWalletOwner(),
                wallet.category,
                wallet.allocationsByParent.map { (parentWalletId, group) ->
                    val walletInfo = if (parentWalletId != 0) {
                        val parentWallet = walletsById.getValue(parentWalletId)
                        val parentOwner = ownersById.getValue(parentWallet.ownedBy)
                        val parentIsProject = parentOwner.isProject()
                        val parentPid =
                            if (parentIsProject) idCardService.lookupPidFromProjectId(parentOwner.reference)
                            else null
                        val parentProjectInfo = parentPid?.let { idCardService.lookupProjectInformation(parentPid) }

                        ParentOrChildWallet(
                            parentOwner.reference.takeIf { parentIsProject },
                            parentProjectInfo?.title ?: parentOwner.reference,
                        )
                    } else {
                        null
                    }

                    AllocationGroupWithParent(
                        walletInfo,
                        group.toApi()
                    )
                },
                if (request.includeChildren) {
                    wallet.childrenUsage.keys.map { childWalletId ->
                        val childWallet = walletsById.getValue(childWalletId)
                        val childOwner = ownersById.getValue(childWallet.ownedBy)
                        val childIsProject = childOwner.isProject()
                        val childPid =
                            if (childIsProject) idCardService.lookupPidFromProjectId(childOwner.reference)
                            else null
                        val childProjectInfo = childPid?.let { idCardService.lookupProjectInformation(childPid) }

                        val group = childWallet.allocationsByParent.getValue(wallet.id)

                        AllocationGroupWithChild(
                            ParentOrChildWallet(
                                childOwner.reference.takeIf { childIsProject },
                                childProjectInfo?.title ?: childOwner.reference,
                            ),
                            group.toApi()
                        )
                    }
                } else {
                    null
                },
                wallet.totalUsage(),
                wallet.localUsage,
                maxUsableForWallet(wallet),
                wallet.totalActiveQuota(),
                wallet.totalAllocated,
            )
        }

        return Response.ok(apiWallets)
    }

    private data class BalancedOverspending(val amount: Long, val overspending: Long)

    private fun balanceOverspending(inputAmount: Long, inputOverspending: Long): BalancedOverspending {
        var amount = inputAmount
        var overspending = inputOverspending

        if (amount < 0 && overspending > 0) {
            // reduce the charging amount from the overspending
            overspending += amount
            if (overspending >= 0) {
                // if the charge is taken in full by the overspending there is nothing else to do
                amount = 0
            } else {
                // if there is a leftover, reset overspending and proceed with the leftover charge
                amount = overspending
                overspending = 0
            }
        }
        return BalancedOverspending(amount, overspending)
    }

    private fun buildGraph(leaf: InternalWallet, now: Long, withOverAllocation: Boolean): Graph {
        val index = ArrayList<Int>()
        val indexInv = HashMap<Int, Int>()
        val queue = ArrayDeque<Int>()

        indexInv[leaf.id] = 0
        index.add(leaf.id)

        indexInv[0] = 1
        index.add(0)

        queue.add(leaf.id)

        while (queue.isNotEmpty()) {
            val wallet = walletsById.getValue(queue.removeFirst())
            for ((parentId, allocationGroup) in wallet.allocationsByParent) {
                if (parentId !in indexInv && allocationGroup.isActive()) {
                    if (parentId != 0) {
                        queue.add(parentId)
                    }
                    indexInv[parentId] = index.size
                    index.add(parentId)
                }
            }
        }

        val gSize = indexInv.size
        var graphSize = gSize
        if (withOverAllocation) graphSize *= 2
        val g = Graph.create(graphSize)
        g.index = index
        g.indexInv = indexInv

        for ((walletId, graphIndex) in indexInv) {
            if (walletId == 0) continue
            val wallet = walletsById.getValue(walletId)
            for ((parentId, allocationGroup) in wallet.allocationsByParent) {
                if (!allocationGroup.isActive()) continue

                val capacity = allocationGroup.totalActiveQuota() - allocationGroup.treeUsage
                g.addEdge(g.indexInv.getValue(parentId), graphIndex, capacity, allocationGroup.treeUsage)
                val cost = wallet.parentEdgeCost(parentId, now)
                g.addEdgeCost(g.indexInv.getValue(parentId), graphIndex, cost)
            }

            if (withOverAllocation && walletId != leaf.id) {
                var overAllocation = wallet.totalAllocated + wallet.totalRetiredAllocated + wallet.localUsage +
                        wallet.localOverspending - wallet.totalActiveQuota()

                if (!wallet.isCapacityBased()) {
                    overAllocation -= wallet.localRetiredUsage
                }

                if (overAllocation > 0) {
                    val rootIndex = g.indexInv.getValue(0)

                    var usage = wallet.totalUsage() - wallet.totalTreeUsage()
                    if (!wallet.isCapacityBased()) {
                        usage -= wallet.localRetiredUsage
                    }
                    usage = max(0, usage)

                    g.addEdge(rootIndex, gSize + graphIndex, overAllocation - usage, usage)
                    g.addEdgeCost(rootIndex, gSize + graphIndex, Long.MAX_VALUE shr 4)
                    g.addEdge(gSize + graphIndex, graphIndex, overAllocation - usage, usage)
                    g.addEdgeCost(gSize + graphIndex, graphIndex, Long.MAX_VALUE shr 4)
                }
            }
        }

        return g
    }

    private suspend fun scanRetirement(request: AccountingRequest.ScanRetirement): Response<Unit> {
        if (request.idCard != IdCard.System) return Response.error(HttpStatusCode.Forbidden, "Forbidden")

        val now = Time.now()
        for ((id, alloc) in allocations) {
            if (alloc.retired) continue
            if (now > alloc.start && now > alloc.end) {
                retireWallet(id)
            }
        }

        return Response.ok(Unit)
    }

    private suspend fun retireWallet(allocationId: Int) {
        val alloc = allocations[allocationId] ?: return
        val wallet = walletsById.getValue(alloc.belongsTo)
        val group = wallet.allocationsByParent.getValue(alloc.parentWallet)

        if (alloc.quota == 0L) {
            alloc.retired = true
            return
        }

        if (alloc.retired) return

        val toRetire = min(alloc.quota, group.treeUsage)
        alloc.retiredUsage = toRetire
        alloc.retired = true

        wallet.localRetiredUsage += toRetire
        group.retiredTreeUsage += toRetire
        group.treeUsage -= toRetire
        group.allocationSet[alloc.id] = false

        val parentWallet = walletsById[alloc.parentWallet]
        if (parentWallet != null) {
            parentWallet.childrenUsage[wallet.id] = (parentWallet.childrenUsage[wallet.id] ?: 0L) - toRetire
            parentWallet.childrenRetiredUsage[wallet.id] = (parentWallet.childrenRetiredUsage[wallet.id] ?: 0L) + toRetire
            parentWallet.totalAllocated -= alloc.quota
            parentWallet.totalRetiredAllocated += alloc.quota

            if (toRetire == 0L) return

            if (wallet.isCapacityBased()) {
                parentWallet.localUsage += toRetire
                chargeWallet(parentWallet, -toRetire, isDelta = true)

                val oldLocalUsage = wallet.localUsage
                val oldOverspending = wallet.localOverspending
                val oldTreeUsage = wallet.totalTreeUsage() + toRetire

                chargeWallet(wallet, toRetire, isDelta = true)

                val newTreeUsage = wallet.totalTreeUsage()
                val diff = newTreeUsage - oldTreeUsage

                wallet.localUsage = oldLocalUsage + diff
                wallet.localOverspending = oldOverspending - diff
                if (wallet.localUsage < 0) {
                    wallet.localOverspending += wallet.localUsage
                    wallet.localUsage = 0
                }
            }
        }
    }

    private suspend fun lookupOwner(idCard: IdCard): String? {
        if (idCard !is IdCard.User) return null
        if (idCard.activeProject != 0) {
            return idCardService.lookupPid(idCard.activeProject)
        } else {
            return idCardService.lookupUid(idCard.uid)
        }
    }

    private suspend fun maxUsable(request: AccountingRequest.MaxUsable): Response<Long> {
        val owner = lookupOwner(request.idCard) ?: return Response.error(HttpStatusCode.Forbidden, "You do not have any wallets")
        val wallet = authorizeAndLocateWallet(request.idCard, owner, request.category, ActionType.READ)
            ?: return Response.error(HttpStatusCode.Forbidden, "You do not have any wallets")
        return Response.ok(maxUsableForWallet(wallet))
    }

    private fun maxUsableForWallet(wallet: InternalWallet): Long {
        val graph = buildGraph(wallet, Time.now(), false)
        val rootIndex = graph.indexInv[0] ?: return 0
        return graph.maxFlow(rootIndex, 0)
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
