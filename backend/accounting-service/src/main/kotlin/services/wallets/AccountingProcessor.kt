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
import dk.sdu.cloud.grant.api.ProjectWithTitle
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

const val doDebug = false
const val allocationIdCutoff = 5900
val UUID_REGEX =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

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
    val allowSubAllocationsToAllocate: Boolean
) {
    var inProgress: Boolean = false
        private set
    var beginNotBefore: Long = 0L
    var beginNotAfter: Long? = null
    var beginQuota: Long = 0L
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
                    beginGrantedIn != grantedIn
        inProgress = false

        verifyIntegrity()
    }

    fun verifyIntegrity() {
        require((notAfter ?: Long.MAX_VALUE) >= notBefore) { "notAfter >= notBefore ($notAfter >= $notBefore) $this" }
        require(quota >= 0) { "initialBalance >= 0 ($quota >= 0) $this" }
        require((treeUsage ?: 0) <= quota) { "treeUsage <= quota ($treeUsage <= $quota) $this" }
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
        val isProject: Boolean
    ) : AccountingRequest()

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
            val description: ChargeDescription
        ) : Charge()

        data class TotalCharge(
            override val actor: Actor,
            override val owner: String,
            override val dryRun: Boolean,
            override val productCategory: ProductCategoryIdV2,
            val usage: Long,
            val description: ChargeDescription
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

        override var id: Long = -1
    ) : AccountingRequest()
}

sealed class AccountingResponse {
    abstract var id: Long

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

    private val dirtyTransactions = ArrayList<UsageReport.AllocationHistoryEntry>()
    private var nextSynchronization = 0L
    private val transactionPrefix = UUID.randomUUID()
    private val transactionCounter = AtomicLong(0)
    private fun transactionId(): String = "$transactionPrefix-${transactionCounter.getAndIncrement()}"

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

