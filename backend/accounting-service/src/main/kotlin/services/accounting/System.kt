package dk.sdu.cloud.accounting.services.accounting

import com.google.common.primitives.Longs.min
import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.util.IIdCardService
import dk.sdu.cloud.accounting.util.IProductCache
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.findAllFreeProducts
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.*
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
import kotlinx.serialization.Serializable
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
    private val distributedState: DistributedStateFactory,
    private val addressToSelf: String,
) {
    // Active processor
    // =================================================================================================================
    @Serializable
    private data class ActiveProcessor(val address: String)

    private val activeProcessor =
        distributedState.create(ActiveProcessor.serializer(), "accounting-active-processor", 60_000)

    suspend fun retrieveActiveProcessor(): String? {
        if (isActiveProcessor.get()) {
            return null
        }
        return activeProcessor.get()?.address
    }

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
        activeProcessor.set(ActiveProcessor(addressToSelf))

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
                                    is AccountingRequest.UpdateAllocation -> updateAllocation(msg)
                                    is AccountingRequest.RetrieveProviderAllocations -> retrieveProviderAllocations(msg)
                                    is AccountingRequest.FindRelevantProviders -> findRelevantProviders(msg)
                                    is AccountingRequest.SystemCharge -> systemCharge(msg)
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
                                response = Response.error(
                                    HttpStatusCode.InternalServerError,
                                    e.toReadableStacktrace().toString()
                                )
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
        WALLET_ADMIN,
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
        val newOwner = InternalOwner(newId, owner, true)
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

        //Permission check
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

            ActionType.WALLET_ADMIN -> {
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

        //return existing wallet or create new wallet
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
                totalAllocated = 0L,
                totalRetiredAllocated = 0L,
                excessUsage = 0L,
                isDirty = true
            ).also {
                wallets.add(it)
                walletsById[id] = it
            }
        }

        toCheck.add(wallet.id)
        return wallet
    }

    private suspend fun rootAllocate(request: AccountingRequest.RootAllocate): Response<Int> {
        val now = Time.now()
        val idCard = request.idCard
        //Check that we only give root allocations to Root projects
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

        return Response.ok(
            insertAllocation(
                now,
                wallet,
                parentWalletId = 0,
                request.amount,
                request.start,
                request.end,
                grantedIn = null
            )
        )
    }

    private suspend fun subAllocate(request: AccountingRequest.SubAllocate): Response<Int> {
        val now = Time.now()
        val idCard = request.idCard
        val parentOwner = lookupOwner(idCard)
            ?: return Response.error(HttpStatusCode.Forbidden, "Could not find information about your project")
        val internalParentWallet = authorizeAndLocateWallet(idCard, parentOwner, request.category, ActionType.READ)
            ?: return Response.error(HttpStatusCode.Forbidden, "You are not allowed to create this sub-allocation")

        toCheck.add(internalParentWallet.id)

        val owner = ownersById.getValue(internalParentWallet.ownedBy)
        if (!owner.isProject()) {
            return Response.error(HttpStatusCode.Forbidden, "Only projects are allowed to create sub-allocations")
        }

        val ownerPid = idCardService.lookupPidFromProjectId(owner.reference)
            ?: return Response.error(HttpStatusCode.InternalServerError, "Unknown project")
        val ownerProjectInfo = idCardService.lookupProjectInformation(ownerPid)
            ?: return Response.error(HttpStatusCode.InternalServerError, "Unknown project")

        authorizeAndLocateWallet(idCard, owner.reference, internalParentWallet.category.toId(), ActionType.WALLET_ADMIN)
            ?: return Response.error(HttpStatusCode.Forbidden, "You are not allowed to create this sub-allocation")

        val internalChild =
            authorizeAndLocateWallet(IdCard.System, request.owner, internalParentWallet.category.toId(), ActionType.READ)
                ?: return Response.error(HttpStatusCode.BadRequest, "Unknown recipient: ${request.owner}")

        val childOwner = ownersById.getValue(internalChild.ownedBy)
        if (ownerProjectInfo.canConsumeResources) {
            if (!childOwner.isProject()) {
                return Response.error(
                    HttpStatusCode.BadRequest,
                    "You are not allowed to sub-allocate to a personal workspace"
                )
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
                "Cannot create an allocation with a negative quota!"
            )
        }

        return Response.ok(
            insertAllocation(now, internalChild, internalParentWallet.id, request.quota, request.start, request.end, null)
        )
    }

    private suspend fun insertAllocation(
        now: Long,
        wallet: InternalWallet,
        parentWalletId: Int,
        quota: Long,
        start: Long,
        end: Long,
        grantedIn: Long?
    ): Int {
        check(start <= end)
        check(parentWalletId >= 0)
        check(quota >= 0)
        val parentWallet = walletsById[parentWalletId]
        check(parentWalletId == 0 || parentWallet != null)

        val allocationId = allocationsIdAccumulator.getAndIncrement()
        val newAllocation = InternalAllocation(
            id = allocationId,
            belongsTo = wallet.id,
            parentWallet = parentWalletId,
            quota = quota,
            start = start,
            end = end,
            retired = false,
            grantedIn = grantedIn,
            isDirty = true
        )

        allocations[allocationId] = newAllocation

        val allocationGroup = wallet.allocationsByParent.getOrPut(parentWalletId) {
            val groupId = allocationGroupIdAccumulator.getAndIncrement()
            InternalAllocationGroup(
                id = groupId,
                associatedWallet = wallet.id,
                parentWallet = parentWalletId,
                treeUsage = 0L,
                retiredTreeUsage = 0L,
                earliestExpiration = Long.MAX_VALUE,
                allocationSet = HashMap(),
                isDirty = true
            )
        }

        val isActiveNow = now >= start
        allocationGroup.allocationSet[allocationId] = isActiveNow

        if (isActiveNow && allocationGroup.earliestExpiration > end) {
            allocationGroup.earliestExpiration = end
            allocationGroup.isDirty = true
        }

        if (parentWallet != null && isActiveNow) {
            parentWallet.totalAllocated += quota
            parentWallet.isDirty = true
        }

        // Rebalance excess usage
        if (wallet.excessUsage > 0) {
            val amount = wallet.excessUsage
            wallet.localUsage -= amount
            internalCharge(wallet, amount, now)
            wallet.localUsage += amount
            wallet.isDirty
        }

        return allocationId
    }

    private data class InternalChargeResult(val chargedAmount: Long, val error: String?)

    private fun internalCharge(wallet: InternalWallet, totalAmount: Long, now: Long): InternalChargeResult {
        // Plan charge
        val (totalChargeableAmount, chargeGraph) = run {
            val graph = buildGraph(wallet, now, true)
            val rootIndex = graph.indexInv.getValue(0)
            val maxUsable = if (totalAmount < 0) {
                graph.minCostFlow(0, rootIndex, -totalAmount)
            } else {
                graph.minCostFlow(rootIndex, 0, totalAmount)
            }

            Pair(maxUsable, graph)
        }

        val walletsUpdated = HashMap<Int, Boolean>()
        walletsUpdated[wallet.id] = true

        if (totalChargeableAmount != 0L) {
            val gSize = chargeGraph.vertexCount / 2
            for (senderIndex in 0 until (chargeGraph.vertexCount / 2)) {
                val senderId = chargeGraph.index[senderIndex]
                val senderWallet = walletsById[senderId]
                if (senderWallet != null) {
                    senderWallet.excessUsage = chargeGraph.adjacent[senderIndex][senderIndex + gSize]
                    senderWallet.isDirty = true
                    walletsUpdated[senderId] = true
                }

                for (receiverIndex in 0 until (chargeGraph.vertexCount / 2)) {
                    val amount = chargeGraph.adjacent[senderIndex][receiverIndex]
                    if (chargeGraph.original[receiverIndex][senderIndex]) {
                        val receiverId = chargeGraph.index[receiverIndex]
                        val receiverWallet = walletsById[receiverId]

                        if (senderWallet != null) {
                            val group = senderWallet.allocationsByParent.getValue(receiverId)
                            group.treeUsage = amount
                            group.isDirty = true
                        }

                        if (receiverWallet != null) {
                            receiverWallet.childrenUsage[senderId] = amount
                            receiverWallet.isDirty = true
                        }
                    }
                }
            }
        }
        for (walletId in chargeGraph.index) {
            if (walletsUpdated[walletId] != true) continue
            if (walletsById.getValue(walletId).isOverspending()) {
                return InternalChargeResult(totalChargeableAmount, "$walletId is overspending")
            }
        }

        return InternalChargeResult(totalChargeableAmount, null)
    }

    private suspend fun charge(request: AccountingRequest.Charge): Response<Unit> {
        val wallet = authorizeAndLocateWallet(request.idCard, request.owner, request.category, ActionType.CHARGE)
            ?: return Response.error(HttpStatusCode.Forbidden, "Not allowed to perform this charge")
        return chargeWallet(wallet, request.amount, request.isDelta, request.scope, request.scope)
    }

    private suspend fun systemCharge(request: AccountingRequest.SystemCharge): Response<Unit> {
        return chargeWallet(
            wallet = walletsById[request.walletId.toInt()]!!,
            amount = request.amount,
            isDelta = true,
        )
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

        if (delta + wallet.localUsage < 0) {
            return Response.error(
                HttpStatusCode.BadRequest,
                "Refusing to process negative charge exceeding local wallet usage"
            )
        }

        val (totalChargeableAmount, error) = internalCharge(wallet, delta, now)
        if (delta > 0) {
            wallet.localUsage += totalChargeableAmount
            wallet.excessUsage += delta - totalChargeableAmount
        } else {
            wallet.localUsage -= totalChargeableAmount
        }

        wallet.isDirty = true

        if (error != null) return Response.error(HttpStatusCode.PaymentRequired, error)
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

    private suspend fun updateAllocation(request: AccountingRequest.UpdateAllocation): Response<Unit> {
        val now = Time.now()
        val internalAllocation = allocations[request.allocationId]
            ?: return Response.error(HttpStatusCode.NotFound, "Unknown allocation")
        val internalWallet = walletsById[internalAllocation.belongsTo]
            ?: return Response.error(HttpStatusCode.NotFound, "Unknown wallet (bad internal state?)")
        val internalOwner = ownersById[internalWallet.ownedBy]
            ?: return Response.error(HttpStatusCode.NotFound, "Unknown wallet owner (bad internal state?)")
        val allocationGroup = internalWallet.allocationsByParent[internalAllocation.parentWallet]
            ?: return Response.error(HttpStatusCode.NotFound, "Unknown allocation group (bad internal state?)")

        authorizeAndLocateWallet(
            request.idCard,
            internalOwner.reference,
            internalWallet.category.toId(),
            ActionType.WALLET_ADMIN
        ) ?: return Response.error(HttpStatusCode.Forbidden, "You are not allowed to update this allocation.")

        if (internalAllocation.retired) {
            return Response.error(
                HttpStatusCode.Forbidden,
                "You cannot update a retired allocation, it has already expired!"
            )
        }

        if (internalAllocation.start > now && request.newStart != null) {
            return Response.error(
                HttpStatusCode.Forbidden,
                "You cannot change the starting time of an allocation which has already started",
            )
        }

        val proposeNewStart = request.newStart ?: internalAllocation.start
        val proposedNewEnd = request.newEnd ?: internalAllocation.end
        if (proposeNewStart > proposedNewEnd) {
            return Response.error(
                HttpStatusCode.Forbidden,
                "This update would make the allocation invalid. An allocation cannot start after it has ended."
            )
        }

        if (request.newQuota != null && request.newQuota < 0) {
            return Response.error(
                HttpStatusCode.Forbidden,
                "You cannot set a negative quota for an allocation (${request.newQuota})"
            )
        }

        if (request.newQuota != null) {
            val delta = internalAllocation.quota - request.newQuota

            val activeQuota = allocationGroup.totalActiveQuota()
            val activeUsage = allocationGroup.treeUsage

            if (activeQuota + delta < activeUsage) {
                return Response.error(
                    HttpStatusCode.Forbidden,
                    "You cannot decrease the quota below the current usage!"
                )
            }
        }

        internalAllocation.start = proposeNewStart
        internalAllocation.end = proposedNewEnd
        internalAllocation.quota = request.newQuota ?: internalAllocation.quota
        internalAllocation.isDirty =  true

        return Response.ok(Unit)
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

            if (withOverAllocation) {
                var overAllocation = wallet.totalAllocated + wallet.totalRetiredAllocated + wallet.localUsage +
                        -wallet.totalActiveQuota()

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
                retireAllocation(id)
            }
        }

        return Response.ok(Unit)
    }

    private suspend fun retireAllocation(allocationId: Int) {
        val alloc = allocations[allocationId] ?: return
        val wallet = walletsById.getValue(alloc.belongsTo)
        val group = wallet.allocationsByParent.getValue(alloc.parentWallet)

        if (alloc.retired) return

        if (alloc.quota == 0L) {
            alloc.retired = true
            return
        }

        val toRetire = min(alloc.quota, group.treeUsage)
        alloc.retiredUsage = toRetire
        alloc.retired = true
        alloc.isDirty = true

        wallet.localRetiredUsage += toRetire
        wallet.isDirty = true

        group.retiredTreeUsage += toRetire
        group.treeUsage -= toRetire
        group.allocationSet[alloc.id] = false
        group.isDirty = true

        val parentWallet = walletsById[alloc.parentWallet]
        if (parentWallet != null) {
            parentWallet.childrenUsage[wallet.id] = (parentWallet.childrenUsage[wallet.id] ?: 0L) - toRetire
            parentWallet.childrenRetiredUsage[wallet.id] =
                (parentWallet.childrenRetiredUsage[wallet.id] ?: 0L) + toRetire
            parentWallet.totalAllocated -= alloc.quota
            parentWallet.totalRetiredAllocated += alloc.quota
            parentWallet.isDirty = true
        }


        if (toRetire == 0L) return

        if (wallet.isCapacityBased()) {
            if (parentWallet != null) {
                parentWallet.localUsage += toRetire
                chargeWallet(parentWallet, -toRetire, isDelta = true)
            }

            wallet.localUsage -= toRetire
            chargeWallet(wallet, toRetire, true)
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
        val owner = lookupOwner(request.idCard) ?: return Response.error(
            HttpStatusCode.Forbidden,
            "You do not have any wallets"
        )
        val wallet = authorizeAndLocateWallet(request.idCard, owner, request.category, ActionType.READ)
            ?: return Response.error(HttpStatusCode.Forbidden, "You do not have any wallets")
        return Response.ok(maxUsableForWallet(wallet))
    }

    private fun maxUsableForWallet(wallet: InternalWallet): Long {
        val graph = buildGraph(wallet, Time.now(), false)
        val rootIndex = graph.indexInv[0] ?: return 0
        return graph.maxFlow(rootIndex, 0)
    }

    private fun retrieveProviderAllocations(
        request: AccountingRequest.RetrieveProviderAllocations
    ): Response<PageV2<AccountingV2.BrowseProviderAllocations.ResponseItem>> {
        val idCard = request.idCard
        if (idCard !is IdCard.Provider) return Response.error(HttpStatusCode.Forbidden, "You are not a provider")

        // Format for pagination tokens: $walletId/$allocationId.
        // When specified we will find anything in wallets with id >= walletId.
        // When $walletId matches then the allocations are also required to be > allocationId.
        val nextTokens = request.next?.split("/")

        val minimumWalletId = nextTokens?.getOrNull(0)?.toIntOrNull() ?: 0
        val minimumAllocationId = nextTokens?.getOrNull(1)?.toIntOrNull() ?: 0

        val relevantWallets = walletsById
            .values
            .asSequence()
            .filter { it.category.provider == idCard.name }
            .filter { it.id >= minimumWalletId }
            .filter { request.filterCategory == null || request.filterCategory == it.category.name }
            .filter { request.filterOwnerId == null || request.filterOwnerId == ownersById[it.ownedBy]?.reference }
            .filter { request.filterOwnerIsProject != true || ownersById[it.ownedBy]?.isProject() == true }
            .filter { request.filterOwnerIsProject != false || ownersById[it.ownedBy]?.isProject() == false }
            .sortedBy { it.id }
            .associateBy { it.id }

        val relevantWalletIds = relevantWallets.keys

        val relevantAllocations = allocations
            .values
            .asSequence()
            .filter { it.belongsTo in relevantWalletIds }
            .filter { it.belongsTo != minimumWalletId || it.id > minimumAllocationId }
            .filter { !it.retired }
            .sortedWith(Comparator.comparingInt<InternalAllocation?> { it.belongsTo }.thenComparingInt { it.id })
            .take(request.itemsPerPage ?: 250)
            .map {
                val wallet = relevantWallets.getValue(it.belongsTo)
                AccountingV2.BrowseProviderAllocations.ResponseItem(
                    it.id.toString(),
                    ownersById.getValue(wallet.ownedBy).toWalletOwner(),
                    wallet.category,
                    it.start,
                    it.end,
                    it.quota
                )
            }
            .toList()

        val lastAllocation = relevantAllocations.lastOrNull()
        val newNextToken = if (lastAllocation != null && relevantAllocations.size < (request.itemsPerPage ?: 250)) {
            val allocId = lastAllocation.id.toInt()
            val walletId = allocations.getValue(allocId).belongsTo
            "$walletId/$allocId"
        } else {
            null
        }

        return Response.ok(
            PageV2(
                request.itemsPerPage ?: 250,
                relevantAllocations,
                newNextToken
            )
        )
    }

    private suspend fun findRelevantProviders(
        request: AccountingRequest.FindRelevantProviders,
    ): Response<Set<String>> {
        val idCard = idCardService.fetchIdCard(
            ActorAndProject(Actor.SystemOnBehalfOfUser(request.username), request.project)
        )

        if (idCard !is IdCard.User) {
            return Response.error(
                HttpStatusCode.Forbidden,
                "You are not a user! There are no relevant providers for you."
            )
        }

        val username = idCardService.lookupUid(idCard.uid)
            ?: return Response.error(HttpStatusCode.InternalServerError, "Could not find user info")

        val allWorkspaces = if (!request.useProject) {
            val projectsUserIsPartOf = idCard.groups.toList().mapNotNull { gid ->
                idCardService.lookupGid(gid)?.projectId
            }.toSet()

            projectsUserIsPartOf + setOf(username)
        } else {
            if (idCard.activeProject == 0) {
                setOf(username)
            } else {
                setOf(idCardService.lookupPid(idCard.activeProject)!!)
            }
        }

        val freeProviders = productCache.products().findAllFreeProducts()
            .filter { request.filterProductType == null || it.productType == request.filterProductType }
            .map { it.category.provider }
            .toSet()

        val providers = allWorkspaces
            .flatMap { projectId ->
                val owner = ownersByReference[projectId] ?: return@flatMap emptyList()
                (walletsByOwner[owner.id] ?: emptyList()).map { it.category.provider }
            }
            .toSet()

        return Response.ok(providers + freeProviders)
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
