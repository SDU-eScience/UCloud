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
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.random.Random

const val DEBUG = true

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

                            persistence.flushChanges()
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
                    log.info("Error happened when attempting to process messages service: ${ex.toReadableStacktrace()}")
                }

                if (isActiveProcessor.get()) {
                    delay(15000 + Random.nextLong(5000))
                }
            }
        }
    }

    private val toCheck = ArrayList<Int>()
    private var nextRetirementScan = 0L

    private suspend fun processMessages(lock: DistributedLock) {
        val didAcquire = disableMasterElection || lock.acquire()
        if (!didAcquire) return

        // Resetting state, so we do not attempt to load into already existing in-mem DB resulting in conflicts
        // resetState()

        log.info("This service has become the master responsible for handling Accounting processor events!")
        activeProcessor.set(ActiveProcessor(addressToSelf))

        persistence.initialize()
        GlobalScope.launch {
            delay(1000)
            persistence.loadOldData(this@AccountingSystem)

            sendRequestNoUnwrap(AccountingRequest.DebugUsable(IdCard.System))
        }

        while (currentCoroutineContext().isActive && isActiveProcessor.get()) {
            try {
                turnstile.withLock {
                    select {
                        requests.onReceive { request ->
                            // NOTE(Dan): We attempt a synchronization here in case we receive so many requests that the
                            // timeout is never triggered.
                            processPeriodicTasks()

                            toCheck.clear()
                            val timeoutTime = 1000L
                            var response = try {
                                withTimeout(timeoutTime) {
                                    when (val msg = request.message) {
                                        is AccountingRequest.Charge -> charge(msg)
                                        is AccountingRequest.RootAllocate -> rootAllocate(msg)
                                        is AccountingRequest.SubAllocate -> subAllocate(msg)
                                        is AccountingRequest.ScanRetirement -> scanRetirement(msg)
                                        is AccountingRequest.MaxUsable -> maxUsable(msg)
                                        is AccountingRequest.BrowseWallets -> browseWallets(msg)
                                        is AccountingRequest.UpdateAllocation -> updateAllocation(msg)
                                        is AccountingRequest.RetrieveProviderAllocations -> retrieveProviderAllocations(
                                            msg
                                        )

                                        is AccountingRequest.FindRelevantProviders -> findRelevantProviders(msg)
                                        is AccountingRequest.SystemCharge -> systemCharge(msg)
                                        is AccountingRequest.ProviderCheckUsable -> providerCheckUsable(msg)
                                        is AccountingRequest.ForEachUpdatedWallet -> forEachUpdatedWallet(msg)
                                        is AccountingRequest.StopSystem -> {
                                            isActiveProcessor.set(false)
                                            Response.ok(Unit)
                                        }

                                        is AccountingRequest.RetrieveDescendants -> retrieveDescendants(msg)
                                        is AccountingRequest.DebugState -> {
                                            if (msg.idCard != IdCard.System) {
                                                Response.error(HttpStatusCode.Forbidden, "Forbidden")
                                            } else {
                                                Response.ok(produceMermaidGraph(msg.roots))
                                            }
                                        }

                                        is AccountingRequest.DebugUsable -> {
                                            if (msg.idCard != IdCard.System) {
                                                Response.error(HttpStatusCode.Forbidden, "Forbidden")
                                            } else {
                                                println("Dumping max usable")
                                                val out = FileWriter("/tmp/max_usable.csv")
                                                val writer = PrintWriter(out)
                                                writer.println("WalletId,MaxUsable")
                                                for ((walletId, wallet) in walletsById) {
                                                    writer.println("$walletId,${maxUsableForWallet(wallet)}")
                                                }

                                                writer.close()
                                                out.close()
                                                println("OK")

                                                Response.ok(Unit)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Throwable) {
                                if (e is TimeoutCancellationException) {
                                    log.warn("request: $request took more than $timeoutTime ms")
                                    Response.error(
                                        HttpStatusCode.RequestTimeout,
                                        "Request took to long"
                                    )
                                } else {
                                    Response.error(
                                        HttpStatusCode.InternalServerError,
                                        e.toReadableStacktrace().toString()
                                    )
                                }
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
                            processPeriodicTasks()
                        }
                    }
                    if (!renewLock(lock)) {
                        isActiveProcessor.set(false)
                    }
                }
            } catch (ex: Throwable) {
                log.info(ex.toReadableStacktrace().toString())
            }
        }
    }

    private suspend fun processPeriodicTasks() {
        withTimeout(15_000) {
            persistence.flushChanges()
        }

        val now = Time.now()
        if (now > nextRetirementScan) {
            scanRetirement(AccountingRequest.ScanRetirement(IdCard.System))
            nextRetirementScan = now + 60_000L
        }
    }

    private suspend fun renewLock(lock: DistributedLock): Boolean {
        if (!disableMasterElection) {
            if (!lock.renew(90_000)) {
                log.warn("Lock was lost")
                isActiveProcessor.set(false)
                return false
            }
            activeProcessor.set(ActiveProcessor(addressToSelf))
        }
        return true
    }

    private enum class ActionType {
        PROVIDER_ACTION,
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

            ActionType.PROVIDER_ACTION -> {
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
                isDirty = true,
                wasLocked = false,
                lastSignificantUpdateAt = Time.now(),
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
            return Response.error(HttpStatusCode.Forbidden, "You are not allowed to create a root allocation! (E1 I=${idCard})")
        }

        if (idCard.activeProject == 0) {
            return Response.error(HttpStatusCode.Forbidden, "Cannot perform a root allocation to a personal workspace!")
        }

        val projectId = idCardService.lookupPid(idCard.activeProject)
            ?: return Response.error(HttpStatusCode.InternalServerError, "Could not lookup project from id card")

        val wallet = authorizeAndLocateWallet(request.idCard, projectId, request.category, ActionType.ROOT_ALLOCATE)
            ?: return Response.error(HttpStatusCode.Forbidden, "You are not allowed to create a root allocation! (E2 U=${idCard.uid} P=${idCard.activeProject} AO=${idCard.adminOf.toList()} G=${idCard.groups.toList()})")

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
        val parentOwner = request.ownerOverride ?: lookupOwner(idCard)
        ?: return Response.error(HttpStatusCode.Forbidden, "Could not find information about your project (${idCard})")
        val internalParentWallet = authorizeAndLocateWallet(idCard, parentOwner, request.category, ActionType.READ)
            ?: return Response.error(HttpStatusCode.Forbidden, "You are not allowed to create this sub-allocation")

        if (request.ownerOverride != null && idCard != IdCard.System) {
            return Response.error(HttpStatusCode.Forbidden, "You are not allowed to bypass ownership check!")
        }

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
            authorizeAndLocateWallet(
                IdCard.System,
                request.owner,
                internalParentWallet.category.toId(),
                ActionType.READ
            )
                ?: return Response.error(HttpStatusCode.BadRequest, "Unknown recipient: ${request.owner}")

        val childOwner = ownersById.getValue(internalChild.ownedBy)
        if (ownerProjectInfo.canConsumeResources) {
            if (childOwner.isProject()) {
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
            insertAllocation(
                now, internalChild, internalParentWallet.id, request.quota, request.start, request.end,
                request.grantedIn
            )
        )
    }

    private fun insertAllocation(
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
            belongsToWallet = wallet.id,
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
            ).also { allocationGroups[groupId] = it }
        }
        allocationGroup.isDirty = true

        allocationGroups[allocationGroup.id] = allocationGroup

        val isActiveNow = now >= start
        allocationGroup.allocationSet[allocationId] = isActiveNow

        if (isActiveNow && allocationGroup.earliestExpiration > end) {
            allocationGroup.earliestExpiration = end
            allocationGroup.isDirty = true
        }

        if (parentWallet != null) {
            parentWallet.isDirty = true
            if (isActiveNow) parentWallet.totalAllocated += quota

            // NOTE(Dan): Insert a childrenUsage entry if we don't already have one. This is required to make
            // childrenUsage a valid tool for looking up children in the parent wallet.
            parentWallet.childrenUsage[wallet.id] = parentWallet.childrenUsage[wallet.id] ?: 0L
        }

        // Re-balance excess usage
        if (wallet.excessUsage > 0) {
            val amount = wallet.excessUsage
            wallet.localUsage -= amount
            val (chargedAmount) = internalCharge(wallet, amount, now)
            wallet.localUsage += amount
            wallet.excessUsage = amount - chargedAmount
        }

        markSignificantUpdate(wallet, now)
        reevaluateWalletsAfterUpdate(wallet)

        return allocationId
    }

    private fun markSignificantUpdate(wallet: InternalWallet, now: Long) {
        mostRecentSignificantUpdateByProvider[wallet.category.provider] = now
        wallet.lastSignificantUpdateAt = now
        wallet.isDirty = true
    }

    private data class InternalChargeResult(
        val chargedAmount: Long,
        val updatedWallets: Collection<Int>
    )

    private fun internalCharge(wallet: InternalWallet, totalAmount: Long, now: Long): InternalChargeResult {
        // Plan charge
        val (totalChargeableAmount, chargeGraph) = run {
            val graph = buildGraph(wallet, now, true)
            debug(wallet) {
                buildString {
                    appendLine("# `internalCharge(${wallet.id}, $totalAmount)` (Before)")
                    appendLine("```mermaid")
                    appendLine(graph.toMermaid())
                    appendLine("```")
                }
            }
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

        val visitedWallets = HashSet<Int>()
        for (walletId in chargeGraph.index) {
            if (walletsUpdated[walletId] != true) continue
            visitedWallets.add(walletId)
        }

        debug(wallet) {
            buildString {
                appendLine("# `internalCharge(${wallet.id}, $totalAmount)` (After)")
                appendLine("```mermaid")
                appendLine(chargeGraph.toMermaid())
                appendLine("```")
            }
        }

        return InternalChargeResult(totalChargeableAmount, visitedWallets)
    }

    private suspend fun charge(request: AccountingRequest.Charge): Response<Unit> {
        val wallet =
            authorizeAndLocateWallet(request.idCard, request.owner, request.category, ActionType.PROVIDER_ACTION)
                ?: return Response.error(HttpStatusCode.Forbidden, "Not allowed to perform this charge")
        return chargeWallet(wallet, request.amount, request.isDelta, request.scope, request.scope)
    }

    private fun systemCharge(request: AccountingRequest.SystemCharge): Response<Unit> {
        val wallet = walletsById[request.walletId.toInt()]
            ?: return Response.error(HttpStatusCode.NotFound, "Unknown wallet: ${request.walletId}")

        return chargeWallet(
            wallet = wallet,
            amount = request.amount,
            isDelta = request.isDelta,
        )
    }

    private suspend fun providerCheckUsable(request: AccountingRequest.ProviderCheckUsable): Response<Long> {
        val internalParentWallet = authorizeAndLocateWallet(
            request.idCard,
            request.owner,
            request.category,
            ActionType.PROVIDER_ACTION
        ) ?: return Response.error(
            HttpStatusCode.Forbidden,
            "You are not allowed to check the usable balance of this wallet. Are you sure you own this product?"
        )

        return Response.ok(maxUsableForWallet(internalParentWallet))
    }

    private suspend fun forEachUpdatedWallet(request: AccountingRequest.ForEachUpdatedWallet): Response<Unit> {
        if (request.idCard != IdCard.System) {
            return Response.error(HttpStatusCode.Forbidden, "Only callable by the system")
        }

        val mostRecent = mostRecentSignificantUpdateByProvider[request.providerId] ?: -1
        if (request.since >= mostRecent) return Response.ok(Unit)

        for ((_, wallet) in walletsById) {
            if (wallet.category.provider != request.providerId) continue
            if (wallet.allocationsByParent.isEmpty()) continue

            if (wallet.lastSignificantUpdateAt > request.since) {
                request.handler(wallet)
            }
        }

        return Response.ok(Unit)
    }

    private fun chargeWallet(
        wallet: InternalWallet,
        amount: Long,
        isDelta: Boolean,
        scope: String? = null,
        scopeExplanation: String? = null,
    ): Response<Unit> {
        debug(wallet) {
            buildString {
                appendLine("# `chargeWallet(${wallet.id}, $amount, isDelta=$isDelta, scope=$scope)` (Before)")
                appendLine("```mermaid")
                appendLine(produceMermaidGraph(listOf(wallet.id)))
                appendLine("```")
            }
        }

        val now = Time.now()

        val currentUsage = if (scope == null) {
            wallet.localUsage
        } else {
            scopedUsage[scopeKey(wallet, scope)] ?: 0L
        }

        val delta = if (isDelta) amount else amount - currentUsage

        if (delta + wallet.localUsage < 0) {
            return Response.error(
                HttpStatusCode.BadRequest,
                "Refusing to process negative charge exceeding local wallet usage"
            )
        }

        if (scope != null) {
            val scopeKey = scopeKey(wallet, scope)
            scopedUsage[scopeKey] = currentUsage + delta
            scopedDirty[scopeKey] = true
        }

        val (totalChargeableAmount, visitedWallets) = internalCharge(wallet, delta, now)
        if (delta > 0) {
            wallet.localUsage += delta
            wallet.excessUsage += delta - totalChargeableAmount
        } else {
            wallet.localUsage -= totalChargeableAmount
        }

        wallet.isDirty = true

        val overSpendingWallets = ArrayList<Int>()
        for (walletId in visitedWallets) {
            val visitedWallet = walletsById.getValue(walletId)
            if (visitedWallet.isOverspending()) {
                overSpendingWallets.add(walletId)
                if (!visitedWallet.wasLocked) {
                    reevaluateWalletsAfterUpdate(visitedWallet)
                }
            } else {
                if (visitedWallet.wasLocked) {
                    reevaluateWalletsAfterUpdate(visitedWallet)
                }
            }
        }

        var error: String? = null
        if (overSpendingWallets.isNotEmpty()) {
            error = "${overSpendingWallets.take(10).joinToString(", ")} is overspending"
        }

        debug(wallet) {
            buildString {
                appendLine("# `chargeWallet(${wallet.id}, $amount, isDelta=$isDelta, scope=$scope)` (After)")
                appendLine("```mermaid")
                appendLine(produceMermaidGraph(listOf(wallet.id)))
                appendLine("```")
            }
        }

        if (error != null) return Response.error(HttpStatusCode.PaymentRequired, error)
        return Response.ok(Unit)
    }

    private suspend fun browseWallets(request: AccountingRequest.BrowseWallets): Response<List<WalletV2>> {
        val owner = lookupOwner(request.idCard)
            ?: return Response.ok(emptyList())
        val internalOwner = ownersByReference[owner]
            ?: return Response.ok(emptyList())
        val allWallets = walletsByOwner[internalOwner.id] ?: emptyList()

        val apiWallets = allWallets.mapNotNull { wallet ->
            if (wallet.allocationsByParent.isEmpty()) return@mapNotNull null

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
                wallet.lastSignificantUpdateAt,
            )
        }

        return Response.ok(apiWallets)
    }

    private suspend fun updateAllocation(request: AccountingRequest.UpdateAllocation): Response<Unit> {
        val now = Time.now()
        val internalAllocation = allocations[request.allocationId]
            ?: return Response.error(HttpStatusCode.NotFound, "Unknown allocation")
        val internalWallet = walletsById[internalAllocation.belongsToWallet]
            ?: return Response.error(HttpStatusCode.NotFound, "Unknown wallet (bad internal state?)")
        val allocationGroup = internalWallet.allocationsByParent[internalAllocation.parentWallet]
            ?: return Response.error(HttpStatusCode.NotFound, "Unknown allocation group (bad internal state?)")
        val parentWallet = walletsById[internalAllocation.parentWallet]
            ?: return Response.error(
                HttpStatusCode.Forbidden,
                "You are not allowed to update this allocation (no parent)."
            )
        val parentOwner = ownersById[parentWallet.ownedBy]
            ?: return Response.error(HttpStatusCode.NotFound, "Unknown parent owner (bad internal state?)")

        authorizeAndLocateWallet(
            request.idCard,
            parentOwner.reference,
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
            val delta = request.newQuota - internalAllocation.quota

            val activeQuota = allocationGroup.totalActiveQuota()
            val activeUsage = allocationGroup.treeUsage

            if (activeQuota + delta < activeUsage) {
                return Response.error(
                    HttpStatusCode.Forbidden,
                    "You cannot decrease the quota below the current usage!"
                )
            }
        }

        internalAllocation.isDirty = true

        internalAllocation.start = proposeNewStart
        internalAllocation.end = proposedNewEnd
        val oldQuota = internalAllocation.quota
        internalAllocation.quota = request.newQuota ?: internalAllocation.quota
        val quotaDiff = internalAllocation.quota - oldQuota
        if (quotaDiff != 0L) {
            val parent = internalAllocation.parentWallet
            if (parent != 0) {
                val parentWallet = walletsById.getValue(parent)
                parentWallet.totalAllocated += quotaDiff
                parentWallet.isDirty = true
            }
        }

        markSignificantUpdate(internalWallet, now)
        reevaluateWalletsAfterUpdate(internalWallet)

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
                g.addEdgeCost(g.indexInv.getValue(parentId), graphIndex, BigInteger.valueOf(cost))
            }

            if (withOverAllocation) {
                var overAllocation = wallet.totalAllocated + wallet.totalRetiredAllocated + wallet.localUsage -
                        wallet.totalActiveQuota()

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
                    g.addEdgeCost(rootIndex, gSize + graphIndex, MANDATORY_EDGE_COST)
                    g.addEdge(gSize + graphIndex, graphIndex, overAllocation - usage, usage)
                    g.addEdgeCost(gSize + graphIndex, graphIndex, MANDATORY_EDGE_COST)
                }
            }
        }

        return g
    }

    private fun scanRetirement(request: AccountingRequest.ScanRetirement): Response<Unit> {
        if (request.idCard != IdCard.System) return Response.error(HttpStatusCode.Forbidden, "Forbidden")

        val now = Time.now()
        for ((id, alloc) in allocations) {
            if (alloc.retired) continue
            if (now > alloc.start && now > alloc.end) {
                retireAllocation(id)

                reevaluateWalletsAfterUpdate(walletsById.getValue(alloc.belongsToWallet))
            }
        }

        return Response.ok(Unit)
    }

    private fun retireAllocation(allocationId: Int) {
        val alloc = allocations[allocationId] ?: return
        val wallet = walletsById.getValue(alloc.belongsToWallet)
        val group = wallet.allocationsByParent.getValue(alloc.parentWallet)

        if (alloc.retired) return

        alloc.isDirty = true
        wallet.isDirty = true
        group.isDirty = true

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
            parentWallet.isDirty = true

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
            .filter { it.belongsToWallet in relevantWalletIds }
            .filter { it.belongsToWallet != minimumWalletId || it.id > minimumAllocationId }
            .filter { !it.retired }
            .sortedWith(Comparator.comparingInt<InternalAllocation?> { it.belongsToWallet }.thenComparingInt { it.id })
            .take(request.itemsPerPage ?: 250)
            .map {
                val wallet = relevantWallets.getValue(it.belongsToWallet)
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
            val walletId = allocations.getValue(allocId).belongsToWallet
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

    private fun retrieveDescendants(request: AccountingRequest.RetrieveDescendants): Response<List<String>> {
        val currentProject = request.projectId
        val owner = ownersByReference[currentProject]
            ?: return Response.error(
                HttpStatusCode.Forbidden,
                "Owner not found"
            )
        val wallets = walletsByOwner[owner.id]
            ?: return Response.error(
                HttpStatusCode.Forbidden,
                "Wallet not found"
            )
        val descendantsId = ArrayList<String>()
        val queue = ArrayDeque<InternalWallet>()
        queue.addAll(wallets)
        while (queue.isNotEmpty()) {
            val wal = queue.removeFirst()
            val childrenId = wal.childrenUsage.keys
            val childrenWallets = walletsById.filter { childrenId.contains(it.key) }
            childrenWallets.values.forEach {
                val childOwner = ownersById[it.ownedBy]
                if (childOwner != null && childOwner.isProject()) {
                    descendantsId.add(childOwner.reference)
                }
            }
            queue.addAll(childrenWallets.values)
        }
        return Response.ok(descendantsId.toSet().toList())
    }

    private fun scopeKey(wallet: InternalWallet, scope: String): String {
        return wallet.ownedBy.toString() + "\n" + scope
    }

    private fun reevaluateWalletsAfterUpdate(root: InternalWallet) {
        val now = Time.now()
        val visited = HashSet<Int>()
        val queue = ArrayDeque<InternalWallet>()
        visited.add(root.id)
        queue.add(root)
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()

            val maxUsable = maxUsableForWallet(next)
            if (maxUsable <= 0 && !next.wasLocked) {
                next.wasLocked = true
                markSignificantUpdate(next, now)
            } else if (maxUsable > 0 && next.wasLocked) {
                next.wasLocked = false
                markSignificantUpdate(next, now)
            }

            val children = next.childrenUsage.keys
            for (child in children) {
                if (child !in visited) {
                    visited.add(child)
                    queue.add(walletsById.getValue(child))
                }
            }
        }
    }

    private fun produceMermaidGraph(roots: List<Int>? = null): String {
        val relevantWallets = HashSet<Int>()
        if (roots == null) {
            relevantWallets.addAll(walletsById.keys)
        } else {
            for (root in roots) {
                relevantWallets.addAll(walletsById[root]?.generateLocalWallets()?.toSet() ?: emptySet())
            }
        }

        val now = Time.now()

        return mermaid outer@{
            node("W0", "Root")
            for (walletId in relevantWallets) {
                val wallet = walletsById[walletId] ?: continue

                subgraph("W${walletId}") {
                    node("W${walletId}Info", buildString {
                        append("<b>Info</b><br>")
                        append("lU: ")
                        append(wallet.localUsage)
                        append("<br>")

                        append("lR: ")
                        append(wallet.localRetiredUsage)
                        append("<br>")

                        append("eU: ")
                        append(wallet.excessUsage)
                        append("<br>")

                        append("tA: ")
                        append(wallet.totalAllocated)
                        append("<br>")

                        if (wallet.childrenUsage.isNotEmpty()) {
                            append("<br>children:<br>")
                            for ((childId, usage) in wallet.childrenUsage) {
                                if (childId !in relevantWallets) continue
                                append("+ W")
                                append(childId)
                                append("=")
                                append(usage)

                                val retired = wallet.childrenRetiredUsage[childId]
                                if (retired != null) {
                                    append(" R=${retired}")
                                }
                                append("<br>")
                            }
                        }
                    }, style = "text-align:left")
                    for ((parent, group) in wallet.allocationsByParent) {
                        val groupGraph = subgraph("W${wallet.id}W${parent}") {
                            node("W${wallet.id}W${parent}Info", buildString {
                                append("<b>Info</b><br>")
                                append("P : $parent ")
                                append("<br>tU: ")
                                append(group.treeUsage)

                                append("<br>tR: ")
                                append(group.retiredTreeUsage)

                                append("<br>pB: ")
                                append(group.preferredBalance(now))
                            }, style = "text-align:left")
                            for ((allocId, active) in group.allocationSet) {
                                val alloc = allocations.getValue(allocId)
                                node("A$allocId", "A${allocId} r/q: ${alloc.retiredUsage} ${alloc.quota} (A=${active})")
                            }
                        }

                        with(this@outer) {
                            groupGraph.linkTo("W${parent}")
                        }
                    }
                }
            }
        }
    }

    private val debugFile by lazy { File("/tmp/debug.txt").printWriter() }
    private inline fun debug(wallet: InternalWallet, fn: () -> String) {
        if (!DEBUG) return
        if (wallet.category.productType != ProductType.LICENSE) return

        synchronized(debugFile) {
            debugFile.println(fn())
            debugFile.flush()
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()

        // NOTE(Dan): Must be less than VERY_LARGE_NUMBER of Graph.kt
        // NOTE(Dan): Must be (significantly) larger than any cost which can naturally be created from a normal node
        private val MANDATORY_EDGE_COST = BigInteger.TWO.pow(80)
    }
}

class MermaidGraphBuilder(val id: String, val title: String?) {
    private val nodes = ArrayList<Node>()
    private val links = ArrayList<Link>()
    private val subgraphs = ArrayList<MermaidGraphBuilder>()

    fun node(id: String, title: String = id, shape: NodeShape = NodeShape.ROUND, style: String? = null): String {
        nodes.add(Node(id, title, shape, style))
        return id
    }

    fun subgraph(id: String, title: String = id, builder: MermaidGraphBuilder.() -> Unit): String {
        val graph = MermaidGraphBuilder(id, title)
        graph.builder()
        subgraphs.add(graph)
        return id
    }

    fun String.linkTo(
        destination: String,
        text: String? = null,
        destinationShape: ArrowShape? = ArrowShape.ARROW,
        sourceShape: ArrowShape? = null,
        lineType: LineType = LineType.NORMAL,
    ) {
        links.add(
            Link(
                this,
                destination,
                text,
                lineType,
                sourceShape,
                destinationShape
            )
        )
    }

    fun build(root: Boolean = true): String {
        val content = buildString {
            for (node in nodes) {
                append(node.id)
                append(node.shape.prefix)
                append('"')
                append(node.title)
                append('"')
                append(node.shape.suffix)
                appendLine()
                if (node.style != null) {
                    append("style ")
                    append(node.id)
                    append(" ")
                    append(node.style)
                    appendLine()
                }
            }

            for (link in links) {
                append(link.source)
                if (link.lineType == LineType.INVISIBLE) {
                    append(link.lineType.withoutArrow)
                } else if (link.sourceShape == null && link.destinationShape == null) {
                    append(link.lineType.withoutArrow)
                } else {
                    if (link.sourceShape != null) append(link.sourceShape.left)
                    append(link.lineType.withArrow)
                    if (link.destinationShape != null) append(link.destinationShape.right)
                }

                if (link.text != null) {
                    append("|")
                    append('"')
                    append(link.text)
                    append('"')
                    append("|")
                }

                append(link.destination)
                appendLine()
            }
        }

        return buildString {
            if (root) {
                appendLine("%%{init: {'themeVariables': { 'fontFamily': 'Monospace'}}}%%")
                append("flowchart TD")
            } else {
                append("subgraph")
                append(" ")
                append(id)
                if (title != null) {
                    append('[')
                    append('"')
                    append(title)
                    append('"')
                    append(']')
                }
            }

            appendLine()

            append(content.prependIndent("    "))

            for (graph in subgraphs) {
                val mermaid = graph.build(root = false)
                appendLine()
                append(mermaid.prependIndent("    "))
                appendLine("end")
            }
        }
    }

    data class Node(
        val id: String,
        val title: String,
        val shape: NodeShape,
        val style: String?,
    )

    data class Link(
        val source: String,
        val destination: String,
        val text: String?,
        val lineType: LineType,
        val sourceShape: ArrowShape?,
        val destinationShape: ArrowShape?,
    )

    enum class NodeShape(val prefix: String, val suffix: String) {
        ROUND("(", ")"),
        PILL("([", "])"),
        SUBROUTINE_BOX("[[", "]]"),
        CYLINDER("[(", ")]"),
        CIRCLE("((", "))"),
        ASYMMETRIC_SHAPE(">", "]"),
        RHOMBUS("{", "}"),
        HEXAGON("{{", "}}"),
        PARALLELOGRAM("[/", "/]"),
        PARALLELOGRAM_ALT("[\\", "\\]"),
        TRAPEZOID("[/", "\\]"),
        TRAPEZOID_ALT("[\\", "//]"),
        DOUBLE_CIRCLE("(((", ")))"),
    }

    enum class LineType(val withArrow: String, val withoutArrow: String) {
        NORMAL("--", "---"),
        THICK("==", "==="),
        INVISIBLE("~~", "~~~"),
        DOTTED("-.-", "-.-")
    }

    enum class ArrowShape(val left: String, val right: String) {
        ARROW("<", ">"),
        CIRCLE("o", "o"),
        CROSS("x", "x"),
    }
}

fun mermaid(builder: MermaidGraphBuilder.() -> Unit): String {
    val graph = MermaidGraphBuilder("root", null)
    graph.builder()
    return graph.build(root = true)
}

