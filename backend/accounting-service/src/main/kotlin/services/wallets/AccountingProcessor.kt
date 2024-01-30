package dk.sdu.cloud.accounting.services.wallets

import dk.sdu.cloud.*
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.WalletAllocationV2
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.SimpleProviderCommunication
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.debug.DebugContextType
import dk.sdu.cloud.accounting.api.WalletAllocationV2 as ApiWalletAllocation
import dk.sdu.cloud.accounting.api.WalletV2 as ApiWallet
import dk.sdu.cloud.accounting.api.WalletOwner as ApiWalletOwner
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.debug.detail
import dk.sdu.cloud.grant.api.GrantApplication
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.provider.api.translateToChargeType
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.ArrayList
import kotlin.math.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours

const val doDebug = false
const val allocationIdCutoff = 5900

val PROJECT_REGEX =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

private data class InternalWallet(
    val id: Int,
    val owner: String,
    val paysFor: ProductCategory,
    val chargePolicy: AllocationSelectorPolicy,

    var isDirty: Boolean = false,
)

private data class InternalWalletAllocation(
    val id: Int,
    val associatedWallet: Int,
    val parentAllocation: Int?,

    var notBefore: Long,
    var notAfter: Long?,

    var quota: Long,
    var treeUsage: Long? = null,
    var localUsage: Long,

    var isDirty: Boolean = false,

    var grantedIn: Long?,

    val canAllocate: Boolean,
    val allowSubAllocationsToAllocate: Boolean,
) {
    var inProgress: Boolean = false
        private set
    var beginNotBefore: Long = 0L
    var beginNotAfter: Long? = null
    var beginQuota: Long = 0L
    var beginTreeUsage: Long? = null
    var beginLocalUsage: Long = 0L
    var beginGrantedIn: Long? = null
    var lastBegin: Throwable? = null

    fun begin() {
        if (inProgress) throw RuntimeException("Already in progress", lastBegin)
        if (doDebug) lastBegin = RuntimeException("Invoking begin")

        beginNotBefore = notBefore
        beginNotAfter = notAfter
        beginQuota = quota
        beginLocalUsage = localUsage
        beginGrantedIn = grantedIn
        beginTreeUsage = treeUsage
        inProgress = true
    }

    fun commit() {
        check(inProgress)

        isDirty =
            isDirty ||
                    beginNotBefore != notBefore ||
                    beginNotAfter != notAfter ||
                    beginQuota != quota ||
                    beginLocalUsage != localUsage ||
                    beginGrantedIn != grantedIn ||
                    beginTreeUsage != treeUsage
        inProgress = false

        verifyIntegrity()
    }

    fun verifyIntegrity() {
        require((notAfter ?: Long.MAX_VALUE) >= notBefore) { "notAfter >= notBefore ($notAfter >= $notBefore) $this" }
        require(quota >= 0) { "initialBalance >= 0 ($quota >= 0) $this" }
        //legacy allocations does not live up to this requirement. Previous checks noted that this was only a problem
        //for allocations with id below 5900
        if (id > allocationIdCutoff) {
            require(parentAllocation == null || id > parentAllocation) { "id > parentAllocation ($id <= $parentAllocation) $this" }
        }
    }

    @Suppress("unused")
    fun rollback() {
        check(inProgress)

        notBefore = beginNotBefore
        notAfter = beginNotAfter
        quota = beginQuota
        localUsage = beginLocalUsage
        inProgress = false
    }

    fun clearDirty() {
        isDirty = false
    }

    fun isValid(now: Long): Boolean = now in notBefore..(notAfter ?: Long.MAX_VALUE)
}

sealed class AccountingRequest {
    abstract val actor: Actor
    abstract var id: Long

    data class RootDeposit(
        override val actor: Actor,
        val owner: String,
        val productCategory: ProductCategoryIdV2,
        val amount: Long,
        val startDate: Long,
        val endDate: Long,
        override var id: Long = -1,
        val forcedSync: Boolean = false
    ) : AccountingRequest()

    data class Deposit(
        override val actor: Actor,
        val owner: String,
        val parentAllocation: Int,
        val amount: Long,
        val notBefore: Long,
        val notAfter: Long?,
        override var id: Long = -1,
        val grantedIn: Long? = null,
    ) : AccountingRequest()

    data class Sync(override val actor: Actor = Actor.System, override var id: Long = -1L) : AccountingRequest()

    sealed class Charge : AccountingRequest() {
        abstract val owner: String
        abstract val productCategory: ProductCategoryIdV2
        override var id: Long = -1
        abstract val dryRun: Boolean


        data class OldCharge(
            override val actor: Actor,
            override val owner: String,
            override val dryRun: Boolean,
            val units: Long,
            val period: Long,
            val product: ProductReferenceV2,
        ) : Charge() {
            override val productCategory = ProductCategoryIdV2(product.category, product.provider)

            fun toDelta(productPrice: Long): DeltaCharge = DeltaCharge(
                actor,
                owner,
                dryRun,
                productCategory,
                units * period * productPrice,
                ChargeDescription(
                    "charge",
                    emptyList()
                )
            )

            fun toTotal(productPrice: Long): TotalCharge = TotalCharge(
                actor,
                owner,
                dryRun,
                productCategory,
                units * period * productPrice,
                ChargeDescription(
                    "charge",
                    emptyList()
                )
            )
        }

        data class DeltaCharge(
            override val actor: Actor,
            override val owner: String,
            override val dryRun: Boolean,
            override val productCategory: ProductCategoryIdV2,
            val usage: Long,
            val description: ChargeDescription,
        ) : Charge()

        data class TotalCharge(
            override val actor: Actor,
            override val owner: String,
            override val dryRun: Boolean,
            override val productCategory: ProductCategoryIdV2,
            val usage: Long,
            val description: ChargeDescription,
        ) : Charge()
    }

    data class Update(
        override val actor: Actor,
        val allocationId: Int,
        val amount: Long?,
        val notBefore: Long?,
        val notAfter: Long?,
        override var id: Long = -1,
    ) : AccountingRequest()

    data class RetrieveAllocationsInternal(
        override val actor: Actor,
        val owner: String,
        val category: ProductCategoryIdV2,
        override var id: Long = -1,
    ) : AccountingRequest()

    data class RetrieveWalletsInternal(
        override val actor: Actor,
        val owner: String,
        override var id: Long = -1,
    ) : AccountingRequest()

    data class BrowseSubAllocations(
        override val actor: Actor,
        val owner: String,
        val filterType: ProductType?,
        val query: String?,
        override var id: Long = -1
    ) : AccountingRequest()

    data class RetrieveProviderAllocations(
        override val actor: Actor,

        val providerId: String,
        val filterOwnerId: String? = null,
        val filterOwnerIsProject: Boolean? = null,
        val filterCategory: String? = null,

        val pagination: NormalizedPaginationRequestV2,

        override var id: Long = -1
    ) : AccountingRequest()

    data class FindRelevantProviders(
        override val actor: Actor,

        val username: String,
        val project: String?,
        val useProject: Boolean,
        val filterProductType: ProductType?,

        override var id: Long = -1
    ) : AccountingRequest()
}

sealed class AccountingResponse {
    abstract var id: Long

    data class Sync(
        override var id: Long = -1,
    ) : AccountingResponse()

    data class Charge(
        val success: Boolean,
        override var id: Long = -1,
    ) : AccountingResponse()

    data class RootDeposit(
        val createdAllocation: Int,
        override var id: Long = -1,
    ) : AccountingResponse()

    data class Deposit(
        val createdAllocation: Int,
        override var id: Long = -1,
    ) : AccountingResponse()

    data class Update(
        val success: Boolean,
        override var id: Long = -1,
    ) : AccountingResponse()

    data class Error(
        val message: String,
        val code: Int = 500,
        override var id: Long = -1,
    ) : AccountingResponse()

    data class RetrieveAllocationsInternal(
        val allocations: List<ApiWalletAllocation>,
        override var id: Long = -1,
    ) : AccountingResponse()

    data class RetrieveWalletsInternal(
        val wallets: List<ApiWallet>,
        override var id: Long = -1
    ) : AccountingResponse()

    data class BrowseSubAllocations(
        val allocations: List<SubAllocationV2>,
        override var id: Long = -1
    ) : AccountingResponse()

    data class RetrieveRelevantWalletsProviderNotifications(
        val page: PageV2<ProviderWalletSummaryV2>,
        override var id: Long = -1
    ) : AccountingResponse()

    data class FindRelevantProviders(
        val providers: List<String>,
        override var id: Long = -1
    ) : AccountingResponse()
}

inline fun <reified T : AccountingResponse> AccountingResponse.orThrow(): T {
    if (this is AccountingResponse.Error) {
        throw RPCException(message, HttpStatusCode.parse(code))
    } else {
        return this as? T ?: error("$this is not a ${T::class}")
    }
}

suspend fun AccountingProcessor.findRelevantProviders(request: AccountingRequest.FindRelevantProviders): AccountingResponse.FindRelevantProviders {
    return sendRequest(request).orThrow()
}

suspend fun AccountingProcessor.rootDeposit(request: AccountingRequest.RootDeposit): AccountingResponse.RootDeposit {
    return sendRequest(request).orThrow()
}

suspend fun AccountingProcessor.deposit(request: AccountingRequest.Deposit): AccountingResponse.Deposit {
    return sendRequest(request).orThrow()
}

suspend fun AccountingProcessor.charge(request: AccountingRequest.Charge): AccountingResponse.Charge {
    return sendRequest(request).orThrow()
}

suspend fun AccountingProcessor.update(request: AccountingRequest.Update): AccountingResponse.Update {
    return sendRequest(request).orThrow()
}

suspend fun AccountingProcessor.retrieveAllocationsInternal(
    request: AccountingRequest.RetrieveAllocationsInternal
): AccountingResponse.RetrieveAllocationsInternal {
    return sendRequest(request).orThrow()
}

suspend fun AccountingProcessor.retrieveWalletsInternal(
    request: AccountingRequest.RetrieveWalletsInternal
): AccountingResponse.RetrieveWalletsInternal {
    return sendRequest(request).orThrow()
}

suspend fun AccountingProcessor.browseSubAllocations(
    request: AccountingRequest.BrowseSubAllocations
): AccountingResponse.BrowseSubAllocations {
    return sendRequest(request).orThrow()
}

suspend fun AccountingProcessor.retrieveProviderAllocations(
    request: AccountingRequest.RetrieveProviderAllocations
): AccountingResponse.RetrieveRelevantWalletsProviderNotifications {
    return sendRequest(request).orThrow()
}