        nextSynchronization = System.currentTimeMillis() + 0
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
        dirtyTransactions.clear()
        wallets.clear()
        allocations.clear()
        walletsIdGenerator = 0
        allocationIdGenerator = 0
        requestsHandled = 0
        slowestRequest = 0L
        slowestRequestName = "?"
        requestTimeSum = 0L
        lastSync = Time.now()
    }

    private suspend fun handleRequest(request: AccountingRequest): AccountingResponse {
        val result = when (request) {
            is AccountingRequest.RootDeposit -> rootDeposit(request)
            is AccountingRequest.Deposit -> deposit(request)
            is AccountingRequest.Update -> update(request)
            is AccountingRequest.Charge -> charge(request).also { println("$request -> $it") }
            is AccountingRequest.RetrieveAllocationsInternal -> retrieveAllocationsInternal(request)
            is AccountingRequest.RetrieveWalletsInternal -> retrieveWalletsInternal(request)
            is AccountingRequest.BrowseSubAllocations -> browseSubAllocations(request)
            is AccountingRequest.RetrieveProviderAllocations -> retrieveProviderAllocations(
                request
            )

            is AccountingRequest.FindRelevantProviders -> findRelevantProviders(request)
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
                        pc.free_to_use
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
                                    freeToUse = freeToUse
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
                                isDirty = isDirty
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
                                    select uuid_generate_v4()::text, now(), now(), :title, false, :parent_id::text, null, false
                                    on conflict (parent, upper(title::text)) do update set title = excluded.title
                                    returning id
                                ),
                                created_user as (
                                    insert into project.project_members (created_at, modified_at, role, username, project_id)
                                    select now(), now(), 'PI', :pi, cp.id
                                    from created_project cp
                                    on conflict (username, project_id) do nothing
                                )
                                select * from created_project
                            """.trimIndent(), debug = true
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
                                isProject = type != GrantApplication.Recipient.PersonalWorkspace
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

                        val now = System.currentTimeMillis()
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
                                isProject = false //TODO(HENRIK) Can gifts be given to other than users?
                            )
                        )
                    }
                }

            }

            calculateFullTreeUsage()
            //This check only runs with debug=true
            //TODO(HENRIK)
            //verifyFromTransactions()
            log.info("Load of DB done.")
        } finally {
            isLoading = false
        }
    }

    /*private suspend fun verifyFromTransactions() {
        if (!doDebug) return
        val tBalances = Array<Long>(allocations.size) { 0 }

        db.withSession { session ->
            session.sendPreparedStatement(
                {},
                """
                    declare transaction_load cursor for
                    select
                        t.id,
                        t.affected_allocation_id,
                        t.change
                    from accounting.transactions t
                """
            )

            while (true) {
                val rows = session.sendPreparedStatement({}, "fetch forward 500 from transaction_load").rows
                if (rows.isEmpty()) break

                for (row in rows) {
                    val transactionId = row.getLong(0)!!
                    val allocationId = row.getLong(1)!!.toInt()
                    val change = row.getLong(2)!!

                    val currBalance = tBalances.getOrNull(allocationId)
                        ?: error("Transaction $transactionId is not pointing to a known allocation!")

                    tBalances[allocationId] = currBalance + change
                }
            }

            session.sendPreparedStatement({}, "close transaction_load")
        }
        for (i in allocations.indices) {
            // We only inspect allocations which are currently known by the database
            val allocation = allocations[i] ?: continue

            if (allocation.currentBalance != tBalances[i]) {
                error("Allocation $i has an unexpected balance according to transaction trace. Expected ${tBalances[i]} but was ${allocation.currentBalance}")
            }

        }
    }*/

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

    fun retrieveUsageOfWallet(
        wallet: ApiWallet
    ): Long {
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
            if (owner.contains("#")) {
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
            allowSubAllocationsToAllocate = allowSubAllocationsToAllocate
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
                    .asSequence()
                    .filter { it?.owner == (project ?: request.username) }
                    .mapNotNull { it?.paysFor?.provider }
                    .toSet()
            }
        }
        val allProviders = providers + products.findAllFreeProducts().map { it.category.provider }.toSet()
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
            currentAlloc.treeUsage = min((currentAlloc.treeUsage ?: 0) + currentAlloc.localUsage, currentAlloc.quota)
            currentAlloc.isDirty = true
            if (currentAlloc.parentAllocation != null) {
                val parent = allocations[currentAlloc.parentAllocation]
                    ?: throw RPCException.fromStatusCode(
                        HttpStatusCode.InternalServerError,
                        "Allocation has parent error"
                    )
                parent.treeUsage = min((parent.treeUsage ?: 0) + currentAlloc.treeUsage!!, parent.quota)
                parent.isDirty = true
            }
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

        val created = createAllocation(
            existingWallet.id,
            request.amount,
            null,
            request.startDate,
            request.endDate,
            null,
            canAllocate = true,
            allowSubAllocationsToAllocate = true
        ).id

        val transactionId = transactionId()
        dirtyTransactions.add(
            UsageReport.AllocationHistoryEntry(
                created.toString(),
                Time.now(),
                UsageReport.Balance(
                    0,
                    0,
                    request.amount
                ),
                UsageReport.HistoryAction.DEPOSIT,
                transactionId,
                UsageReport.Change(
                    0,
                    0,
                    request.amount
                )
            )
        )

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

        val created = createAllocation(
            existingWallet.id,
            request.amount,
            request.parentAllocation,
            notBefore,
            notAfter,
            request.grantedIn,
            canAllocate = if (request.isProject) parent.allowSubAllocationsToAllocate else false,
            allowSubAllocationsToAllocate = if (request.isProject) parent.allowSubAllocationsToAllocate else false,
        ).id

        val transactionId = transactionId()
        dirtyTransactions.add(
            UsageReport.AllocationHistoryEntry(
                created.toString(),
                Time.now(),
                UsageReport.Balance(
                    0,
                    0,
                    request.amount,
                ),
                UsageReport.HistoryAction.DEPOSIT,
                transactionId,
                UsageReport.Change(
                    0,
                    0,
                    request.amount
                )
            )
        )

        return AccountingResponse.Deposit(created)
    }


    // Charge
    // =================================================================================================================

    @Suppress("DEPRECATION")
    private suspend fun charge(request: AccountingRequest.Charge): AccountingResponse {
        if (authorizeProvider(request.actor, request.productCategory)) return AccountingResponse.Error("Forbidden", 403)
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
                println("period: ${requestWithNewUnit.period}, units: ${requestWithNewUnit.units}")
                println("price: $price")
                return when (translateToChargeType(category)) {
                    ChargeType.ABSOLUTE -> {
                        deltaCharge(
                            requestWithNewUnit.toDelta(price)

                        )
                    }

                    ChargeType.DIFFERENTIAL_QUOTA -> {
                        totalCharge(
                            requestWithNewUnit.toTotal(price)
                        )
                    }
                }
            }

            is AccountingRequest.Charge.DeltaCharge -> {
                return deltaCharge(
                    request
                )
            }

            is AccountingRequest.Charge.TotalCharge -> {
                return totalCharge(
                    request
                )
            }
            // Leaving redundant else in case we add more charge types
            else -> {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Unknown charge request type")
            }
        }
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

        val activeAllocations = wallet.allocations.filter { it.isActive() }
        return AccountingResponse.Charge(
            activeAllocations.sumOf { it.localUsage } < activeAllocations.sumOf { it.quota }
        )
    }

    private suspend fun deltaCharge(request: AccountingRequest.Charge.DeltaCharge): AccountingResponse {
        println("Charging Delta: Usage: ${request.usage}, Product: ${request.productCategory}")
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
        println("Charging Total: Usage: ${request.usage}, Product: ${request.productCategory}")
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

    //TODO(HENRIK) Might be to expensive to update with every charge and that it would be fine to update tree usage by
    // doing a full scan of tree every 5 min using O(n*log(n)) but this update should take log(n) (n=total number of allocations in path)
    private fun updateParentTreeUsage(allocation: InternalWalletAllocation, delta: Long): Boolean {
        if (allocation.parentAllocation == null) {
            return true
        } else {
            val parent = allocations[allocation.parentAllocation] ?: return false
            parent.begin()
            parent.treeUsage = min((parent.treeUsage ?: parent.localUsage) + delta, parent.quota)
            parent.isDirty = true
            parent.commit()

            val transactionId = transactionId()
            dirtyTransactions.add(
                UsageReport.AllocationHistoryEntry(
                    parent.id.toString(),
                    Time.now(),
                    UsageReport.Balance(
                        parent.treeUsage!!,
                        parent.localUsage,
                        parent.quota
                    ),
                    //TODO(Henrik) Should be more flexible
                    //Currently only charges hitting this code
                    UsageReport.HistoryAction.CHARGE,
                    transactionId,
                    UsageReport.Change(
                        0,
                        delta,
                        0
                    )
                )
            )
            return if (parent.parentAllocation == null) {
                true
            } else {
                return updateParentTreeUsage(parent, delta)
            }
        }
    }

    private fun chargeAllocation(allocationId: Int, delta: Long): Boolean {
        val internalWalletAllocation = allocations[allocationId] ?: return false
        internalWalletAllocation.begin()
        val willOvercharge = (internalWalletAllocation.localUsage + delta > internalWalletAllocation.quota)
                && (internalWalletAllocation.localUsage < internalWalletAllocation.quota)
        val preChargeTree = internalWalletAllocation.treeUsage ?: 0L
        internalWalletAllocation.localUsage += delta
        internalWalletAllocation.treeUsage = min(
            (internalWalletAllocation.treeUsage ?: internalWalletAllocation.localUsage) + delta,
            internalWalletAllocation.quota
        )
        val postChargeTree = internalWalletAllocation.treeUsage ?: 0
        internalWalletAllocation.isDirty = true
        internalWalletAllocation.commit()
        val transactionId = transactionId()
        val transaction = UsageReport.AllocationHistoryEntry(
            internalWalletAllocation.id.toString(),
            Time.now(),
            UsageReport.Balance(
                internalWalletAllocation.treeUsage ?: internalWalletAllocation.localUsage,
                internalWalletAllocation.localUsage,
                internalWalletAllocation.quota
            ),
            UsageReport.HistoryAction.CHARGE,
            transactionId,
            UsageReport.Change(
                delta,
                postChargeTree - preChargeTree,
                0
            )
        )
        println("TRANSACTION IN CHARGE ALLOC: $transaction")
        dirtyTransactions.add(
            transaction
        )
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

    private fun applyPeriodCharge(
        delta: Long,
        walletAllocations: List<WalletAllocationV2>,
        chargeDescription: ChargeDescription
    ): AccountingResponse {
        println("Applying: $delta")
        if (delta == 0L) return AccountingResponse.Charge(true)
        var activeQuota = 0L
        for (allocation in walletAllocations) {
            if (!allocation.isActive()) continue
            if (allocation.isLocked()) continue
            activeQuota += allocation.quota
        }
        var amountCharged = 0L
        for (allocation in walletAllocations) {
            if (!allocation.isActive()) continue
            if (allocation.isLocked()) continue

            //If we have no quota then just charge all to first allocation, and skip rest
            if (activeQuota == 0L) {
                if (!chargeAllocation(allocation.id.toInt(), delta)) {
                    return AccountingResponse.Error(
                        "Internal Error in charging all to first allocation", 500
                    )
                }
                amountCharged = delta
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
        println("AmountCharged = $amountCharged. Delta = $delta")
        if (amountCharged != delta) {
            val stillActiveAllocations = walletAllocations.filter { it.isActive() && !it.isLocked() }
            val difference = delta - amountCharged
            println("difference $difference")
            if (stillActiveAllocations.isEmpty()) {
                // Have chosen the last allocation since it is most likely to change over time. Not like the
                // first/oldest alloc
                if (!chargeAllocation(walletAllocations.last().id.toInt(), difference)) {
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
        return AccountingResponse.Charge(true)
    }

    private fun applyNonPeriodicCharge(
        totalUsage: Long,
        walletAllocations: List<WalletAllocationV2>,
        description: ChargeDescription
    ): AccountingResponse {
        println("applying: $totalUsage")
        var activeQuota = 0L
        val activeAllocations = walletAllocations.mapNotNull {
            if (it.isActive()) {
                activeQuota += it.quota
                it.id
            } else {
                null
            }
        }
        var totalCharged = 0L
        for (allocation in walletAllocations) {
            val alloc = allocations[allocation.id.toInt()]
                ?: return AccountingResponse.Error("Error on finding walletAllocation", 500)
            alloc.begin()
            val oldValue = alloc.localUsage
            val quota = alloc.quota
            var diff = -oldValue
            alloc.localUsage = 0L
            var localChange = 0L
            var treeChange = 0L
            if (activeAllocations.contains(allocation.id)) {
                val weight = allocation.quota.toDouble() / activeQuota.toDouble()
                val toCharge = round(totalUsage.toDouble() * weight).toLong()
                diff = toCharge - oldValue
                if (diff < 0 && abs(diff) > quota) {
                    diff += quota
                }
                alloc.localUsage = toCharge
                localChange = toCharge
                treeChange = min(diff, alloc.quota)
                alloc.treeUsage = (alloc.treeUsage ?: alloc.localUsage) + treeChange
                totalCharged += toCharge
            }
            alloc.commit()
            val transactionId = transactionId()
            val transaction = UsageReport.AllocationHistoryEntry(
                allocation.id,
                Time.now(),
                UsageReport.Balance(
                    allocation.treeUsage ?: allocation.localUsage,
                    allocation.localUsage,
                    allocation.quota
                ),
                UsageReport.HistoryAction.CHARGE,
                transactionId,
                UsageReport.Change(
                    localChange,
                    treeChange,
                    0
                )
            )
            println("addingTrans non periodic: $transaction")
            dirtyTransactions.add(
                transaction
            )
            updateParentTreeUsage(alloc, min(diff, alloc.quota))
        }

        if (totalCharged != totalUsage) {
            val difference = totalUsage - totalCharged
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
            val error = checkOverlapAncestors(parent, notBefore ?: 0, request.notAfter)
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
        val transactionId = transactionId()
        dirtyTransactions.add(
            UsageReport.AllocationHistoryEntry(
                allocation.id.toString(),
                Time.now(),
                UsageReport.Balance(
                    allocation.treeUsage ?: allocation.localUsage,
                    allocation.localUsage,
                    allocation.quota
                ),
                UsageReport.HistoryAction.UPDATE,
                transactionId,
                UsageReport.Change(
                    0,
                    0,
                    quotaChange
                )
            )
        )

        allocation.commit()
        clampDescendantsOverlap(allocation)
        return AccountingResponse.Update(true)
    }

    // Retrieve Allocations
    // =================================================================================================================
    private fun retrieveAllocationsInternal(request: AccountingRequest.RetrieveAllocationsInternal): AccountingResponse {
        if (request.actor != Actor.System) return AccountingResponse.Error("Forbidden", 403)
        val now = System.currentTimeMillis()
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
                    usage = allocation.localUsage,
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
        val now = System.currentTimeMillis()
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
                    """, debug = true
                    )
                }

                debug.detail("Dealing with transactions")

                dirtyTransactions.chunkedSequence(500).forEach { chunk ->
                    session.sendPreparedStatement(
                        {
                            chunk.split {

                                into("affected_allocations") { it.allocationId }
                                into("new_usages") { it.balance.localUsage }
                                into("new_treeusages") { it.balance.treeUsage }
                                into("new_quotas") { it.balance.quota }
                                into("timestamps") { it.timestamp }
                                into("transaction_ids") { it.transactionId }
                                into("actions") { it.relatedAction.toString() }
                                into("local_change") { it.change.localChange }
                                into("tree_change") { it.change.treeChange }
                                into("quota_change") { it.change.quotaChange }
                            }
                        },
                        """
                            insert into accounting.transaction_history
                                (transaction_id, created_at, affected_allocation, new_tree_usage, new_local_usage, new_quota, action, local_change, tree_change, quota_change) 
                            select
                                unnest(:transaction_ids::text[]),
                                now(),
                                unnest(:affected_allocations::bigint[]),
                                unnest(:new_treeusages::bigint[]),
                                unnest(:new_usages::bigint[]),
                                unnest(:new_quotas::bigint[]),
                                unnest(:actions::text[]),
                                unnest(:local_change::bigint[]),
                                unnest(:tree_change::bigint[]),
                                unnest(:quota_change::bigint[])
                        """
                    )
                }

                dirtyTransactions.asSequence().filter { it.relatedAction == UsageReport.HistoryAction.DEPOSIT }
                    .chunkedSequence(500)
                    .forEach { chunk ->
                        session.sendPreparedStatement(
                            {
                                chunk.split {
                                    into("allocations") { it.allocationId }
                                    into("balances") { it.balance.quota }
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
                                    now(), username, project_id, category_id, balance
                                from notification_data
                            """
                        )
                    }

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
            }

            val depositForProviders = dirtyTransactions.asSequence()
                .filter { it.relatedAction == UsageReport.HistoryAction.DEPOSIT }
                .map { wallets[allocations[it.allocationId.toInt()]!!.associatedWallet]!!.paysFor.provider }
                .toSet()

            if (depositForProviders.isNotEmpty()) {
                depositForProviders.forEach { provider ->
                    val comms = providers.prepareCommunication(provider)
                    DepositNotificationsProvider(provider).pullRequest.call(Unit, comms.client)
                }
            }

            // Clear dirty checks
            wallets.asSequence().filterNotNull().filter { it.isDirty }.forEach { it.isDirty = false }

            allocations.asSequence().filterNotNull().filter { it.isDirty }.forEach { it.isDirty = false }

            dirtyTransactions.clear()
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
    private val projects = AtomicReference<List<Pair<ProjectWithTitle, String>>>(emptyList())
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
            val projects = ArrayList<Pair<ProjectWithTitle, String>>()

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
                        select p.id, p.title, pm.username
                        from project.projects p join project.project_members pm on p.id = pm.project_id
                        where pm.role = 'PI'
                    """.trimIndent()
                )

                while (true) {
                    val rows = session.sendPreparedStatement({}, "fetch forward 500 from project_curs").rows
                    if (rows.isEmpty()) break

                    for (row in rows) {
                        val projectId = row.getString(0)!!
                        val projectTitle = row.getString(1)!!
                        val pi = row.getString(2)!!
                        projects.add(Pair(ProjectWithTitle(projectId, projectTitle), pi))
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
    ): Pair<ProjectWithTitle, String> {
        if (!id.matches(PROJECT_REGEX)) return Pair(ProjectWithTitle(id, id), id)

        val project = projects.get().find { it.first.projectId == id }
        if (project == null && allowCacheRefill) {
            fillCache()
            return retrieveProjectInfoFromId(id, false)
        }
        return project ?: Pair(ProjectWithTitle(id, id), id)
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