class AccountingProcessor(
    private val db: DBContext,
    private val debug: DebugSystem,
    private val providers: Providers<SimpleProviderCommunication>,
    private val distributedLocks: DistributedLockFactory,
    private val disableMasterElection: Boolean = false,
    distributedState: DistributedStateFactory,
    private val addressToSelf: String,
) {
    // Active processor
    // =================================================================================================================
    @Serializable
    private data class ActiveProcessor(val address: String)

    private val activeProcessor =
        distributedState.create(ActiveProcessor.serializer(), "accounting-active-processor", 60_000)

    suspend fun retrieveActiveProcessor(): String? {
        if (isActiveProcessor) {
            return null
        }
        return activeProcessor.get()?.address
    }

    // State
    // =================================================================================================================
    private val wallets = ArrayList<InternalWallet?>()
    private var walletsIdGenerator = 0

    private val allocations = ArrayList<InternalWalletAllocation?>()
    private var allocationIdGenerator = 0

    private val requests = Channel<AccountingRequest>(Channel.BUFFERED)

    // NOTE(Dan): Without replays, we risk the async listener missing the response if the coroutine is too slow to start
    private val responses = MutableSharedFlow<AccountingResponse>(replay = 16)
    private var requestIdGenerator = AtomicLong(0)

    private data class BreakdownSample(
        val walletId: Int,
        val sampledAt: Long,
        val localUsage: Long,
        val referencesAreProjects: Boolean,
        val bucketReferences: List<Int>,
        val bucketUsage: List<Long>,
    )

    private var lastSampling = 0L
    private var nextSynchronization = 0L
    private val dirtyDeposits = ArrayList<DirtyDeposit>()

    private data class DirtyDeposit(
        val allocationId: Int,
        val timestamp: Long,
        val quota: Long,
    )

    private val projects = ProjectCache(db)
    private val products = ProductCache(db)
    private val productCategories = ProductCategoryCache(db)

    private val turnstile = Mutex()
    private var isActiveProcessor = false

    private var isLoading = false

    // Metrics
    private var requestsHandled = 0
    private var slowestRequest = 0L
    private var slowestRequestName = "?"
    private var requestTimeSum = 0L
    private var lastSync = Time.now()

    // Primary interface
    // =================================================================================================================
    // The accounting processors is fairly simple to use. It must first be started by call start(). After this you can
    // process requests by invoking `sendRequest()` which will return an appropriate response.

    @OptIn(DelicateCoroutinesApi::class)
    fun start(): Job {
        return GlobalScope.launch {
            val lock = distributedLocks.create("accounting_processor", duration = 60_000)

            Runtime.getRuntime().addShutdownHook(
                Thread {
                    runBlocking {
                        if (!isActiveProcessor) {
                            return@runBlocking
                        }

                        turnstile.withLock {
                            if (!disableMasterElection && !lock.renew(60_000)) {
                                return@runBlocking
                            }

                            attemptSynchronize(true)
                            lock.release()
                        }
                    }
                }
            )

            while (isActive) {
                try {
                    becomeMasterAndListen(lock)
                } catch (ex: Throwable) {
                    debug.logThrowable("Error happened when attempting to lock service", ex)
                    log.info("Error happened when attempting to lock service: $ex")
                }

                delay(15000 + Random.nextLong(5000))
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun becomeMasterAndListen(lock: DistributedLock) {
        val didAcquire = disableMasterElection || lock.acquire()
        if (!didAcquire) return

        // Resetting state, so we do not attempt to load into already existing in-mem DB resulting in conflicts
        resetState()

        log.info("This service has become the master responsible for handling Accounting processor events!")
        activeProcessor.set(ActiveProcessor(addressToSelf))
        isActiveProcessor = true

        debug.useContext(DebugContextType.BACKGROUND_TASK, "Loading accounting database") {
            loadDatabase()
        }

        nextSynchronization = Time.now() + 0
        var isAlive = true
        while (currentCoroutineContext().isActive && isAlive) {
            try {
                turnstile.withLock {
                    select {
                        requests.onReceive { request ->
                            // NOTE(Dan): We attempt a synchronization here in case we receive so many requests that the
                            // timeout is never triggered.
                            attemptSynchronize()

                            val start = System.nanoTime()

                            val response = handleRequest(request)
                            response.id = request.id

                            requestsHandled++
                            val end = System.nanoTime()
                            requestTimeSum += (end - start)
                            if (end - start > slowestRequest) {
                                slowestRequest = end - start
                                slowestRequestName = request.javaClass.simpleName
                            }

                            if (doDebug) {
                                println("Request: $request")
                                println("Response: $response")
                                printState(true)
                                println(CharArray(120) { '-' }.concatToString())
                            }
                            responses.emit(response)
                        }
                        onTimeout(500) {
                            attemptSynchronize()
                        }
                    }
                    if (!renewLock(lock)) {
                        isAlive = false
                        isActiveProcessor = false
                    }
                }
            } catch (ex: Throwable) {
                debug.logThrowable("Error in Accounting processor", ex)
                log.info(ex.toReadableStacktrace().toString())
            }
        }
    }

    private suspend fun renewLock(lock: DistributedLock): Boolean {
        if (!disableMasterElection) {
            if (!lock.renew(90_000)) {
                log.warn("Lock was lost")
                isActiveProcessor = false
                return false
            }
            activeProcessor.set(ActiveProcessor(addressToSelf))
        }
        return true
    }

    suspend fun sendRequest(request: AccountingRequest): AccountingResponse {
        val id = requestIdGenerator.getAndIncrement()
        request.id = id
        return coroutineScope {
            val collector = async {
                var result: AccountingResponse? = null
                responses.takeWhile {
                    if (it.id == id) result = it
                    it.id != id
                }.collect()
                result ?: error("No response was ever received")
            }
            requests.send(request)
            collector.await()
        }
    }

    fun resetState() {
        wallets.clear()
        allocations.clear()
        walletsIdGenerator = 0
        allocationIdGenerator = 0
        requestsHandled = 0
        slowestRequest = 0L
        slowestRequestName = "?"
        requestTimeSum = 0L
        lastSync = Time.now()
        lastSampling = 0L
    }

    private suspend fun handleRequest(request: AccountingRequest): AccountingResponse {
        val result = try {
            when (request) {
                is AccountingRequest.Sync -> {
                    attemptSynchronize(forced = true)
                    AccountingResponse.Sync()
                }

                is AccountingRequest.RootDeposit -> rootDeposit(request)
                is AccountingRequest.Deposit -> deposit(request)
                is AccountingRequest.Update -> update(request)
                is AccountingRequest.Charge -> charge(request)
                is AccountingRequest.RetrieveAllocationsInternal -> retrieveAllocationsInternal(request)
                is AccountingRequest.RetrieveWalletsInternal -> retrieveWalletsInternal(request)
                is AccountingRequest.BrowseSubAllocations -> browseSubAllocations(request)
                is AccountingRequest.RetrieveProviderAllocations -> retrieveProviderAllocations(
                    request
                )

                is AccountingRequest.FindRelevantProviders -> findRelevantProviders(request)
            }
        } catch (ex: Throwable) {
            return AccountingResponse.Error(ex.toReadableStacktrace().message, 500)
        }
        if (doDebug) {
            var error = false
            for (allocation in allocations) {
                if (allocation == null) continue
                if (allocation.inProgress) {
                    println("Request: $request")
                    println("Response: $result")
                    println("Allocation still in progress: $allocation")
                    println("---------------------------------------------------------------")
                    error = true
                }
            }
            if (error) {
                printState(true)
                error("Allocation is in progress at the end of a request")
            }
        }

        return result
    }

    // Loading state from database
    // =================================================================================================================
    // The accounting processor loads the data at start-up and only then. All wallet and allocation data is then used
    // only from the in-memory version which is periodically synchronized using attemptSynchronize().
    private suspend fun loadDatabase() {
        log.info("Attempting to load postgres data.")

        try {
            if (isLoading) {
                log.info("Loading already in progress.")
                return
            }
            isLoading = true

            db.withSession { session ->
                log.info("Fetching last sample date")
                session.sendPreparedStatement(
                    {},
                    """
                        select provider.timestamp_to_unix(max(sampled_at))::int8
                        from accounting.wallet_samples
                        limit 1
                    """
                ).rows.forEach {
                    val mostRecentSample = it.getLong(0) ?: 0L
                    lastSampling = mostRecentSample
                }

                log.info("Loading wallets")
                //TODO(HENRIK) MAKE CHANGES TO DB
                session.sendPreparedStatement(
                    {},
                    """
                    declare wallet_load cursor for
                    select 
                        w.id, 
                        wo.username,
                        wo.project_id, 
                        pc.category, 
                        pc.provider, 
                        pc.product_type, 
                        w.allocation_selector_policy, 
                        au.name, 
                        au.name_plural, 
                        au.floating_point, 
                        au.display_frequency_suffix,
                        pc.accounting_frequency,
                        pc.free_to_use,
                        pc.allow_sub_allocations
                    from
                        accounting.wallets w join
                        accounting.wallet_owner wo
                            on w.owned_by = wo.id join
                        accounting.product_categories pc
                            on w.category = pc.id join 
                        accounting.accounting_units au on au.id = pc.accounting_unit
                    order by w.id
                """
                )

                while (true) {
                    val rows = session.sendPreparedStatement({}, "fetch forward 500 from wallet_load").rows
                    if (rows.isEmpty()) break
                    for (row in rows) {
                        val id = row.getLong(0)!!.toInt()
                        val username = row.getString(1)
                        val project = row.getString(2)
                        val category = row.getString(3)!!
                        val provider = row.getString(4)!!
                        val productType = ProductType.valueOf(row.getString(5)!!)
                        val allocationPolicy = AllocationSelectorPolicy.valueOf(row.getString(6)!!)
                        val accountingUnit = AccountingUnit(
                            row.getString(7)!!,
                            row.getString(8)!!,
                            row.getBoolean(9)!!,
                            row.getBoolean(10)!!
                        )
                        val frequency = row.getString(11)!!
                        val freeToUse = row.getBoolean(12)!!
                        val allowSubAllocations = row.getBoolean(13)!!
                        val emptySlots = id - wallets.size
                        require(emptySlots >= 0) { "Duplicate wallet detected (or bad logic): $id ${wallets.size} $emptySlots" }
                        repeat(emptySlots) { wallets.add(null) }
                        require(wallets.size == id) { "Bad logic detected wallets[id] != id" }

                        wallets.add(
                            InternalWallet(
                                id,
                                project ?: username ?: error("Bad wallet owner $id"),
                                ProductCategory(
                                    category,
                                    provider,
                                    productType,
                                    accountingUnit,
                                    AccountingFrequency.fromValue(frequency),
                                    emptyList(),
                                    freeToUse = freeToUse,
                                    allowSubAllocations = allowSubAllocations,
                                ),
                                allocationPolicy,
                            )
                        )
                    }
                }

                session.sendPreparedStatement({}, "close wallet_load")
                walletsIdGenerator = wallets.size

                log.info("Loading allocations")
                //TODO(HENRIK) MAKE CHANGES TO DB
                session.sendPreparedStatement(
                    {},
                    """
                    declare allocation_load cursor for
                    select
                        alloc.id, 
                        alloc.allocation_path::text, 
                        alloc.associated_wallet, 
                        provider.timestamp_to_unix(alloc.start_date), 
                        provider.timestamp_to_unix(alloc.end_date),
                        alloc.initial_balance,
                        alloc.balance,
                        alloc.local_balance,
                        alloc.granted_in,
                        alloc.can_allocate,
                        alloc.allow_sub_allocations_to_allocate
                    from
                        accounting.wallet_allocations alloc
                    order by
                        alloc.id
                """
                )

                while (true) {
                    val rows = session.sendPreparedStatement({}, "fetch forward 500 from allocation_load").rows
                    if (rows.isEmpty()) break
                    for (row in rows) {
                        val id = row.getLong(0)!!.toInt()
                        val allocationPath = row.getString(1)!!
                        val walletOwner = row.getLong(2)!!.toInt()
                        val startDate = row.getDouble(3)!!.toLong()
                        var endDate = row.getDouble(4)?.toLong()
                        var quota = row.getLong(5)!!
                        var treeUsage = quota - row.getLong(6)!!
                        var localUsage = quota - row.getLong(7)!!
                        val grantedIn = row.getLong(8)
                        val canAllocate = row.getBoolean(9)!!
                        val allowSubAllocationsToAllocate = row.getBoolean(10)!!

                        var isDirty = false

                        val emptySlots = id - allocations.size
                        require(emptySlots >= 0) { "Duplicate allocations detected (or bad logic): $id" }
                        repeat(emptySlots) { allocations.add(null) }
                        require(allocations.size == id) { "Bad logic detected wallets[id] != id" }

                        if ((endDate ?: Long.MAX_VALUE) < startDate) {
                            log.info("Changing endDate of allocation $id.")
                            endDate = startDate
                            isDirty = true
                        }
                        if (quota < 0) {
                            log.info("Changing initialBalance of allocation $id from $quota to 0")
                            quota = 0
                            isDirty = true
                        }

                        if (localUsage > quota) {
                            log.info("Changing localUsage of allocation $id from $localUsage to $quota")
                            localUsage = quota
                            isDirty = true
                        }

                        allocations.add(
                            InternalWalletAllocation(
                                id,
                                walletOwner,
                                allocationPath.split(".").let { path ->
                                    if (path.size <= 1) null
                                    else path[path.lastIndex - 1]
                                }?.toIntOrNull(),
                                startDate,
                                endDate,
                                quota,
                                treeUsage = null,
                                localUsage,
                                grantedIn = grantedIn,
                                canAllocate = canAllocate,
                                allowSubAllocationsToAllocate = allowSubAllocationsToAllocate,
                                isDirty = isDirty,
                            ).also {
                                it.verifyIntegrity()
                            }
                        )
                    }
                }

                session.sendPreparedStatement({}, "close allocation_load")
                allocationIdGenerator = allocations.size

                if (doDebug) printState(printWallets = true)

                log.info("Loading applications that has not been synced")
                val unresolvedApplications = session.sendPreparedStatement(
                    //language=postgresql
                    """
                    with unsynced_applications as (
                        select id 
                        from "grant".applications
                        where overall_state = 'APPROVED' and synchronized = false
                    )
                    select "grant".application_to_json(id)
                    from unsynced_applications
                """
                ).rows
                    .map {
                        val application = defaultMapper.decodeFromString<GrantApplication>(it.getString(0)!!)
                        application
                    }
                log.info("Deposits granted un-synchronized applications")
                unresolvedApplications.forEach { application ->
                    val (owner, type) = when (val recipient = application.currentRevision.document.recipient) {
                        is GrantApplication.Recipient.NewProject -> {
                            //Attempt to create project and PI. In case of conflict nothing is created but id returned
                            val createdProject = session.sendPreparedStatement(
                                {
                                    setParameter("parent_id", application.currentRevision.document.parentProjectId)
                                    setParameter("pi", application.createdBy)
                                    setParameter("title", recipient.title)
                                },
                                """ 
                                with created_project as (
                                    insert into project.projects (id, created_at, modified_at, title, archived, parent, dmp, subprojects_renameable)
                                    select uuid_generate_v4()::text, to_timestamp(:now / 1000.0), to_timestamp(:now / 1000.0), :title, false, :parent_id::text, null, false
                                    on conflict (parent, upper(title::text)) do update set title = excluded.title
                                    returning id
                                ),
                                created_user as (
                                    insert into project.project_members (created_at, modified_at, role, username, project_id)
                                    select to_timestamp(:now / 1000.0), to_timestamp(:now / 1000.0), 'PI', :pi, cp.id
                                    from created_project cp
                                    on conflict (username, project_id) do nothing
                                )
                                select * from created_project
                            """
                            ).rows
                                .singleOrNull()
                                ?.getString(0)
                                ?: throw RPCException.fromStatusCode(
                                    HttpStatusCode.InternalServerError,
                                    "Error in creating project and PI"
                                )

                            Pair(createdProject, GrantApplication.Recipient.NewProject)
                        }

                        is GrantApplication.Recipient.ExistingProject -> {
                            Pair(recipient.id, GrantApplication.Recipient.ExistingProject)
                        }

                        is GrantApplication.Recipient.PersonalWorkspace ->
                            Pair(recipient.username, GrantApplication.Recipient.PersonalWorkspace)
                    }
                    application.currentRevision.document.allocationRequests.forEach { allocRequest ->
                        val granterOfResource =
                            application.status.stateBreakdown.find { it.projectId == allocRequest.grantGiver }?.projectId
                                ?: throw RPCException.fromStatusCode(
                                    HttpStatusCode.InternalServerError,
                                    "Project not found"
                                )

                        val pi =
                            projects.retrievePIFromProjectID(granterOfResource)
                                ?: throw RPCException.fromStatusCode(
                                    HttpStatusCode.InternalServerError,
                                    "missing PI for project"
                                )
                        deposit(
                            AccountingRequest.Deposit(
                                actor = Actor.SystemOnBehalfOfUser(pi),
                                owner = owner,
                                parentAllocation = allocRequest.sourceAllocation!!.toInt(),
                                amount = allocRequest.balanceRequested!!,
                                notBefore = allocRequest.period.start ?: Time.now(),
                                notAfter = allocRequest.period.end,
                                grantedIn = application.id.toLong(),
                            )
                        )
                    }
                }

                log.info("Loading Gifts and deposits un-synchronized claims")
                val giftIdsAndClaimer = session.sendPreparedStatement(
                    //language=postgresql
                    """
                        select * 
                        from "grant".gifts_claimed
                        where synchronized = false
                    """
                ).rows.map {
                    Pair(it.getLong(0), it.getString(1))
                }

                giftIdsAndClaimer.forEach {
                    val rows = session.sendPreparedStatement(
                        {
                            setParameter("gift_id", it.first)
                            setParameter("username", it.second)
                        },
                        """
                            select
                                g.id gift_id,
                                :username recipient,
                                coalesce(res.credits, res.quota) as balance,
                                pc.category,
                                pc.provider,
                                g.resources_owned_by
                            from
                                -- NOTE(Dan): Fetch data about the gift
                                "grant".gifts g join
                                "grant".gift_resources res on g.id = res.gift_id join

                                accounting.product_categories pc on
                                    res.product_category = pc.id 
                            where
                                g.id = :gift_id;
                        """
                    ).rows

                    rows.forEach { row ->
                        val receiver = row.getString(1)!!
                        val balance = row.getLong(2)!!
                        val category = ProductCategoryIdV2(row.getString(3)!!, row.getString(4)!!)
                        val sourceProject = row.getString(5)!!

                        val now = Time.now()
                        val wallet = findWallet(sourceProject, category)
                            ?: throw RPCException.fromStatusCode(
                                HttpStatusCode.NotFound,
                                "Wallet missing for gift to be claimed"
                            )

                        val allocations = allocations
                            .asSequence()
                            .filterNotNull()
                            .filter { it.associatedWallet == wallet.id && it.isValid(now) }
                            .map { it.toApiAllocation() }
                            .toList()


                        //TODO(HENRIK) CHECK FIND STATEMENT
                        val sourceAllocation =
                            allocations.find { (it.quota - it.localUsage) >= balance } ?: allocations.firstOrNull()
                            ?: throw RPCException("Unable to claim this gift", HttpStatusCode.BadRequest)

                        deposit(
                            AccountingRequest.Deposit(
                                ActorAndProject(Actor.System, null).actor,
                                receiver,
                                sourceAllocation.id.toInt(),
                                balance,
                                notBefore = now,
                                notAfter = null,
                            )
                        )
                    }
                }

            }

            calculateFullTreeUsage()
            log.info("Load of DB done.")
        } finally {
            isLoading = false
        }
    }

    // Utilities for managing state
    // =================================================================================================================
    fun retrieveUsageFromProduct(owner: String, productCategory: ProductCategoryIdV2): Long? {
        val wallet = findWallet(owner, productCategory)
        return if (wallet != null) {
            retrieveUsageOfWallet(wallet.toApiWallet())
        } else {
            null
        }
    }

    fun retrieveUsageOfWallet(wallet: ApiWallet): Long {
        val productCategory = ProductCategoryIdV2(wallet.paysFor.name, wallet.paysFor.provider)
        val internalWallet = when (val owner = wallet.owner) {
            is ApiWalletOwner.Project -> findWallet(owner.projectId, productCategory)
            is ApiWalletOwner.User -> findWallet(owner.username, productCategory)
        }
        return if (internalWallet == null) {
            0L
        } else {
            allocations.filter { it != null && it.associatedWallet == internalWallet.id }
                .mapNotNull { retrieveUsageOfAllocation(it!!.toApiAllocation()) }
                .sum()
        }
    }

    private fun retrieveUsageOfAllocation(
        allocation: ApiWalletAllocation
    ): Long {
        return allocations[allocation.id.toInt()]?.localUsage ?: 0L
    }

    private fun findWallet(owner: String, category: ProductCategoryIdV2): InternalWallet? {
        return wallets.find { it?.owner == owner && it.paysFor.name == category.name && it.paysFor.provider == category.provider }
    }

    private suspend fun createWallet(owner: String, category: ProductCategoryIdV2): InternalWallet? {
        val resolvedCategory = productCategories.retrieveProductCategory(category) ?: return null
        val selectorPolicy = AllocationSelectorPolicy.EXPIRE_FIRST
        val wallet = InternalWallet(
            walletsIdGenerator++,
            owner,
            resolvedCategory,
            selectorPolicy,
            isDirty = true
        )

        wallets.add(wallet)
        return wallet
    }

    private fun createAllocation(
        wallet: Int,
        quota: Long,
        parentAllocation: Int?,
        notBefore: Long,
        notAfter: Long?,
        grantedIn: Long?,
        canAllocate: Boolean,
        allowSubAllocationsToAllocate: Boolean
    ): InternalWalletAllocation {
        val alloc = InternalWalletAllocation(
            allocationIdGenerator++,
            wallet,
            parentAllocation,
            notBefore,
            notAfter,
            quota = quota,
            treeUsage = 0,
            localUsage = 0,
            isDirty = true,
            grantedIn = grantedIn,
            canAllocate = canAllocate,
            allowSubAllocationsToAllocate = allowSubAllocationsToAllocate
        )

        allocations.add(alloc)
        return alloc
    }

    private fun InternalWallet.toApiWallet(): ApiWallet {
        return ApiWallet(
            if (!owner.matches(PROJECT_REGEX)) {
                ApiWalletOwner.User(owner)
            } else {
                ApiWalletOwner.Project(owner)
            },
            paysFor,
            allocations.mapNotNull { alloc ->
                if (alloc?.associatedWallet == id) {
                    alloc.toApiAllocation()
                } else null
            }
        )
    }

    private fun InternalWalletAllocation.toApiAllocation(): ApiWalletAllocation {
        val internalWallAlloc = allocations[id]
            ?: throw RPCException.fromStatusCode(
                HttpStatusCode.InternalServerError,
                "Cannot find allocations that is being translated"
            )
        val maxAllowedUsage = calculateMaxUsableQuotaFromParents(internalWallAlloc)
        return ApiWalletAllocation(
            id.toString(),
            run {
                val reversePath = ArrayList<Int>()
                var current: InternalWalletAllocation? = allocations[id]
                while (current != null) {
                    reversePath.add(current.id)

                    val parent = current.parentAllocation
                    current = if (parent == null) null else allocations[parent]
                }

                reversePath.reversed().map { it.toString() }
            },
            localUsage = localUsage,
            quota = quota,
            treeUsage = treeUsage,
            startDate = notBefore,
            endDate = notAfter ?: Long.MAX_VALUE,
            grantedIn,
            canAllocate = canAllocate,
            allowSubAllocationsToAllocate = allowSubAllocationsToAllocate,
            maxUsable = maxAllowedUsage
        )
    }

    private suspend fun findRelevantProviders(
        request: AccountingRequest.FindRelevantProviders
    ): AccountingResponse {
        val providers = if (!request.useProject) {
            val projectsUserIsPartOf = projects.fetchMembership(request.username, allowCacheRefill = false)
            val allWorkspaces = projectsUserIsPartOf + listOf(request.username)

            allWorkspaces
                .flatMap { projectId ->
                    wallets
                        .asSequence()
                        .filter { wallet -> wallet?.owner == projectId }
                        .mapNotNull { it?.paysFor?.provider }
                }
                .toSet()
        } else {
            val project = request.project
            if (project != null && projects.retrieveProjectRole(request.username, project) == null) {
                emptySet()
            } else {
                wallets
                    .filterNotNull()
                    .filter {
                        it.owner == (project ?: request.username) &&
                            (request.filterProductType == null || it.paysFor.productType == request.filterProductType)
                    }
                    .map { it.paysFor.provider }
                    .toSet()
            }
        }

        val freeProviders = products.findAllFreeProducts()
            .filter { request.filterProductType == null || it.productType == request.filterProductType }
            .map { it.category.provider }
            .toSet()

        val allProviders = providers + freeProviders
        return AccountingResponse.FindRelevantProviders(allProviders.toList())
    }

    // Utilities for enforcing allocation period constraints
    // =================================================================================================================
    private fun checkOverlapAncestors(
        parent: InternalWalletAllocation,
        notBefore: Long,
        notAfter: Long?
    ): AccountingResponse.Error? {
        var current: InternalWalletAllocation? = parent
        if ((notAfter ?: Long.MAX_VALUE) < notBefore) return overlapError(parent)
        while (current != null) {
            if (notBefore !in current.notBefore..(current.notAfter ?: Long.MAX_VALUE)) {
                return overlapError(parent)
            }

            if ((notAfter ?: Long.MAX_VALUE) !in current.notBefore..(current.notAfter ?: Long.MAX_VALUE)) {
                return overlapError(parent)
            }

            val parentOfCurrent = current.parentAllocation
            current = if (parentOfCurrent == null) null else allocations[parentOfCurrent]
        }
        return null
    }

    private fun clampDescendantsOverlap(parent: InternalWalletAllocation) {
        val watchSet = hashSetOf(parent.id)
        // NOTE(Dan): The hierarchy is immutable after creation and allocation IDs are monotonically increasing. As a
        // result, we don't have to search any ID before the root, and we only have to perform a single loop over the
        // allocations to get all results.
        for (i in (parent.id + 1) until allocations.size) {
            //NOTE(Henrik) It is possible to have nullable allocations in the list
            //In that case continue to next id
            val alloc = allocations[i]
            if (alloc != null) {
                val newNotAfter =
                    if (min(alloc.notAfter ?: Long.MAX_VALUE, parent.notAfter ?: Long.MAX_VALUE) == Long.MAX_VALUE) {
                        null
                    } else {
                        min(alloc.notAfter ?: Long.MAX_VALUE, parent.notAfter ?: Long.MAX_VALUE)
                    }
                if (alloc.parentAllocation in watchSet) {
                    alloc.begin()
                    alloc.notBefore = max(alloc.notBefore, parent.notBefore)
                    alloc.notAfter = newNotAfter
                    alloc.commit()
                    watchSet.add(alloc.id)
                }
            }
        }
    }

    private fun overlapError(root: InternalWalletAllocation): AccountingResponse.Error {
        var latestBefore = Long.MIN_VALUE
        var earliestAfter = Long.MAX_VALUE

        var c: InternalWalletAllocation? = root
        while (c != null) {
            if (c.notBefore >= latestBefore) latestBefore = c.notBefore
            if ((c.notAfter ?: Long.MAX_VALUE) <= earliestAfter) earliestAfter = c.notAfter ?: Long.MAX_VALUE

            val parentOfCurrent = c.parentAllocation
            c = if (parentOfCurrent == null) null else allocations[parentOfCurrent]
        }

        val b = dateString(latestBefore)
        val a = dateString(earliestAfter)

        return AccountingResponse.Error(
            "Allocation period is outside of allowed range. It must be between $b and $a.",
            code = 400
        )
    }

    private fun calculateFullTreeUsage() {
        val sortedAndReversedList = allocations.filterNotNull().sortedBy { it.parentAllocation }.reversed()
        //Traverses from leafs towards top.
        for (currentAlloc in sortedAndReversedList) {
            currentAlloc.begin()
            currentAlloc.treeUsage = min((currentAlloc.treeUsage ?: 0) + currentAlloc.localUsage, currentAlloc.quota)
            currentAlloc.commit()
            if (currentAlloc.parentAllocation != null) {
                val parent = allocations[currentAlloc.parentAllocation]
                    ?: throw RPCException.fromStatusCode(
                        HttpStatusCode.InternalServerError,
                        "Allocation has parent error"
                    )
                parent.begin()
                parent.treeUsage = min((parent.treeUsage ?: 0) + currentAlloc.treeUsage!!, parent.quota)
                parent.commit()
            }
        }
    }

    private fun calculateMaxUsableQuotaFromParents(allocation: InternalWalletAllocation): Long {
        if (allocation.parentAllocation != null) {
            val parent = allocations.getOrNull(allocation.parentAllocation)
                ?: throw RPCException.fromStatusCode(
                    HttpStatusCode.InternalServerError,
                    "Allocation has parent error"
                )
            return min(
                allocation.quota - ( allocation.treeUsage ?: allocation.localUsage ),
                calculateMaxUsableQuotaFromParents(parent)
                )
        } else {
            return allocation.quota - ( allocation.treeUsage ?: allocation.localUsage )
        }
    }

    // Deposits
    // =================================================================================================================
    private suspend fun rootDeposit(request: AccountingRequest.RootDeposit): AccountingResponse {
        if (request.amount < 0) return AccountingResponse.Error("Cannot deposit with a negative balance", 400)
        if (request.actor != Actor.System) {
            return AccountingResponse.Error("Only UCloud administrators can perform a root deposit", 403)
        }

        val existingWallet = findWallet(request.owner, request.productCategory)
            ?: createWallet(request.owner, request.productCategory)
            ?: return AccountingResponse.Error("Unknown product category.", 400)

        val productAllowsSubAllocations = existingWallet.paysFor.allowSubAllocations

        val created = createAllocation(
            existingWallet.id,
            request.amount,
            null,
            request.startDate,
            request.endDate,
            null,

            // NOTE(Dan): Root allocations can always sub-allocate. We have an assumption that provider projects are
            // always created with canConsumeResources = false, but this is technically not enforced.
            // TODO(Dan): We should enforce that provider projects have canConsumResources = false.
            canAllocate = true,
            allowSubAllocationsToAllocate = productAllowsSubAllocations,
        ).id

        dirtyDeposits.add(DirtyDeposit(created, Time.now(), request.amount))

        if (request.forcedSync) {
            attemptSynchronize(forced = true)
        }
        return AccountingResponse.RootDeposit(created)
    }

    fun checkIfAllocationIsAllowed(allocs: List<String>): Boolean {
        val foundAllocations = allocations.mapNotNull { savedAlloc ->
            val found = allocs.find { it.toInt() == savedAlloc?.id }
            if (found != null) savedAlloc else null
        }
        return foundAllocations.all { it.canAllocate }
    }

    fun checkIfSubAllocationIsAllowed(allocs: List<String>): Boolean {
        val foundAllocations = allocations.mapNotNull { savedAlloc ->
            val found = allocs.find { it.toInt() == savedAlloc?.id }
            if (found != null) savedAlloc else null
        }
        return foundAllocations.all { it.allowSubAllocationsToAllocate }
    }

    private suspend fun deposit(request: AccountingRequest.Deposit): AccountingResponse {
        if (request.amount < 0) return AccountingResponse.Error("Cannot deposit with a negative balance", 400)

        val parent = allocations.getOrNull(request.parentAllocation)
            ?: return AccountingResponse.Error("Bad parent allocation", 400)

        if (request.actor != Actor.System) {
            val wallet = wallets[parent.associatedWallet]!!
            val role = projects.retrieveProjectRole(request.actor.safeUsername(), wallet.owner)

            if (role?.isAdmin() != true) {
                return AccountingResponse.Error(
                    "You are not allowed to manage this allocation.",
                    HttpStatusCode.Forbidden.value
                )
            }
        }
        val notBefore = max(parent.notBefore, request.notBefore)
        val notAfter =
            min(parent.notAfter ?: (Time.now() + (365 * 24 * 60 * 60 * 1000L)), request.notAfter ?: Long.MAX_VALUE)
        run {
            val error = checkOverlapAncestors(parent, notBefore, notAfter)
            if (error != null) return error
        }

        val parentWallet = wallets[parent.associatedWallet]!!

        val category = ProductCategoryIdV2(parentWallet.paysFor.name, parentWallet.paysFor.provider)
        val existingWallet = findWallet(request.owner, category)
            ?: createWallet(request.owner, category)
            ?: return AccountingResponse.Error("Internal error - Product category no longer exists ${parentWallet.paysFor}")

        val isProject = PROJECT_REGEX.matches(request.owner)
        val info = projects.retrieveProjectInfoFromId(request.owner)
        val canAllocate = if (isProject) {
            parent.allowSubAllocationsToAllocate || !info.first.canConsumeResources
        } else {
            false
        }
        val allowSubAllocationsToAllocate = if (isProject) {
            parent.allowSubAllocationsToAllocate
        } else {
            false
        }

        val created = createAllocation(
            existingWallet.id,
            request.amount,
            request.parentAllocation,
            notBefore,
            notAfter,
            request.grantedIn,
            canAllocate = canAllocate,
            allowSubAllocationsToAllocate = allowSubAllocationsToAllocate,
        ).id

        dirtyDeposits.add(DirtyDeposit(created, Time.now(), request.amount))

        return AccountingResponse.Deposit(created)
    }


    // Charge
    // =================================================================================================================

    @Suppress("DEPRECATION")
    private suspend fun charge(request: AccountingRequest.Charge): AccountingResponse {
        if (!authorizeProvider(request.actor, request.productCategory)) {
            return AccountingResponse.Error("Forbidden", 403)
        }

        if (request.dryRun) return check(request)

        when (request) {
            is AccountingRequest.Charge.OldCharge -> {
                val category = productCategories.retrieveProductCategory(request.productCategory)
                    ?: return AccountingResponse.Charge(false)
                //Note(HENRIK) This will also be caught in delta and total charge, but lets just skip the translate work
                if (category.freeToUse) {
                    return AccountingResponse.Charge(true)
                }
                val product =
                    products.retrieveProduct(request.product)?.first ?: return AccountingResponse.Charge(false)
                val newUnits = when (val v1 = product.toV1()) {
                    is Product.Compute -> {
                        request.units / (v1.cpu ?: 1)
                    }

                    else -> request.units
                }
                val price = product.price
                val requestWithNewUnit = request.copy(units = newUnits)
                return when (translateToChargeType(category)) {
                    ChargeType.ABSOLUTE -> {
                        deltaCharge(requestWithNewUnit.toDelta(price))
                    }

                    ChargeType.DIFFERENTIAL_QUOTA -> {
                        totalCharge(requestWithNewUnit.toTotal(price))
                    }
                }
            }

            is AccountingRequest.Charge.DeltaCharge -> {
                return deltaCharge(request)
            }

            is AccountingRequest.Charge.TotalCharge -> {
                return totalCharge(request)
            }
            // Leaving redundant else in case we add more charge types
            else -> {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Unknown charge request type")
            }
        }
    }

    private fun isLocked(allocation: InternalWalletAllocation): Boolean {
        return allocation.quota <= allocation.localUsage || allocation.quota <= (allocation.treeUsage ?: 0)
    }
    private suspend fun allocationIsLocked(allocationId: Int): Boolean {
        val allocation = allocations[allocationId] ?: return true
        if (isLocked(allocation)) {
            return true
        }
        if (allocation.parentAllocation != null) {
            return allocationIsLocked(allocation.parentAllocation)
        }
        return false
    }

    private suspend fun check(request: AccountingRequest.Charge): AccountingResponse {
        val productCategory = productCategories.retrieveProductCategory(request.productCategory)
            ?: return AccountingResponse.Error("No matching product category", 400)
        if (productCategory.freeToUse) {
            return AccountingResponse.Charge(true)
        }

        val wallet = wallets.find {
            it?.owner == request.owner &&
                    (it.paysFor.provider == productCategory.provider &&
                            it.paysFor.name == productCategory.name)
        }?.toApiWallet() ?: return AccountingResponse.Charge(false)

        val activeAllocations = wallet.allocations.filter { it.isActive() && !allocationIsLocked(it.id.toInt()) }
        //If local usage is higher or equal to quota no more charges can be made
        if (activeAllocations.sumOf { it.localUsage } >= activeAllocations.sumOf { it.quota }) {
            //local allocations are not sufficient
            return AccountingResponse.Charge(false)
        }
        return AccountingResponse.Charge(true)
    }

    private suspend fun deltaCharge(request: AccountingRequest.Charge.DeltaCharge): AccountingResponse {
        val productCategory = productCategories.retrieveProductCategory(request.productCategory)
            ?: return AccountingResponse.Charge(false)
        if (productCategory.freeToUse) {
            return AccountingResponse.Charge(true)
        }
        val wallet = wallets.find {
            it?.owner == request.owner &&
                    (it.paysFor.provider == request.productCategory.provider &&
                            it.paysFor.name == request.productCategory.name)
        }?.toApiWallet() ?: return AccountingResponse.Charge(false)
        return if (productCategory.isPeriodic()) {
            applyPeriodCharge(request.usage, wallet.allocations, request.description)
        } else {
            var currentUsage = 0L
            for (allocation in wallet.allocations) {
                currentUsage += allocation.localUsage
            }
            applyNonPeriodicCharge(currentUsage + request.usage, wallet.allocations, request.description)
        }
    }

    private suspend fun totalCharge(request: AccountingRequest.Charge.TotalCharge): AccountingResponse {
        val productCategory = productCategories.retrieveProductCategory(request.productCategory)
            ?: return AccountingResponse.Charge(false)
        if (productCategory.freeToUse) {
            return AccountingResponse.Charge(true)
        }
        val wallet = wallets.find {
            it?.owner == request.owner &&
                    (it.paysFor.provider == request.productCategory.provider &&
                            it.paysFor.name == request.productCategory.name)
        }?.toApiWallet() ?: return AccountingResponse.Charge(false)
        return if (productCategory.isPeriodic()) {
            var currentUsage = 0L
            for (allocation in wallet.allocations) {
                currentUsage += allocation.localUsage
            }
            applyPeriodCharge(request.usage - currentUsage, wallet.allocations, request.description)
        } else {
            applyNonPeriodicCharge(request.usage, wallet.allocations, request.description)
        }
    }

    private fun updateParentTreeUsage(
        allocation: InternalWalletAllocation,
        delta: Long,
    ): Boolean {
        var toCharge = delta
        if (allocation.parentAllocation == null) {
            return true
        } else {
            val parent = allocations[allocation.parentAllocation] ?: return false
            parent.begin()
            //If resources are being released we should be careful not to just subtract from the parent.
            //The allocation might still be using its parens full capacity so parents should not be charged
            //before the allocation is using less than what has been given.
            if (toCharge < 0) {
                if ((allocation.treeUsage ?: allocation.localUsage) > parent.quota) {
                    //Dont update since the usage is still be over consumed so parent should just have quota as treeusage
                } else {
                    val previousOvercharge = ((allocation.treeUsage ?: allocation.localUsage) - toCharge) -  allocation.quota
                    if (previousOvercharge > 0) {
                        toCharge += previousOvercharge
                    }
                    parent.treeUsage = (parent.treeUsage ?: parent.localUsage) + toCharge
                }
            } else {
                parent.treeUsage = min((parent.treeUsage ?: parent.localUsage) + toCharge, parent.quota)
            }
            parent.commit()
            return if (parent.parentAllocation == null) {
                true
            } else {
                return updateParentTreeUsage(parent, toCharge)
            }
        }
    }

    private fun chargeAllocation(
        allocationId: Int,
        delta: Long,
    ): Boolean {
        val internalWalletAllocation = allocations[allocationId] ?: return false
        internalWalletAllocation.begin()
        val willOvercharge = (internalWalletAllocation.localUsage + delta > internalWalletAllocation.quota)
                && (internalWalletAllocation.localUsage < internalWalletAllocation.quota)
        internalWalletAllocation.localUsage += delta
        internalWalletAllocation.treeUsage = min(
            (internalWalletAllocation.treeUsage ?: internalWalletAllocation.localUsage) + delta,
            internalWalletAllocation.quota
        )
        internalWalletAllocation.commit()

        //In case of overcharge, only propagate the part of delta that is need to hit quota. Parents should not pay for
        // overconsumption
        if (willOvercharge) {
            val remainingDelta = internalWalletAllocation.quota - (internalWalletAllocation.localUsage - delta)
            if (!updateParentTreeUsage(internalWalletAllocation, remainingDelta)) {
                return false
            }
        }
        if (internalWalletAllocation.localUsage > internalWalletAllocation.quota) {
            return true
        }
        if (!updateParentTreeUsage(internalWalletAllocation, delta)) {
            return false
        }
        return true
    }

    private suspend fun applyPeriodCharge(
        delta: Long,
        walletAllocations: List<WalletAllocationV2>,
        chargeDescription: ChargeDescription,
    ): AccountingResponse {
        var chargeFailed = false
        if (delta == 0L) return AccountingResponse.Charge(true)
        var activeQuota = 0L
        for (allocation in walletAllocations) {
            if (!allocation.isActive()) continue
            if (allocationIsLocked(allocation.id.toInt())) continue
            activeQuota += allocation.quota
        }
        var amountCharged = 0L
        for (allocation in walletAllocations) {
            if (!allocation.isActive()) continue
            if (allocationIsLocked(allocation.id.toInt())) continue

            //If we have no quota then just charge all to first allocation, and skip rest
            if (activeQuota == 0L) {
                if (!chargeAllocation(allocation.id.toInt(), delta)) {
                    return AccountingResponse.Error(
                        "Internal Error in charging all to first allocation", 500
                    )
                }
                amountCharged = delta
                chargeFailed = true
                break
            }
            val weight = allocation.quota.toDouble() / activeQuota.toDouble()
            val localCharge = (delta.toDouble() * weight).toLong()
            if (!chargeAllocation(allocation.id.toInt(), localCharge)) {
                return AccountingResponse.Error(
                    "Internal Error in charging specific allocation, allocation: ${allocation.id}", 500
                )
            }
            amountCharged += localCharge
        }
        if (amountCharged != delta) {
            val stillActiveAllocations = walletAllocations.filter { it.isActive() && !allocationIsLocked(it.id.toInt()) }
            val difference = delta - amountCharged
            if (stillActiveAllocations.isEmpty()) {
                // Will choose latest invalidated allocation to charge.
                // In the case of only active but empty allocs choose the first (an most likely only) allocation
                // In the case there is no allocations then something went terrible wrong since we just used it above
                val allocChosen = walletAllocations.filter { it.endDate <= Time.now() }.maxByOrNull { it.endDate }
                    ?: walletAllocations.lastOrNull() ?: return AccountingResponse.Error("Allocations somehow disappeared")
                if (!chargeAllocation(allocChosen.id.toInt(), difference)) {
                    return AccountingResponse.Error(
                        "Internal Error in charging all to first allocation", 500
                    )
                }
                return AccountingResponse.Charge(false)
            } else {
                val amountPerAllocation = difference / stillActiveAllocations.size
                var isFirst = true
                for (allocation in stillActiveAllocations) {
                    if (isFirst) {
                        if (!chargeAllocation(allocation.id.toInt(), difference % walletAllocations.size)) {
                            return AccountingResponse.Error(
                                "Internal Error in charging remainder", 500
                            )
                        }
                        isFirst = false
                    }
                    if (amountPerAllocation != 0L) {
                        if (!chargeAllocation(allocation.id.toInt(), amountPerAllocation)) {
                            return AccountingResponse.Error(
                                "Internal Error in charging remaining", 500
                            )
                        }
                    }
                }
                if (delta > activeQuota) {
                    return AccountingResponse.Charge(false)
                }
            }
        }
        return AccountingResponse.Charge(!chargeFailed)
    }

    private fun applyNonPeriodicCharge(
        totalUsage: Long,
        walletAllocations: List<WalletAllocationV2>,
        description: ChargeDescription,
    ): AccountingResponse {
        if (walletAllocations.isEmpty()) {
            return AccountingResponse.Error("No allocations to charge from", 400)
        }
        var activeQuota = 0L
        val activeAllocations = walletAllocations.mapNotNull {
            if (it.isActive()) {
                activeQuota += it.quota
                it.id
            } else {
                null
            }
        }
        val allocationsUsedBeforeChange = walletAllocations.mapNotNull {
            if (it.localUsage > 0) {
                it.id
            } else {
                null
            }
        }

        var totalCharged = 0L
        for (allocation in walletAllocations) {
            val alloc = allocations[allocation.id.toInt()]
                ?: return AccountingResponse.Error("Error on finding walletAllocation", 500)

            val oldValue = alloc.localUsage
            var diff = -oldValue

            //Resetting the old localusage to 0 before updating if still active
            alloc.begin()

            alloc.localUsage = 0L
            var treeChange = 0L

            if (activeAllocations.contains(allocation.id)) {
                val weight = alloc.quota.toDouble() / activeQuota.toDouble()
                val toCharge = round(totalUsage.toDouble() * weight).toLong()
                diff = toCharge - oldValue
                //Not sure why this is here. This case will only happen when an allocation
                //has expried and moves all usage to other still active allocation and then
                //charges less than the active allocations quota. And in this case the diff
                //will always be correct as it is.
                /*if (diff < 0 && abs(diff) > quota) {
                    diff += quota
                }*/
                alloc.localUsage = toCharge
                treeChange = diff
                alloc.treeUsage = (alloc.treeUsage ?: alloc.localUsage) + treeChange
                totalCharged += toCharge

            } else {
                treeChange = min(diff, alloc.quota)
                alloc.treeUsage = (alloc.treeUsage ?: alloc.localUsage) + treeChange
            }
            alloc.commit()

            //No need to use time on recursive updating tree when diff == 0
            if (diff != 0L) {
                updateParentTreeUsage(alloc, min(diff, alloc.quota))
            }
        }

        if (totalCharged != totalUsage) {
            val difference = totalUsage - totalCharged
            if (activeAllocations.isEmpty()) {
                if (allocationsUsedBeforeChange.isEmpty()) {
                    return AccountingResponse.Error("No old allocations to charge from", 400)
                }
                val amountPerAllocation = difference / allocationsUsedBeforeChange.size
                var isFirst = true
                //If we do not have any active allocations to choose from we should charge does that was in use before
                //the charge
                for (allocation in allocationsUsedBeforeChange) {
                    if (isFirst) {
                        if (!chargeAllocation(allocation.toInt(), difference % walletAllocations.size)) {
                            return AccountingResponse.Error(
                                "Internal Error in charging remainder of non-periodic", 500
                            )
                        }
                        isFirst = false
                    }
                    if (!chargeAllocation(allocation.toInt(), amountPerAllocation)) {
                        return AccountingResponse.Error(
                            "Internal Error in charging remaining of non-periodic", 500
                        )
                    }
                }
            } else {
                // If we have any active allocations then we want to use does and keep the reset of the old ones.
                val amountPerAllocation = difference / activeAllocations.size
                var isFirst = true
                for (allocation in activeAllocations) {
                    if (isFirst) {
                        if (!chargeAllocation(allocation.toInt(), difference % walletAllocations.size)) {
                            return AccountingResponse.Error(
                                "Internal Error in charging remainder of non-periodic", 500
                            )
                        }
                        isFirst = false
                    }
                    if (!chargeAllocation(allocation.toInt(), amountPerAllocation)) {
                        return AccountingResponse.Error(
                            "Internal Error in charging remaining of non-periodic", 500
                        )
                    }
                }
            }
            if (activeQuota < totalUsage) {
                return AccountingResponse.Charge(false)
            }
        }
        return AccountingResponse.Charge(true)
    }

    // Update
    // =================================================================================================================
    private suspend fun update(request: AccountingRequest.Update): AccountingResponse {
        val amountRequested = request.amount
        if (amountRequested != null && amountRequested < 0) return AccountingResponse.Error(
            "Cannot update to a negative balance",
            400
        )
        val allocation = allocations.getOrNull(request.allocationId)
            ?: return AccountingResponse.Error("Invalid allocation id supplied", 400)

        if (amountRequested != null && (allocation.localUsage > amountRequested || ((allocation.treeUsage
                ?: allocation.localUsage) > amountRequested))
        ) {
            return AccountingResponse.Error("Cannot set value to lower than current usage", 400)
        }

        wallets[allocation.associatedWallet]
            ?: return AccountingResponse.Error("Invalid allocation id supplied", 400)

        if (request.actor != Actor.System) {
            val parentAllocation =
                if (allocation.parentAllocation == null) null
                else allocations[allocation.parentAllocation]

            if (parentAllocation == null) {
                return AccountingResponse.Error("You are not allowed to manage this allocation.", 403)
            }

            val role = projects.retrieveProjectRole(
                request.actor.safeUsername(),
                wallets[parentAllocation.associatedWallet]!!.owner
            )

            if (role?.isAdmin() != true) {
                return AccountingResponse.Error("You are not allowed to manage this allocation.", 403)
            }
        }

        val parent = if (allocation.parentAllocation == null) null else allocations[allocation.parentAllocation]

        val notBefore = if (parent != null) {
            max(parent.notBefore, request.notBefore ?: 0)
        } else {
            request.notBefore
        }

        if (parent != null) {
            val error = checkOverlapAncestors(parent, notBefore ?: 0, request.notAfter ?: allocation.notAfter)
            if (error != null) return error
        }

        allocation.begin()
        var quotaChange = 0L
        if (amountRequested != null) {
            quotaChange = amountRequested - allocation.quota
            allocation.quota = request.amount
        }
        if (notBefore != null) {
            allocation.notBefore = notBefore
        }
        if (request.notAfter != null) {
            allocation.notAfter = request.notAfter
        }

        allocation.commit()
        clampDescendantsOverlap(allocation)
        return AccountingResponse.Update(true)
    }

    // Retrieve Allocations
    // =================================================================================================================
    private fun retrieveAllocationsInternal(request: AccountingRequest.RetrieveAllocationsInternal): AccountingResponse {
        if (request.actor != Actor.System) return AccountingResponse.Error("Forbidden", 403)
        val now = Time.now()
        val wallet = findWallet(request.owner, request.category)
            ?: return AccountingResponse.Error("Unknown wallet requested", 404)

        return AccountingResponse.RetrieveAllocationsInternal(
            allocations
                .asSequence()
                .filterNotNull()
                .filter { it.associatedWallet == wallet.id && it.isValid(now) }
                .map { it.toApiAllocation() }
                .toList()
        )
    }

    private suspend fun retrieveWalletsInternal(request: AccountingRequest.RetrieveWalletsInternal): AccountingResponse {
        if (!authorizeAccess(request.actor, request.owner)) return AccountingResponse.Error("Forbidden", 403)

        val wallets = wallets.filter { it?.owner == request.owner }
        return AccountingResponse.RetrieveWalletsInternal(
            wallets
                .asSequence()
                .filterNotNull()
                .map { it.toApiWallet() }
                .toList()
        )
    }

    private suspend fun getAllocationsPath(allocationId: Int): ArrayList<Int> {
        val current = allocations[allocationId] ?: return arrayListOf()
        return if (current.parentAllocation == null) {
            arrayListOf(current.id)
        } else {
            val path = getAllocationsPath(current.parentAllocation)
            path.add(current.id)
            path
        }
    }

    private fun retrieveProviderAllocations(
        request: AccountingRequest.RetrieveProviderAllocations
    ): AccountingResponse {
        // Format for pagination tokens: $walletId/$allocationId.
        // When specified we will find anything in wallets with id >= walletId.
        // When $walletId matches then the allocations are also required to be > allocationId.
        val nextTokens = request.pagination.next?.split("/")

        val minimumWalletId = nextTokens?.getOrNull(0)?.toIntOrNull() ?: 0
        val minimumAllocationId = nextTokens?.getOrNull(1)?.toIntOrNull() ?: 0
        val now = Time.now()

        val relevantWallets = wallets
            .asSequence()
            .filterNotNull()
            .filter { it.paysFor.provider == request.providerId }
            .filter { it.id >= minimumWalletId }
            .filter { request.filterCategory == null || request.filterCategory == it.paysFor.name }
            .filter { request.filterOwnerId == null || request.filterOwnerId == it.owner }
            .filter { request.filterOwnerIsProject != true || it.owner.matches(PROJECT_REGEX) }
            .filter { request.filterOwnerIsProject != false || !it.owner.matches(PROJECT_REGEX) }
            .sortedBy { it.id }
            .associateBy { it.id }

        val relevantWalletIds = relevantWallets.keys

        val relevantAllocations = allocations
            .asSequence()
            .filterNotNull()
            .filter { it.associatedWallet in relevantWalletIds }
            .filter { it.associatedWallet != minimumWalletId || it.id > minimumAllocationId }
            .filter { now in it.notBefore..(it.notAfter ?: Long.MAX_VALUE) }
            .sortedWith(Comparator.comparingInt<InternalWalletAllocation?> { it.associatedWallet }
                .thenComparingInt { it.id })
            .take(request.pagination.itemsPerPage)
            .map {
                val apiWallet = relevantWallets.getValue(it.associatedWallet).toApiWallet()
                ProviderWalletSummaryV2(
                    it.id.toString(),
                    apiWallet.owner,
                    apiWallet.paysFor,
                    it.notBefore,
                    it.notAfter,
                    it.quota
                )
            }
            .toList()

        val lastAllocation = relevantAllocations.lastOrNull()
        val newNextToken = if (lastAllocation != null && relevantAllocations.size < request.pagination.itemsPerPage) {
            val allocId = lastAllocation.id.toInt()
            val walletId = allocations.find { it?.id == allocId }?.associatedWallet!!
            "$walletId/$allocId"
        } else {
            null
        }

        return AccountingResponse.RetrieveRelevantWalletsProviderNotifications(
            PageV2(
                request.pagination.itemsPerPage,
                relevantAllocations,
                newNextToken
            )
        )
    }

    private suspend fun browseSubAllocations(
        request: AccountingRequest.BrowseSubAllocations
    ): AccountingResponse {
        if (!authorizeAccess(request.actor, request.owner)) return AccountingResponse.Error("Forbidden", 403)

        val currentProjectWalletsIds = wallets.mapNotNull { if (it?.owner == request.owner) it.id else null }.toSet()
        val currentProjectAllocations = mutableListOf<Int>()
        val subAllocations = mutableListOf<InternalWalletAllocation>()
        allocations.forEach {
            if (it != null && currentProjectWalletsIds.contains(it.associatedWallet)) {
                currentProjectAllocations.add(it.id)
            }
        }

        // Double loop needed due to ids not in order for first allocations on production.
        allocations.forEach {
            if (it != null && currentProjectAllocations.contains(it.parentAllocation)) {
                subAllocations.add(it)
            }
        }

        val list = subAllocations.mapNotNull { allocation ->
            val wall = wallets[allocation.associatedWallet]
            if (wall != null) {
                val projectInfo = projects.retrieveProjectInfoFromId(wall.owner)
                SubAllocationV2(
                    id = allocation.id.toString(),
                    path = allocation.toApiAllocation().allocationPath.joinToString(separator = "."),
                    startDate = allocation.notBefore,
                    endDate = allocation.notAfter,
                    productCategory = wall.paysFor,
                    workspaceId = projectInfo.first.projectId,
                    workspaceTitle = projectInfo.first.title,
                    workspaceIsProject = wall.owner.matches(PROJECT_REGEX),
                    projectPI = projectInfo.second,
                    usage = allocation.treeUsage ?: allocation.localUsage,
                    quota = allocation.quota,
                    grantedIn = allocation.grantedIn
                )
            } else null
        }

        val filteredList = if (request.query == null) {
            list
        } else {
            val query = request.query
            list.filter {
                it.workspaceTitle.contains(query) ||
                        it.productCategory.name.contains(query) ||
                        it.productCategory.provider.contains(query)
            }
        }
        return AccountingResponse.BrowseSubAllocations(filteredList)
    }

    // Authorization
    // =================================================================================================================
    private suspend fun authorizeAccess(actor: Actor, owner: String): Boolean {
        if (actor != Actor.System) {
            val isProject = owner.matches(PROJECT_REGEX)
            val username = actor.safeUsername()
            if (isProject) {
                val role = projects.retrieveProjectRole(username, owner)
                if (role == null) return false
            } else {
                if (username != owner) return false
            }
        }
        return true
    }

    private fun authorizeProvider(actor: Actor, category: ProductCategoryIdV2): Boolean {
        val username = actor.safeUsername()
        if (username.startsWith("_")) return true
        if (!username.startsWith(AuthProviders.PROVIDER_PREFIX)) return false
        return username.removePrefix(AuthProviders.PROVIDER_PREFIX) == category.provider
    }

    // Database synchronization
    // =================================================================================================================
    // We attempt to synchronize the dirty changes with the database at least once every 30 seconds. This is not a super
    // precise measurement, and we allow this to be off by ~1 second.
    private suspend fun attemptSynchronize(forced: Boolean = false) {
        val now = Time.now()
        if (now < nextSynchronization && !forced) return
        if (isLoading) return

        if (lastSync != -1L && requestsHandled > 0) {
            val timeDiff = now - lastSync
            log.info(
                "Handled $requestsHandled in ${timeDiff}ms. " +
                        "Average speed was ${requestTimeSum / requestsHandled} nanoseconds. " +
                        "Slowest request was: $slowestRequestName at $slowestRequest nanoseconds."
            )

            slowestRequestName = ""
            slowestRequest = 0
            requestsHandled = 0
            requestTimeSum = 0L
            lastSync = now
        }

        log.info("Synchronizing accounting data")
        debug.useContext(DebugContextType.BACKGROUND_TASK, "Synchronizing accounting data") {
            debug.detail("Filling products")
            products.fillCache()
            debug.detail("Filling projects")
            projects.fillCache()

            db.withSession { session ->
                // Synchronize new wallet owners to the database
                // -----------------------------------------------------------------------------------------------------
                debug.detail("Dealing with wallets")
                wallets.asSequence().filterNotNull().chunkedSequence(500).forEach { chunk ->
                    val filtered = chunk
                        .filter { it.isDirty }
                        .takeIfNotEmpty()
                        ?.toList()
                        ?: return@forEach

                    session.sendPreparedStatement(
                        {
                            filtered.split {
                                into("ids") { it.owner }
                            }
                        },
                        """
                            with
                                input_table as (
                                    select unnest(:ids::text[]) id
                                ),
                                username_and_project as (
                                    select u.id as username, p.id as project_id
                                    from
                                        input_table t left join
                                        auth.principals u on t.id = u.id left join
                                        project.projects p on t.id = p.id
                                )
                            insert into accounting.wallet_owner (username, project_id) 
                            select username, project_id
                            from username_and_project
                            on conflict do nothing 
                        """
                    )

                    // Synchronize new wallets to the database
                    // -----------------------------------------------------------------------------------------------------
                    session.sendPreparedStatement(
                        {
                            filtered.split {
                                into("ids") { it.id.toLong() }
                                into("categories") { it.paysFor.name }
                                into("providers") { it.paysFor.provider }
                                into("owned_by") { it.owner }
                            }
                        },
                        """
                            with input_table as (
                                select
                                    unnest(:ids::bigint[]) id,
                                    unnest(:categories::text[]) category,
                                    unnest(:providers::text[]) provider,
                                    unnest(:owned_by::text[]) owned_by
                            )
                            insert into accounting.wallets (id, category, owned_by) 
                            select t.id, pc.id, wo.id
                            from
                                input_table t join
                                accounting.wallet_owner wo on
                                    t.owned_by = wo.username or
                                    t.owned_by = wo.project_id join
                                accounting.product_categories pc on
                                    t.category = pc.category and
                                    t.provider = pc.provider
                            on conflict do nothing 
                        """
                    )
                }

                // Synchronize state of allocations
                // -----------------------------------------------------------------------------------------------------
                debug.detail("Dealing with allocations")
                allocations.asSequence().filterNotNull().chunkedSequence(500).forEach { chunk ->
                    val filtered = chunk
                        .filter { it.isDirty }
                        .takeIfNotEmpty()
                        ?: return@forEach

                    session.sendPreparedStatement(
                        {
                            filtered.split {
                                into("ids") { it.id.toLong() }
                                into("allocation_paths") {
                                    val reversePath = ArrayList<Int>()
                                    var current: InternalWalletAllocation? = allocations[it.id]
                                    while (current != null) {
                                        reversePath.add(current.id)

                                        val parent = current.parentAllocation
                                        current = if (parent == null) null else allocations[parent]
                                    }
                                    reversePath.reversed().joinToString(".")
                                }
                                into("associated_wallets") { it.associatedWallet.toLong() }
                                //TODO(HENRIK NEW VERSION CHECK)
                                into("balances") { it.quota - (it.treeUsage ?: it.localUsage) }
                                into("initial_balances") { it.quota }
                                into("local_balances") { it.quota - it.localUsage }
                                into("start_dates") { it.notBefore }
                                into("end_dates") { it.notAfter }
                                into("granted_ins") { it.grantedIn }
                                into("can_allocates") { it.canAllocate }
                                into("allow_subs") { it.allowSubAllocationsToAllocate }
                            }
                        },
                        """
                            insert into accounting.wallet_allocations 
                                (id, allocation_path, associated_wallet, balance, initial_balance, local_balance, start_date, 
                                 end_date, granted_in, provider_generated_id, can_allocate, allow_sub_allocations_to_allocate)
                            select
                                unnest(:ids::bigint[]),
                                unnest(:allocation_paths::ltree[]),
                                unnest(:associated_wallets::bigint[]),
                                unnest(:balances::bigint[]),
                                unnest(:initial_balances::bigint[]),
                                unnest(:local_balances::bigint[]),
                                to_timestamp(unnest(:start_dates::bigint[]) / 1000),
                                to_timestamp(unnest(:end_dates::bigint[]) / 1000),
                                unnest(:granted_ins::bigint[]),
                                null,
                                unnest(:can_allocates::bool[]),
                                unnest(:allow_subs::bool[])
                            on conflict (id) do update set
                                balance = excluded.balance,
                                initial_balance = excluded.initial_balance,
                                local_balance = excluded.local_balance,
                                start_date = excluded.start_date,
                                end_date = excluded.end_date,
                                granted_in = excluded.granted_in
                        """
                    )
                }

                // Synchronize deposit notifications (for providers)
                // -----------------------------------------------------------------------------------------------------
                debug.detail("Dealing with transactions")
                dirtyDeposits.asSequence()
                    .chunkedSequence(500)
                    .forEach { chunk ->
                        session.sendPreparedStatement(
                            {
                                setParameter("now", now)
                                chunk.split {
                                    into("allocations") { it.allocationId }
                                    into("balances") { it.quota }
                                }
                            },
                            """
                                with
                                    raw_data as (
                                        select
                                            unnest(:allocations::bigint[]) allocation,
                                            unnest(:balances::bigint[]) balance
                                    ),
                                    notification_data as (
                                        select
                                            data.balance,
                                            pc.id as category_id,
                                            owner.username,
                                            owner.project_id
                                        from
                                            raw_data data 
                                            join accounting.wallet_allocations alloc on
                                                data.allocation = alloc.id 
                                            join accounting.wallets wallet on
                                                alloc.associated_wallet = wallet.id 
                                            join accounting.wallet_owner owner on
                                                wallet.owned_by = owner.id
                                            join accounting.product_categories pc on
                                                wallet.category = pc.id
                                    )
                                insert into accounting.deposit_notifications
                                    (created_at, username, project_id, category_id, balance) 
                                select
                                    to_timestamp(:now / 1000.0), username, project_id, category_id, balance
                                from notification_data
                            """
                        )
                    }

                // Mark grant applications and gifts as synchronized
                // -----------------------------------------------------------------------------------------------------
                session.sendPreparedStatement(
                    //language=postgresql
                    """
                        UPDATE "grant".applications
                        SET synchronized = true
                        WHERE overall_state = 'APPROVED' AND synchronized = false
                    """
                )

                session.sendPreparedStatement(
                    //language=postgresql
                    """
                        UPDATE "grant".gifts_claimed
                        SET synchronized = true
                        WHERE synchronized = false
                    """
                )

                // Check if it is time to sample state of wallets
                // -----------------------------------------------------------------------------------------------------
                val shouldSampleWallets = (now - lastSampling).absoluteValue > 6.hours.inWholeMilliseconds
                if (shouldSampleWallets) {
                    data class Alloc(
                        val allocId: Int,
                        val localUsage: Long,
                        val treeUsage: Long,
                        val quota: Long,
                    )

                    data class Sample(
                        val walletId: Int,
                        var localUsage: Long = 0L,
                        var localUsageChange: Long = 0L,
                        var treeUsage: Long = 0L,
                        var quota: Long = 0L,
                        val allocs: ArrayList<Alloc> = ArrayList(),
                    )

                    val samples = HashMap<Int, Sample>()

                    for (allocation in allocations) {
                        if (allocation == null) continue

                        val allocationPeriod = (allocation.notBefore..(allocation.notAfter ?: Long.MAX_VALUE))
                        if (now !in allocationPeriod && lastSampling !in allocationPeriod) continue
                        val isValidNow = now in allocationPeriod

                        val sample = samples.getOrPut(allocation.associatedWallet) {
                            Sample(allocation.associatedWallet)
                        }

                        if (isValidNow) {
                            sample.localUsage += allocation.localUsage
                            sample.treeUsage += allocation.treeUsage ?: allocation.localUsage
                            sample.quota += allocation.quota
                            sample.allocs.add(
                                Alloc(
                                    allocation.id,
                                    allocation.localUsage,
                                    allocation.treeUsage ?: allocation.localUsage,
                                    allocation.quota,
                                )
                            )
                        } else {
                            sample.allocs.add(Alloc(allocation.id, 0L, 0L, 0L))
                        }
                    }

                    samples.entries.chunkedSequence(500).forEach { chunk ->
                        session.sendPreparedStatement(
                            {
                                setParameter("now", now)
                                val allocWalletIds = ArrayList<Int>().also { setParameter("alloc_wallet_ids", it) }
                                val allocIds = ArrayList<Int>().also { setParameter("alloc_ids", it) }
                                val localAlloc = ArrayList<Long>().also { setParameter("local_allocs", it) }
                                val treeAlloc = ArrayList<Long>().also { setParameter("tree_allocs", it) }
                                val quotaAlloc = ArrayList<Long>().also { setParameter("quota_allocs", it) }

                                val walletIds = ArrayList<Int>().also { setParameter("wallet_ids", it) }
                                val localUsage = ArrayList<Long>().also { setParameter("local_usage", it) }
                                val treeUsage = ArrayList<Long>().also { setParameter("tree_usage", it) }
                                val quota = ArrayList<Long>().also { setParameter("quota", it) }

                                for ((walletId, sample) in chunk) {
                                    for (alloc in sample.allocs) {
                                        allocWalletIds.add(walletId)
                                        allocIds.add(alloc.allocId)
                                        localAlloc.add(alloc.localUsage)
                                        treeAlloc.add(alloc.treeUsage)
                                        quotaAlloc.add(alloc.quota)
                                    }

                                    walletIds.add(walletId)
                                    localUsage.add(sample.localUsage)
                                    treeUsage.add(sample.treeUsage)
                                    quota.add(sample.quota)
                                }
                            },
                            """
                                with
                                    allocs as (
                                        select
                                            unnest(:alloc_wallet_ids::int[]) as wallet_id,
                                            unnest(:alloc_ids::int[]) as alloc_id,
                                            unnest(:local_allocs::int8[]) as local_usage,
                                            unnest(:tree_allocs::int8[]) as tree_alloc,
                                            unnest(:quota_allocs::int8[]) as quota_alloc
                                    ),
                                    aggregated_allocs as (
                                        select
                                            wallet_id,
                                            array_agg(alloc_id) as alloc_ids,
                                            array_agg(local_usage) as local_usage,
                                            array_agg(tree_alloc) as tree_usage,
                                            array_agg(quota_alloc) as quota
                                        from allocs
                                        group by wallet_id
                                    ),
                                    wallets as (
                                        select
                                            unnest(:wallet_ids::int4[]) as wallet_id,
                                            unnest(:local_usage::int8[]) as local_usage,
                                            unnest(:tree_usage::int8[]) as tree_usage,
                                            unnest(:quota::int8[]) as quota
                                    ),
                                    combined as (
                                        select
                                            to_timestamp(:now / 1000.0), w.wallet_id, w.local_usage, w.tree_usage, w.quota,
                                            coalesce(a.alloc_ids, array[]::int4[]),
                                            coalesce(a.local_usage, array[]::int8[]),
                                            coalesce(a.tree_usage, array[]::int8[]),
                                            coalesce(a.quota, array[]::int8[])
                                        from
                                            wallets w
                                            left join aggregated_allocs a on w.wallet_id = a.wallet_id
                                    )
                                insert into accounting.wallet_samples
                                    (sampled_at, wallet_id, local_usage, tree_usage, quota, allocation_ids, 
                                     local_usage_by_allocation, tree_usage_by_allocation, quota_by_allocation)
                                select * from combined
                            """
                        )
                    }

                    lastSampling = now
                }
            }

            // Notify providers about new deposits
            // ---------------------------------------------------------------------------------------------------------
            val depositForProviders = dirtyDeposits.asSequence()
                .map { wallets[allocations[it.allocationId]!!.associatedWallet]!!.paysFor.provider }
                .toSet()

            if (depositForProviders.isNotEmpty()) {
                depositForProviders.forEach { provider ->
                    val comms = providers.prepareCommunication(provider)
                    DepositNotificationsProvider(provider).pullRequest.call(Unit, comms.client)
                }
            }

            // Prepare for next iteration
            // ---------------------------------------------------------------------------------------------------------
            wallets.asSequence().filterNotNull().filter { it.isDirty }.forEach { it.isDirty = false }
            allocations.asSequence().filterNotNull().filter { it.isDirty }.forEach { it.clearDirty() }

            nextSynchronization = Time.now() + 30_000
            log.info("Synchronization of accounting data: Done!")
        }
    }

    // Debugging
    // =================================================================================================================
    // Several utilities intended for debugging. Note that printState() is _not_ thread safe and should only be invoked
    // if you absolutely know that the processors is idle. This method should never be invoked in something resembling
    // a production environment.
    fun printState(printWallets: Boolean = false) {
        if (printWallets) {
            println("Wallets:")
            println()
            table {
                column("ID")
                column("Owner", 45)
                column("Pays For")
                column("Type", 20)
                column("Dirty")

                for (wallet in wallets) {
                    if (wallet == null) continue
                    cell(wallet.id)
                    cell(wallet.owner)
                    cell(wallet.paysFor.name)
                    cell(wallet.paysFor.productType)
                    cell(wallet.isDirty)
                    nextRow()
                }
            }.also(::println)
        }

        println()
        println("Allocations:")
        println()
        table {
            column("ID")
            column("Owner", 45)
            column("Parent")
            column("LocalUsage", 20)
            column("Quota", 20)
            column("TreeUsage", 20)
            column("Dirty")
            column("Start", 30)
            column("End", 30)

            for (alloc in allocations) {
                if (alloc == null) continue
                val owner = wallets[alloc.associatedWallet]?.owner ?: continue
                if (owner.startsWith("_filter")) continue
                cell(alloc.id)
                cell("$owner (${alloc.associatedWallet})")
                cell(alloc.parentAllocation)
                cell(alloc.localUsage)
                cell(alloc.quota)
                cell(alloc.treeUsage)
                cell(alloc.isDirty)
                cell(dateString(alloc.notBefore))
                cell(if (alloc.notAfter == null) "Never" else dateString(alloc.notAfter!!))

                nextRow()
            }
        }.also(::println)
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm:ss z")
    private fun dateString(timestamp: Long): String {
        return Date(timestamp).toInstant().atZone(ZoneId.of("Europe/Copenhagen")).format(dateFormatter)
    }

    companion object : Loggable {
        override val log = logger()
    }
}

private class ProjectCache(private val db: DBContext) {
    private data class ProjectMember(val username: String, val project: String, val role: ProjectRole)

    private val projectMembers = AtomicReference<List<ProjectMember>>(emptyList())
    private val projects = AtomicReference<List<Pair<ProjectInfo, String>>>(emptyList())
    private val fillMutex = Mutex()

    suspend fun fillCache() {
        val beforeMembers = projectMembers.get()
        val beforeProjects = projects.get()
        fillMutex.withLock {
            val currentMembers = projectMembers.get()
            val currentProjects = projects.get()
            if (currentMembers != beforeMembers || currentProjects != beforeProjects) return

            val projectMembers = ArrayList<ProjectMember>()
            //Project with title and PI
            val projects = ArrayList<Pair<ProjectInfo, String>>()

            db.withSession { session ->
                session.sendPreparedStatement(
                    {},
                    """
                        declare project_load cursor for
                        select pm.username::text, pm.project_id::text, pm.role::text
                        from project.project_members pm
                    """
                )

                while (true) {
                    val rows = session.sendPreparedStatement({}, "fetch forward 500 from project_load").rows
                    if (rows.isEmpty()) break

                    for (row in rows) {
                        val username = row.getString(0)!!
                        val project = row.getString(1)!!
                        val role = ProjectRole.valueOf(row.getString(2)!!)
                        projectMembers.add(ProjectMember(username, project, role))
                    }
                }

                session.sendPreparedStatement({}, "close project_load")

                if (!this.projectMembers.compareAndSet(currentMembers, projectMembers)) {
                    error("Project members were updated even though we have the mutex")
                }

                session.sendPreparedStatement(
                    {},
                    """
                        declare project_curs cursor for 
                        select p.id, p.title, pm.username, p.pid, p.can_consume_resources
                        from project.projects p join project.project_members pm on p.id = pm.project_id
                        where pm.role = 'PI'
                    """
                )

                while (true) {
                    val rows = session.sendPreparedStatement({}, "fetch forward 500 from project_curs").rows
                    if (rows.isEmpty()) break

                    for (row in rows) {
                        val projectId = row.getString(0)!!
                        val projectTitle = row.getString(1)!!
                        val pi = row.getString(2)!!
                        val pid = row.getInt(3)!!
                        val canConsumeResources = row.getBoolean(4)!!
                        projects.add(Pair(ProjectInfo(projectId, pid, projectTitle, canConsumeResources), pi))
                    }
                }

                session.sendPreparedStatement({}, "close project_curs")

                if (!this.projects.compareAndSet(currentProjects, projects)) {
                    error("Project members were updated even though we have the mutex")
                }
            }
        }
    }

    suspend fun retrieveProjectInfoFromId(
        id: String,
        allowCacheRefill: Boolean = true
    ): Pair<ProjectInfo, String> {
        if (!id.matches(PROJECT_REGEX)) return Pair(ProjectInfo(id, -1, id, true), id)

        val project = projects.get().find { it.first.projectId == id }
        if (project == null && allowCacheRefill) {
            fillCache()
            return retrieveProjectInfoFromId(id, false)
        }
        return project ?: Pair(ProjectInfo(id, -1, id, true), id)
    }

    suspend fun retrieveProjectRole(username: String, project: String, allowCacheRefill: Boolean = true): ProjectRole? {
        val role = projectMembers.get().find { it.username == username && it.project == project }?.role
        if (role == null && allowCacheRefill) {
            fillCache()
            return retrieveProjectRole(username, project, false)
        }
        return role
    }

    suspend fun retrievePIFromProjectID(projectId: String, allowCacheRefill: Boolean = true): String? {
        val pi = projectMembers.get().find { it.project == projectId && it.role == ProjectRole.PI }?.username
        if (pi == null && allowCacheRefill) {
            fillCache()
            return retrievePIFromProjectID(projectId, false)
        }
        return pi
    }

    suspend fun fetchMembership(username: String, allowCacheRefill: Boolean = true): List<String> {
        val result = projectMembers.get()
            .asSequence()
            .filter { it.username == username }
            .map { it.project }
            .toList()

        if (result.isEmpty() && allowCacheRefill) {
            fillCache()
            return fetchMembership(username, allowCacheRefill = false)
        }

        return result
    }
}

private class ProductCategoryCache(private val db: DBContext) {
    private val productCategories = AtomicReference<List<Pair<ProductCategory, Long>>>(emptyList())
    private val fillMutex = Mutex()

    suspend fun fillCache() {
        val before = productCategories.get()
        fillMutex.withLock {
            val current = productCategories.get()
            if (before != current) return

            //PAIR(CATEGORY/ID)
            val productCategoryCollector = HashMap<ProductCategoryIdV2, Pair<ProductCategory, Long>>()

            db.withSession { session ->
                //TODO(HENRIK) FIX DB STATEMENT
                session.sendPreparedStatement(
                    {},
                    """
                        declare product_category_load cursor for
                        select accounting.product_category_to_json(pc, au), pc.id
                        from
                            accounting.product_categories pc join 
                            accounting.accounting_units au on au.id = pc.accounting_unit
                    """
                )

                while (true) {
                    val rows = session.sendPreparedStatement({}, "fetch forward 100 from product_category_load").rows
                    if (rows.isEmpty()) break

                    rows.forEach { row ->
                        val productCategory =
                            defaultMapper.decodeFromString(ProductCategory.serializer(), row.getString(0)!!)
                        val id = row.getLong(1)!!
                        val reference = ProductCategoryIdV2(productCategory.name, productCategory.provider)
                        productCategoryCollector[reference] = Pair(productCategory, id)

                    }
                }

                session.sendPreparedStatement(
                    {},
                    "close product_category_load"
                )

                if (!productCategories.compareAndSet(current, productCategoryCollector.values.toList())) {
                    error("Product Categories were modified even though we have the mutex")
                }
            }
        }
    }

    suspend fun retrieveProductCategory(
        category: ProductCategoryIdV2,
        allowCacheRefill: Boolean = true
    ): ProductCategory? {
        val productCategories = productCategories.get()
        val productCategory = productCategories.find {
            it.first.name == category.name &&
                    it.first.provider == category.provider
        }

        if (productCategory == null && allowCacheRefill) {
            fillCache()
            return retrieveProductCategory(category, false)
        }

        return productCategory?.first
    }
}

private class ProductCache(private val db: DBContext) {
    private val products = AtomicReference<List<Pair<ProductV2, Long>>>(emptyList())
    private val fillMutex = Mutex()

    suspend fun fillCache() {
        val before = products.get()
        fillMutex.withLock {
            val current = products.get()
            if (before != current) return

            val productCollector = HashMap<ProductReferenceV2, Pair<ProductV2, Long>>()

            db.withSession { session ->
                session.sendPreparedStatement(
                    {},
                    """
                        declare product_load cursor for
                        select accounting.product_to_json(p, pc, au, 0), p.id
                        from
                            accounting.products p join
                            accounting.product_categories pc on
                                p.category = pc.id join 
                            accounting.accounting_units au on au.id = pc.accounting_unit
                    """
                )

                while (true) {
                    val rows = session.sendPreparedStatement({}, "fetch forward 100 from product_load").rows
                    if (rows.isEmpty()) break

                    rows.forEach { row ->
                        val product = defaultMapper.decodeFromString(ProductV2.serializer(), row.getString(0)!!)
                        val id = row.getLong(1)!!
                        val reference =
                            ProductReferenceV2(product.name, product.category.name, product.category.provider)
                        productCollector[reference] = Pair(product, id)
                    }
                }

                session.sendPreparedStatement(
                    {},
                    "close product_load"
                )

                if (!products.compareAndSet(current, productCollector.values.toList())) {
                    error("Products were modified even though we have the mutex")
                }
            }
        }
    }

    fun findAllFreeProducts(): List<ProductV2> {
        val products = products.get()
        return products.filter { it.first.category.freeToUse }.map { it.first }
    }

    suspend fun retrieveProduct(
        reference: ProductReferenceV2,
        allowCacheRefill: Boolean = true
    ): Pair<ProductV2, Long>? {
        val products = products.get()
        val product = products.find {
            it.first.name == reference.id &&
                    it.first.category.name == reference.category &&
                    it.first.category.provider == reference.provider
        }

        if (product == null && allowCacheRefill) {
            fillCache()
            return retrieveProduct(reference, false)
        }

        return product
    }
}

private fun <T : Any> Iterable<T>.chunkedSequence(chunkSize: Int): Sequence<Sequence<T>> {
    return iterator().chunkedSequence(chunkSize)
}

private fun <T : Any> Sequence<T>.chunkedSequence(chunkSize: Int): Sequence<Sequence<T>> {
    return iterator().chunkedSequence(chunkSize)
}

private fun <T : Any> Iterator<T>.chunkedSequence(chunkSize: Int): Sequence<Sequence<T>> {
    require(chunkSize > 0) { "chunkSize > 0 ($chunkSize > 0 = false)" }
    val iterator = this

    return generateSequence {
        if (iterator.hasNext()) {
            var count = 0
            generateSequence {
                if (count < chunkSize && iterator.hasNext()) {
                    count++
                    iterator.next()
                } else {
                    null
                }
            }
        } else {
            null
        }
    }
}

private fun <T> Sequence<T>.takeIfNotEmpty(): Sequence<T>? {
    val iterator = iterator()
    if (!iterator.hasNext()) return null
    return Sequence { iterator }
}

private data class ProjectInfo(
    val projectId: String,
    val numericPid: Int,
    val title: String,
    val canConsumeResources: Boolean,
)
