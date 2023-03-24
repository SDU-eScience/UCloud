package dk.sdu.cloud.accounting.services.wallets

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.SimpleProviderCommunication
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.debug.DebugContextType
import dk.sdu.cloud.accounting.api.WalletAllocation as ApiWalletAllocation
import dk.sdu.cloud.accounting.api.Wallet as ApiWallet
import dk.sdu.cloud.accounting.api.WalletOwner as ApiWalletOwner
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.debug.detail
import dk.sdu.cloud.debug.log
import dk.sdu.cloud.grant.api.GrantApplication
import dk.sdu.cloud.grant.api.ProjectWithTitle
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.provider.api.getTime
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

const val doDebug = false

data class WalletSummary(
    val walletId: Long,
    val ownerUsername: String?,
    val ownerProject: String?,
    val category: String,
    val productType: ProductType,
    val chargeType: ChargeType,
    val unitOfPrice: ProductPriceUnit,

    val allocId: Long,
    val allocBalance: Long,
    val allocInitialBalance: Long,
    val allocPath: List<Long>,

    val ancestorId: Long?,
    val ancestorBalance: Long?,
    val ancestorInitialBalance: Long?,

    val notBefore: Long,
    val notAfter: Long?,
)

private data class Wallet(
    val id: Int,
    val owner: String,
    val paysFor: ProductCategoryId,
    val chargePolicy: AllocationSelectorPolicy,
    val productType: ProductType,
    val chargeType: ChargeType,
    val unit: ProductPriceUnit,

    var isDirty: Boolean = false,
)

private data class WalletAllocation(
    val id: Int,
    val associatedWallet: Int,
    val parentAllocation: Int?,

    var notBefore: Long,
    var notAfter: Long?,

    // Always contains the balance was initially allocated (updates are considered an initial allocation)
    var initialBalance: Long,

    // The balance which is currently usable by this wallet allocation
    var currentBalance: Long,

    // How much this local allocation is consuming. Used to compute the change for the remaining hiearchy in case of
    // quotas.
    var localBalance: Long,

    var isDirty: Boolean = false,

    var grantedIn: Long?,
    val maxUsableBalance: Long?,
    val canAllocate: Boolean,
    val allowSubAllocationsToAllocate: Boolean
) {
    var inProgress: Boolean = false
        private set
    var beginNotBefore: Long = 0L
    var beginNotAfter: Long? = null
    var beginInitialBalance: Long = 0L
    var beginCurrentBalance: Long = 0L
    var beginLocalBalance: Long = 0L
    var beginGrantedIn: Long? = null
    var lastBegin: Throwable? = null

    fun begin() {
        if (inProgress) throw RuntimeException("Already in progress", lastBegin)
        if (doDebug) lastBegin = RuntimeException("Invoking begin")

        beginNotBefore = notBefore
        beginNotAfter = notAfter
        beginInitialBalance = initialBalance
        beginLocalBalance = localBalance
        beginCurrentBalance = currentBalance
        beginGrantedIn = grantedIn
        inProgress = true
    }

    fun commit() {
        check(inProgress)

        isDirty =
            isDirty ||
                    beginNotBefore != notBefore ||
                    beginNotAfter != notAfter ||
                    beginInitialBalance != initialBalance ||
                    beginLocalBalance != localBalance ||
                    beginCurrentBalance != currentBalance ||
                    beginGrantedIn != grantedIn
        inProgress = false

        verifyIntegrity()
    }

    fun verifyIntegrity() {
        require((notAfter ?: Long.MAX_VALUE) >= notBefore) { "notAfter >= notBefore ($notAfter >= $notBefore) $this" }
        require(initialBalance >= 0) { "initialBalance >= 0 ($initialBalance >= 0) $this" }
        require(currentBalance <= initialBalance) { "currentBalance <= initialBalance ($currentBalance <= $initialBalance) $this" }
        require(localBalance <= initialBalance) { "localBalance <= initialBalance ($localBalance <= $initialBalance) $this" }
        require(currentBalance <= localBalance) { "currentBalance <= localBalance ($currentBalance <= $localBalance) $this" }
        //legacy allocations does not live up to this requirement. Previous checks noted that this was only a problem
        //for allocations with id below 5900
        if (id > 5900) {
            require(parentAllocation == null || id > parentAllocation) { "id > parentAllocation ($id <= $parentAllocation) $this" }
        }
    }

    fun rollback() {
        check(inProgress)

        notBefore = beginNotBefore
        notAfter = beginNotAfter
        initialBalance = beginInitialBalance
        localBalance = beginLocalBalance
        currentBalance = beginCurrentBalance
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
        val productCategory: ProductCategoryId,
        val amount: Long,
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

    sealed class Charge() : AccountingRequest() {
        abstract val owner: String
        abstract val productCategory: ProductCategoryId
        override var id: Long = -1
        abstract val dryRun: Boolean

        data class Raw(
            override val actor: Actor,
            override val owner: String,
            override val dryRun: Boolean,
            override val productCategory: ProductCategoryId,
            val amount: Long
        ) : Charge()

        data class ProductUse(
            override val actor: Actor,
            override val owner: String,
            override val dryRun: Boolean,
            val units: Long,
            val period: Long,
            val product: ProductReference,
            val resource: String? = null,
        ) : Charge() {
            override val productCategory = ProductCategoryId(product.category, product.provider)
        }
    }

    data class Update(
        override val actor: Actor,
        val allocationId: Int,
        val amount: Long,
        val notBefore: Long,
        val notAfter: Long?,
        override var id: Long = -1,
    ) : AccountingRequest()

    data class RetrieveAllocationsInternal(
        override val actor: Actor,
        val owner: String,
        val category: ProductCategoryId,
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

    data class RetrieveRelevantWalletsProviderNotifications(
        override val actor: Actor,

        val providerId: String,
        val filterOwnerId: String? = null,
        val filterOwnerIsProject: Boolean? = null,
        val filterCategory: String? = null,

        val itemsPerPage: Int?,
        val next: String?,

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
        val allocations: List<SubAllocation>,
        override var id: Long = -1
    ) : AccountingResponse()

    data class RetrieveRelevantWalletsProviderNotifications(
        val wallets: List<WalletSummary>,
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

suspend fun AccountingProcessor.retrieveRelevantWalletsNotifications(
    request: AccountingRequest.RetrieveRelevantWalletsProviderNotifications
): AccountingResponse.RetrieveRelevantWalletsProviderNotifications {
    return sendRequest(request).orThrow()
}

class AccountingProcessor(
    private val db: DBContext,
    private val debug: DebugSystem,
    private val providers: Providers<SimpleProviderCommunication>,
    private val distributedLocks: DistributedLockFactory,
    private val disableMasterElection: Boolean = false,
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
        if (isActiveProcessor) {
            return null
        }
        return activeProcessor.get()?.address
    }

    // State
    // =================================================================================================================
    private val wallets = ArrayList<Wallet?>()
    private var walletsIdGenerator = 0

    private val allocations = ArrayList<WalletAllocation?>()
    private var allocationIdGenerator = 0

    private val requests = Channel<AccountingRequest>(Channel.BUFFERED)

    // NOTE(Dan): Without replays, we risk the async listener missing the response if the coroutine is too slow to start
    private val responses = MutableSharedFlow<AccountingResponse>(replay = 16)
    private var requestIdGenerator = AtomicLong(0)

    private val dirtyTransactions = ArrayList<Transaction>()
    private var nextSynchronization = 0L
    private val transactionPrefix = UUID.randomUUID()
    private val transactionCounter = AtomicLong(0)
    private fun transactionId(): String = "$transactionPrefix-${transactionCounter.getAndIncrement()}"

    private val projects = ProjectCache(db)
    private val products = ProductCache(db)

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

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
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

    private suspend fun becomeMasterAndListen(lock: DistributedLock) {
        val didAcquire = disableMasterElection || lock.acquire()
        if (!didAcquire) return
        log.info("This service has become the master responsible for handling Accounting proccessor events!")
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
                    select<Unit> {
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

    /**
     * Only for testing
     */
    suspend fun clearCache() {
        dirtyTransactions.clear()
        wallets.clear()
        allocations.clear()
        walletsIdGenerator = 0
        allocationIdGenerator = 0
    }

    private suspend fun handleRequest(request: AccountingRequest): AccountingResponse {
        val result = when (request) {
            is AccountingRequest.RootDeposit -> rootDeposit(request)
            is AccountingRequest.Deposit -> deposit(request)
            is AccountingRequest.Update -> update(request)
            is AccountingRequest.Charge -> charge(request)
            is AccountingRequest.RetrieveAllocationsInternal -> retrieveAllocationsInternal(request)
            is AccountingRequest.RetrieveWalletsInternal -> retrieveWalletsInternal(request)
            is AccountingRequest.BrowseSubAllocations -> browseSubAllocations(request)
            is AccountingRequest.RetrieveRelevantWalletsProviderNotifications -> retrieveRelevantWalletsNotifications(
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
                session.sendPreparedStatement(
                    {},
                    """
                    declare wallet_load cursor for
                    select w.id, wo.username, wo.project_id, pc.category, pc.provider, pc.product_type, pc.charge_type, w.allocation_selector_policy, pc.unit_of_price
                    from
                        accounting.wallets w join
                        accounting.wallet_owner wo
                            on w.owned_by = wo.id join
                        accounting.product_categories pc
                            on w.category = pc.id
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
                        val chargeType = ChargeType.valueOf(row.getString(6)!!)
                        val allocationPolicy = AllocationSelectorPolicy.valueOf(row.getString(7)!!)
                        val unit = ProductPriceUnit.valueOf(row.getString(8)!!)
                        val emptySlots = id - wallets.size
                        require(emptySlots >= 0) { "Duplicate wallet detected (or bad logic): $id ${wallets.size} $emptySlots" }
                        repeat(emptySlots) { wallets.add(null) }
                        require(wallets.size == id) { "Bad logic detected wallets[id] != id" }

                        wallets.add(
                            Wallet(
                                id,
                                project ?: username ?: error("Bad wallet owner $id"),
                                ProductCategoryId(category, provider),
                                allocationPolicy,
                                productType,
                                chargeType,
                                unit,
                            )
                        )
                    }
                }

                session.sendPreparedStatement({}, "close wallet_load")
                walletsIdGenerator = wallets.size

                log.info("Loading allocations")
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
                        var startDate = row.getDouble(3)!!.toLong()
                        var endDate = row.getDouble(4)?.toLong()
                        var initialBalance = row.getLong(5)!!
                        var currentBalance = row.getLong(6)!!
                        var localBalance = row.getLong(7)!!
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
                        if (initialBalance < 0) {
                            log.info("Changing initialBalance of allocation $id from $initialBalance to 0")
                            initialBalance = 0
                            isDirty = true
                        }

                        if (currentBalance > initialBalance) {
                            log.info("Changing currentBalance of allocation $id from $currentBalance to $initialBalance")
                            currentBalance = initialBalance
                            isDirty = true
                        }

                        if (localBalance > initialBalance) {
                            log.info("Changing localBalance of allocation $id from $localBalance to $initialBalance")
                            localBalance = initialBalance
                            isDirty = true
                        }

                        if (currentBalance > localBalance) {
                            log.info("Levels CurrentBalance with localbalance of allocation $id from $currentBalance to $localBalance")
                            currentBalance = localBalance
                            isDirty = true
                        }

                        allocations.add(
                            WalletAllocation(
                                id,
                                walletOwner,
                                allocationPath.split(".").let { path ->
                                    if (path.size <= 1) null
                                    else path[path.lastIndex - 1]
                                }?.toIntOrNull(),
                                startDate,
                                endDate,
                                initialBalance,
                                currentBalance,
                                localBalance,
                                grantedIn = grantedIn,
                                maxUsableBalance = null,
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
                        val category = ProductCategoryId(row.getString(3)!!, row.getString(4)!!)
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


                        val sourceAllocation = allocations.find { it.balance >= balance } ?: allocations.firstOrNull()
                        ?: throw RPCException("Unable to claim this gift", HttpStatusCode.BadRequest)

                        deposit(
                            AccountingRequest.Deposit(
                                ActorAndProject(Actor.System, null).actor,
                                receiver,
                                sourceAllocation.id.toInt(),
                                balance,
                                notBefore = now,
                                notAfter = null,
                                isProject = false //TODO Can gifts be given to other than users?
                            )
                        )
                    }
                }

            }

            //This check only runs with debug=true
            verifyFromTransactions()
            log.info("Load of DB done.")
        } finally {
            isLoading = false
        }
    }

    private suspend fun verifyFromTransactions() {
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
    }

    // Utilities for managing state
    // =================================================================================================================

    fun retrieveBalanceFromProduct(owner: String, productCategory: ProductCategoryId): Long? {
        val wallet = findWallet(owner, productCategory)
        return if (wallet != null) {
            retrieveBalanceOfWallet(wallet.toApiWallet())
        } else {
            null
        }
    }

    fun retrieveBalanceOfWallet(
        wallet: ApiWallet
    ): Long {
        val internalWallet = when (val owner = wallet.owner) {
            is ApiWalletOwner.Project -> findWallet(owner.projectId, wallet.paysFor)
            is ApiWalletOwner.User -> findWallet(owner.username, wallet.paysFor)
        }
        return if (internalWallet == null) {
            0L
        } else {
            allocations.filter { it != null && it.associatedWallet == internalWallet.id }
                .mapNotNull { retrieveBalanceOfAllocation(it!!.toApiAllocation()) }
                .sum()
        }
    }

    fun retrieveBalanceOfAllocation(
        allocation: ApiWalletAllocation
    ): Long {
        return allocations[allocation.id.toInt()]?.currentBalance ?: 0L
    }

    // Adds maxUsableBalance to walletAllocations in a wallet
    fun includeMaxUsableBalance(
        wallet: ApiWallet,
        filterEmptyAllocations: Boolean? = true
    ): ApiWallet {
        var returnWallet = wallet
        val internalWallet = when (val owner = wallet.owner) {
            is ApiWalletOwner.Project -> findWallet(owner.projectId, wallet.paysFor)
            is ApiWalletOwner.User -> findWallet(owner.username, wallet.paysFor)
        }
        return if (internalWallet == null) {
            returnWallet
        } else {
            val allocationsWithMaxUsableBalance = if (filterEmptyAllocations != null && filterEmptyAllocations) {
                allocations
                    .filter { it != null && it.associatedWallet == internalWallet.id }
                    .mapNotNull { it!!.copy(maxUsableBalance = calculateMaxUsableBalance(it)) }
                    .map { it.toApiAllocation() }
                    .filter { it.balance > 0 }
            } else {
                allocations
                    .filter { it != null && it.associatedWallet == internalWallet.id }
                    .mapNotNull { it!!.copy(maxUsableBalance = calculateMaxUsableBalance(it)) }
                    .map { it.toApiAllocation() }
            }
            returnWallet = wallet.copy(allocations = allocationsWithMaxUsableBalance)
            returnWallet
        }
    }

    //Goes through entire allocation tree to find the lowest possible amount that can be charged without problems
    private fun calculateMaxUsableBalance(
        allocation: WalletAllocation
    ): Long {
        var current: WalletAllocation? = allocation
        var maxUsableBalance = min(current!!.currentBalance, current.localBalance)
        while (current != null) {
            val parentOfCurrent = current.parentAllocation
            current = if (parentOfCurrent == null) {
                null
            } else {
                val parentAllocation = allocations[parentOfCurrent]
                if (parentAllocation == null) {
                    return maxUsableBalance
                } else {
                    maxUsableBalance = min(maxUsableBalance, parentAllocation.currentBalance)
                }
                parentAllocation
            }
        }
        return maxUsableBalance
    }

    private fun findWallet(owner: String, category: ProductCategoryId): Wallet? {
        return wallets.find { it?.owner == owner && it.paysFor == category }
    }

    private suspend fun createWallet(owner: String, category: ProductCategoryId): Wallet? {
        val chargeType = products.retrieveChargeType(category) ?: return null
        val productType = products.retrieveProductType(category) ?: return null
        val selectorPolicy = AllocationSelectorPolicy.EXPIRE_FIRST
        val unit = products.retrieveUnitType(category) ?: return null
        val wallet = Wallet(
            walletsIdGenerator++,
            owner,
            category,
            selectorPolicy,
            productType,
            chargeType,
            unit,
            isDirty = true
        )

        wallets.add(wallet)
        return wallet
    }

    private fun createAllocation(
        wallet: Int,
        balance: Long,
        parentAllocation: Int?,
        notBefore: Long,
        notAfter: Long?,
        grantedIn: Long?,
        canAllocate: Boolean,
        allowSubAllocationsToAllocate: Boolean
    ): WalletAllocation {
        val alloc = WalletAllocation(
            allocationIdGenerator++,
            wallet,
            parentAllocation,
            notBefore,
            notAfter,
            balance,
            balance,
            balance,
            isDirty = true,
            grantedIn = grantedIn,
            maxUsableBalance = null,
            canAllocate = canAllocate,
            allowSubAllocationsToAllocate = allowSubAllocationsToAllocate
        )

        allocations.add(alloc)
        return alloc
    }

    private fun Wallet.toApiWallet(): ApiWallet {
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
            },
            chargePolicy,
            productType,
            chargeType,
            unit
        )
    }

    private fun WalletAllocation.toApiAllocation(): ApiWalletAllocation {
        return ApiWalletAllocation(
            id.toString(),
            run {
                val reversePath = ArrayList<Int>()
                var current: WalletAllocation? = allocations[id]
                while (current != null) {
                    reversePath.add(current.id)

                    val parent = current.parentAllocation
                    current = if (parent == null) null else allocations[parent]
                }

                reversePath.reversed().map { it.toString() }
            },
            currentBalance,
            initialBalance,
            localBalance,
            notBefore,
            notAfter,
            grantedIn,
            maxUsableBalance = calculateMaxUsableBalance(this),
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
        parent: WalletAllocation,
        notBefore: Long,
        notAfter: Long?
    ): AccountingResponse.Error? {
        var current: WalletAllocation? = parent
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

    private fun clampDescendantsOverlap(parent: WalletAllocation) {
        val watchSet = hashSetOf(parent.id)
        // NOTE(Dan): The hierarchy is immutable after creation and allocation IDs are monotonically increasing. As a
        // result, we don't have to search any ID before the root, and we only have to perform a single loop over the
        // allocations to get all results.
        for (i in (parent.id + 1) until allocations.size) {
            val alloc = allocations[i]!!
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

    private fun overlapError(root: WalletAllocation): AccountingResponse.Error {
        var latestBefore = Long.MIN_VALUE
        var earliestAfter = Long.MAX_VALUE

        var c: WalletAllocation? = root
        while (c != null) {
            if (c.notBefore >= latestBefore) latestBefore = c.notBefore
            if ((c.notAfter ?: Long.MAX_VALUE) <= earliestAfter) earliestAfter = c.notAfter ?: Long.MAX_VALUE

            val parentOfCurrent = c.parentAllocation
            c = if (parentOfCurrent == null) null else allocations[parentOfCurrent]
        }

        val b = dateString(latestBefore)
        val a = dateString(earliestAfter)

        return AccountingResponse.Error(
            "Allocation period is outside of allowed range. It must be between $b and $a."
        )
    }

    // Deposits
    // =================================================================================================================
    private suspend fun rootDeposit(request: AccountingRequest.RootDeposit): AccountingResponse {
        if (request.amount < 0) return AccountingResponse.Error("Cannot deposit with a negative balance")
        if (request.actor != Actor.System) {
            return AccountingResponse.Error("Only UCloud administrators can perform a root deposit")
        }

        val existingWallet = findWallet(request.owner, request.productCategory)
            ?: createWallet(request.owner, request.productCategory)
            ?: return AccountingResponse.Error("Unknown product category.")

        val start = getTime(atStartOfDay = true)
        val created = createAllocation(
            existingWallet.id,
            request.amount,
            null,
            start,
            null,
            null,
            canAllocate = true,
            allowSubAllocationsToAllocate = true
        ).id
        val transactionId = transactionId()
        dirtyTransactions.add(
            Transaction.Deposit(
                null,
                start,
                null,
                request.amount,
                "_ucloud",
                "Root deposit",
                created.toString(),
                System.currentTimeMillis(),
                request.productCategory,
                transactionId,
                transactionId
            )
        )
        if (request.forcedSync) {
            attemptSynchronize(forced = true)
        }
        return AccountingResponse.RootDeposit(created)
    }

    fun checkIfSubAllocationIsAllowed(allocs: List<String>): Boolean {
        val foundAllocations = allocations.mapNotNull { savedAlloc ->
            val found = allocs.find { it.toInt() == savedAlloc?.id }
            if (found != null) savedAlloc else null
        }
        return foundAllocations.all { it.allowSubAllocationsToAllocate }
    }

    private suspend fun deposit(request: AccountingRequest.Deposit): AccountingResponse {
        if (request.amount < 0) return AccountingResponse.Error("Cannot deposit with a negative balance")

        val parent = allocations.getOrNull(request.parentAllocation)
            ?: return AccountingResponse.Error("Bad parent allocation")

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

        run {
            val error = checkOverlapAncestors(parent, notBefore, request.notAfter)
            if (error != null) return error
        }

        val parentWallet = wallets[parent.associatedWallet]!!

        val existingWallet = findWallet(request.owner, parentWallet.paysFor)
            ?: createWallet(request.owner, parentWallet.paysFor)
            ?: return AccountingResponse.Error("Internal error - Product category no longer exists ${parentWallet.paysFor}")

        val created = createAllocation(
            existingWallet.id,
            request.amount,
            request.parentAllocation,
            notBefore,
            request.notAfter,
            request.grantedIn,
            canAllocate = if (request.isProject) parent.allowSubAllocationsToAllocate else false,
            allowSubAllocationsToAllocate = if (request.isProject) parent.allowSubAllocationsToAllocate else false,
        ).id

        val now = System.currentTimeMillis()
        val transactionId = transactionId()
        dirtyTransactions.add(
            Transaction.Deposit(
                null,
                notBefore,
                request.notAfter,
                request.amount,
                request.actor.safeUsername(),
                "Deposit",
                created.toString(),
                now,
                parentWallet.paysFor,
                transactionId,
                transactionId
            )
        )

        return AccountingResponse.Deposit(created)
    }

    // Charge
    // =================================================================================================================
    private suspend fun charge(request: AccountingRequest.Charge): AccountingResponse {
        val charge = describeCharge(request.actor, request)
            ?: return AccountingResponse.Error("Could not find product information in charge request.")
        if (charge.amount < 0) return AccountingResponse.Error("Cannot charge a negative amount")
        if (charge.isFree) return AccountingResponse.Charge(true)

        val wallet = findWallet(request.owner, request.productCategory)
            ?: return AccountingResponse.Charge(false)

        val myAllocations = allocations.filter {
            it?.associatedWallet == wallet.id && it.isValid(System.currentTimeMillis())
        }.filterNotNull()

        if (myAllocations.isEmpty()) {
            return AccountingResponse.Charge(false)
        }

        val initialTransactionId = transactionId()
        val now = System.currentTimeMillis()

        when (wallet.chargeType) {
            ChargeType.ABSOLUTE -> {
                var idx = 0
                var charged = 0L
                while (charged < charge.amount && idx < myAllocations.size) {
                    val alloc = myAllocations[idx++]
                    charged += attemptAbsoluteCharge(
                        charge,
                        alloc.id,
                        charge.amount - charged,
                        now,
                        initialTransactionId,
                        idx == 0,
                        request.dryRun,
                    )
                }

                return AccountingResponse.Charge(charged == charge.amount)
            }

            ChargeType.DIFFERENTIAL_QUOTA -> {
                var charged = 0L

                run {
                    // Strategy:
                    // - For each allocation (even if charge remaining is 0):
                    //   - We return the amount that _WE_ are consuming immediately to the hierarchy.
                    //   - Then we determine the largest current balance we can charge (up to amount)
                    //   - This charge is deducted from currentBalance in all allocations
                    //   - This charge is deducted from localBalance only in the leaf
                    // - If the amount is still > 0 then this is split, as evenly as possible, amongst all allocations
                    //   - The split amount is deducted, without further checks, on currentBalance (and local in leaf)

                    // NOTE(Dan): Charge all allocations involved in this transaction. This can potentially
                    // return credits to an allocation if usage was lower than last time a charge was made.
                    // As a result, we also process _all_ allocations even if the amount ends up being 0.
                    var idx = 0
                    while (idx < myAllocations.size) {
                        val alloc = myAllocations[idx++]
                        charged += attemptDifferentialCharge(
                            charge,
                            alloc.id,
                            charge.amount - charged,
                            now,
                            initialTransactionId,
                            idx == 0,
                            request.dryRun
                        )
                    }
                }

                val success = charged == charge.amount
                if (!success) {
                    // NOTE(Dan): After charging all, we still have some which hasn't been charged. Unlike
                    // ABSOLUTE payment, we keep charging and going into the negatives. This is based on the
                    // assumption that an absolute charge is typically upfront or soon after consumption and
                    // differential are done some time after consumption.

                    val amountMissing = charge.amount - charged
                    val amountToDeductPerAllocation = amountMissing / myAllocations.size

                    var idx = 0
                    while (idx < myAllocations.size) {
                        val isFirst = idx == 0
                        val toCharge = amountToDeductPerAllocation +
                                (if (!isFirst) 0 else amountMissing % myAllocations.size)

                        deductWithoutChecks(
                            charge,
                            myAllocations[idx++].id,
                            toCharge,
                            now,
                            initialTransactionId,
                            request.dryRun
                        )
                    }
                }

                return AccountingResponse.Charge(success)
            }
        }
    }

    private data class ChargeDescription(
        val actor: Actor,
        val units: Long?,
        val periods: Long?,
        val resource: String?,
        val productForeignKey: Long?,
        val amount: Long,
        val isFree: Boolean,
    )

    private suspend fun describeCharge(actor: Actor, request: AccountingRequest.Charge): ChargeDescription? {
        return when (request) {
            is AccountingRequest.Charge.ProductUse -> {
                val (product, productKey) = products.retrieveProduct(request.product) ?: return null
                ChargeDescription(
                    actor = actor,
                    units = request.units,
                    periods = request.period,
                    resource = request.resource,
                    amount = product.pricePerUnit * request.units * request.period,
                    productForeignKey = productKey,
                    isFree = product.freeToUse
                )
            }

            is AccountingRequest.Charge.Raw -> ChargeDescription(
                actor = actor,
                units = null,
                periods = null,
                resource = null,
                productForeignKey = null,
                amount = request.amount,
                isFree = false,
            )
        }
    }

    private fun attemptAbsoluteCharge(
        charge: ChargeDescription,
        allocation: Int,
        amount: Long,
        now: Long,
        initialId: String,
        isInitial: Boolean,
        dryRun: Boolean,
    ): Long {
        var maximumCharge = amount

        var current: WalletAllocation? = allocations[allocation]
        val sourceWallet = wallets[allocations[allocation]!!.associatedWallet]!!
        while (current != null) {
            maximumCharge = min(maximumCharge, current.currentBalance)

            val parent = current.parentAllocation
            current = if (parent == null) null else allocations[parent]
        }

        if (maximumCharge < 0L) maximumCharge = 0L

        current = allocations[allocation]
        while (current != null) {
            current.begin()
            current.currentBalance -= maximumCharge
            if (current.id == allocation) current.localBalance -= maximumCharge

            if (!dryRun) {
                current.commit()
                dirtyTransactions.add(
                    Transaction.Charge(
                        allocation.toString(),
                        charge.productForeignKey?.toString(),
                        charge.periods,
                        charge.units,
                        current.id.toString(),
                        -maximumCharge,
                        charge.actor.safeUsername(),
                        "Charge",
                        now,
                        sourceWallet.paysFor,
                        initialId,
                        if (isInitial && current.id == allocation) initialId else transactionId()
                    )
                )
            } else {
                current.rollback()
            }

            val parent = current.parentAllocation
            current = if (parent == null) null else allocations[parent]
        }
        return maximumCharge
    }

    private fun attemptDifferentialCharge(
        charge: ChargeDescription,
        allocation: Int,
        amount: Long,
        now: Long,
        initialId: String,
        isInitial: Boolean,
        dryRun: Boolean,
    ): Long {
        var maximumCharge = amount

        val initial = allocations[allocation]!!
        val sourceWallet = wallets[initial.associatedWallet]!!
        var current: WalletAllocation? = initial
        val leafUsage = initial.initialBalance - initial.localBalance
        while (current != null) {
            current.begin()
            current.currentBalance += leafUsage
            if (current.id == allocation) current.localBalance += leafUsage

            maximumCharge = min(maximumCharge, current.currentBalance)

            val parent = current.parentAllocation
            current = if (parent == null) null else allocations[parent]
        }

        current = allocations[allocation]
        while (current != null) {
            current.currentBalance -= maximumCharge
            if (current.id == allocation) current.localBalance -= maximumCharge

            if (!dryRun) {
                dirtyTransactions.add(
                    Transaction.Charge(
                        allocation.toString(),
                        charge.productForeignKey?.toString(),
                        charge.periods,
                        charge.units,
                        current.id.toString(),
                        current.currentBalance - current.beginCurrentBalance,
                        charge.actor.safeUsername(),
                        "Charge",
                        now,
                        sourceWallet.paysFor,
                        initialId,
                        if (isInitial && current.id == allocation) initialId else transactionId()
                    )
                )

                current.commit()
            } else {
                current.rollback()
            }

            val parent = current.parentAllocation
            current = if (parent == null) null else allocations[parent]
        }
        return maximumCharge
    }

    private fun deductWithoutChecks(
        charge: ChargeDescription,
        allocation: Int,
        amount: Long,
        now: Long,
        initialId: String,
        dryRun: Boolean,
    ) {
        val sourceWallet = wallets[allocations[allocation]!!.associatedWallet]!!
        var current: WalletAllocation? = allocations[allocation]
        while (current != null) {
            current.begin()

            current.currentBalance -= amount
            if (current.id == allocation) current.localBalance -= amount

            if (!dryRun) {
                current.commit()

                dirtyTransactions.add(
                    Transaction.Charge(
                        allocation.toString(),
                        charge.productForeignKey?.toString(),
                        charge.periods,
                        charge.units,
                        current.id.toString(),
                        amount,
                        charge.actor.safeUsername(),
                        "Charge",
                        now,
                        sourceWallet.paysFor,
                        initialId,
                        transactionId()
                    )
                )
            } else {
                current.rollback()
            }

            val parent = current.parentAllocation
            current = if (parent == null) null else allocations[parent]
        }
    }

    // Update
    // =================================================================================================================
    private suspend fun update(request: AccountingRequest.Update): AccountingResponse {
        if (request.amount < 0) return AccountingResponse.Error("Cannot update to a negative balance")
        val allocation = allocations.getOrNull(request.allocationId)
            ?: return AccountingResponse.Error("Invalid allocation id supplied")

        val wallet = wallets[allocation.associatedWallet]
            ?: return AccountingResponse.Error("Invalid allocation id supplied")

        if (request.actor != Actor.System) {
            val parentAllocation =
                if (allocation.parentAllocation == null) null
                else allocations[allocation.parentAllocation]

            if (parentAllocation == null) {
                return AccountingResponse.Error("You are not allowed to manage this allocation.")
            }

            val role = projects.retrieveProjectRole(
                request.actor.safeUsername(),
                wallets[parentAllocation.associatedWallet]!!.owner
            )

            if (role?.isAdmin() != true) {
                return AccountingResponse.Error("You are not allowed to manage this allocation.")
            }
        }


        val parent = if (allocation.parentAllocation == null) null else allocations[allocation.parentAllocation]

        val notBefore = if (parent != null) {
            max(parent.notBefore, request.notBefore)
        } else {
            request.notBefore
        }

        if (parent != null) {
            val error = checkOverlapAncestors(parent, request.notBefore, request.notAfter)
            if (error != null) return error
        }

        allocation.begin()
        val currentUse = allocation.initialBalance - allocation.currentBalance
        val currentLocal = allocation.initialBalance - allocation.localBalance
        allocation.initialBalance = request.amount
        allocation.currentBalance = request.amount
        allocation.localBalance = request.amount
        allocation.notBefore = notBefore
        allocation.notAfter = request.notAfter

        if (wallet.chargeType == ChargeType.DIFFERENTIAL_QUOTA) {
            // NOTE(Dan): Without this, we will break the entire tree as it has recorded usage which otherwise
            // won't be returned on next charge. This is not true for ABSOLUTE, which doesn't have this property
            // and as a result it is correct not to set the localBalance in that case.
            allocation.currentBalance -= currentUse
            allocation.localBalance -= currentLocal
        }

        val transactionId = transactionId()
        dirtyTransactions.add(
            Transaction.AllocationUpdate(
                startDate = allocation.notBefore,
                endDate = allocation.notAfter,
                change = allocation.currentBalance - allocation.beginCurrentBalance,
                actionPerformedBy = request.actor.safeUsername(),
                description = "Allocation update",
                affectedAllocationId = allocation.id.toString(),
                timestamp = System.currentTimeMillis(),
                resolvedCategory = wallet.paysFor,
                initialTransactionId = transactionId,
                transactionId = transactionId,
            )
        )

        allocation.commit()
        clampDescendantsOverlap(allocation)
        return AccountingResponse.Update(true)
    }

    // Retrieve Allocations
    // =================================================================================================================
    private suspend fun retrieveAllocationsInternal(request: AccountingRequest.RetrieveAllocationsInternal): AccountingResponse {
        if (request.actor != Actor.System) return AccountingResponse.Error("Forbidden", 403)
        val now = System.currentTimeMillis()
        val wallet = findWallet(request.owner, request.category)
            ?: return AccountingResponse.Error("Unknown wallet requested")

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
        if (request.actor != Actor.System) return AccountingResponse.Error("Forbidden", 403)

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

    val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    private suspend fun retrieveRelevantWalletsNotifications(request: AccountingRequest.RetrieveRelevantWalletsProviderNotifications): AccountingResponse {
        // NOTE(Henrik): We fetch all the relevant data.
        //
        // The first we retrieve the relevant wallets, filteres them by request filters and creates pagination of
        // the wallets
        // The second part retrieves all relevant allocations along with their ancestors.
        //
        // This summary is then used to build the summary required by the provider.


        var providerWallets = wallets.filter { it?.paysFor?.provider == request.providerId }.mapNotNull { it }

        if (request.filterCategory != null) {
            val filtered = providerWallets.filter { it.paysFor.name == request.filterCategory }
            providerWallets = filtered
        }
        if (request.filterOwnerId != null) {
            val filtered = providerWallets.filter { it.owner == request.filterOwnerId }
            providerWallets = filtered
        }
        if (request.filterOwnerIsProject != null) {
            val filtered = providerWallets.filter { it.owner.matches(UUID_REGEX) == true }
            providerWallets = filtered
        }

        if (request.next != null) {
            val startIndex = providerWallets.indexOfFirst { it.id == request.next.toInt() }
            val limited = providerWallets.subList(startIndex, providerWallets.size - 1)
            providerWallets = limited
        }

        val walletsNeededForPage = providerWallets.chunked(request.itemsPerPage ?: 50).firstOrNull()
            ?: return AccountingResponse.RetrieveRelevantWalletsProviderNotifications(
                emptyList()
            )

        val allocs = walletsNeededForPage.map { wallet ->
            val allocsForWallet = allocations.filter { it?.associatedWallet == wallet.id }.mapNotNull { it }
            Pair(wallet, allocsForWallet)
        }

        val summaries = ArrayList<WalletSummary>()

        allocs.forEach { wal ->
            val wallet = wal.first
            val isProject = wallet.owner.matches(UUID_REGEX)
            val associatedAllocations = wal.second
            associatedAllocations.forEach { alloc ->
                val allocationPath = getAllocationsPath(alloc.id).map { it.toLong() }
                val parent = alloc.parentAllocation
                summaries.add(
                    WalletSummary(
                        wallet.id.toLong(),
                        if (isProject) null else wallet.owner,
                        if (isProject) wallet.owner else null,
                        wallet.paysFor.name,
                        wallet.productType,
                        wallet.chargeType,
                        wallet.unit,
                        alloc.id.toLong(),
                        alloc.currentBalance,
                        alloc.initialBalance,
                        allocationPath,
                        parent?.toLong(),
                        if (parent != null) {
                            allocations[parent]?.currentBalance
                        } else {
                            null
                        },
                        if (parent != null) {
                            allocations[parent]?.initialBalance
                        } else {
                            null
                        },
                        alloc.notBefore,
                        alloc.notAfter
                    )
                )
            }
        }

        return AccountingResponse.RetrieveRelevantWalletsProviderNotifications(
            summaries
        )
    }

    suspend fun maxUsableBalanceForProduct(owner: String, categoryId: ProductCategoryId): Long {
        val wallet = findWallet(owner, categoryId) ?: return 0L
        val maxUsableBalances = allocations.mapNotNull { it }.filter { it.associatedWallet == wallet.id }
            .map { calculateMaxUsableBalance(it) }
        return maxUsableBalances.sum()
    }


    //Is Autherized in AccountingService
    private suspend fun browseSubAllocations(
        request: AccountingRequest.BrowseSubAllocations
    ): AccountingResponse {
        val currentProjectWalletsIds = wallets.mapNotNull { if (it?.owner == request.owner) it.id else null }.toSet()
        val currentProjectAllocations = mutableListOf<Int>()
        val subAllocations = mutableListOf<WalletAllocation>()
        allocations.forEach {
            if (it != null && currentProjectWalletsIds.contains(it.associatedWallet)) {
                currentProjectAllocations.add(it.id)
            }
            if (it != null && currentProjectAllocations.contains(it.parentAllocation)) {
                subAllocations.add(it)
            }
        }

        val list = subAllocations.mapNotNull { allocation ->
            val wall = wallets[allocation.associatedWallet]
            if (wall != null) {
                val projectInfo = projects.retrieveProjectInfoFromId(wall.owner)
                SubAllocation(
                    id = allocation.id.toString(),
                    path = allocation.toApiAllocation().allocationPath.joinToString(separator = "."),
                    startDate = allocation.notBefore,
                    endDate = allocation.notAfter,
                    productCategoryId = wall.paysFor,
                    productType = wall.productType,
                    chargeType = wall.chargeType,
                    unit = wall.unit,
                    workspaceId = projectInfo.first.projectId,
                    workspaceTitle = projectInfo.first.title,
                    workspaceIsProject = wall.owner.matches(UUID_REGEX),
                    projectPI = projectInfo.second,
                    remaining = allocation.currentBalance,
                    initialBalance = allocation.initialBalance
                )
            } else null
        }

        val filteredList = if (request.query == null) {
            list
        } else {
            val query = request.query
            list.filter {
                it.workspaceTitle.contains(query) ||
                        it.productCategoryId.name.contains(query) ||
                        it.productCategoryId.provider.contains(query)
            }
        }
        return AccountingResponse.BrowseSubAllocations(filteredList)
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
            log.info("Handled $requestsHandled in ${timeDiff}ms. " +
                    "Average speed was ${requestTimeSum / requestsHandled} nanoseconds. " +
                    "Slowest request was: $slowestRequestName at $slowestRequest nanoseconds.")

            slowestRequestName = ""
            slowestRequest = 0
            requestsHandled = 0
            requestTimeSum = 0L
            lastSync = now
        }

        log.info("Synching")
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
                                    var current: WalletAllocation? = allocations[it.id]
                                    while (current != null) {
                                        reversePath.add(current.id)

                                        val parent = current.parentAllocation
                                        current = if (parent == null) null else allocations[parent]
                                    }
                                    reversePath.reversed().joinToString(".")
                                }
                                into("associated_wallets") { it.associatedWallet.toLong() }
                                into("balances") { it.currentBalance }
                                into("initial_balances") { it.initialBalance }
                                into("local_balances") { it.localBalance }
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

                debug.detail("Dealing with transactions")

                dirtyTransactions.chunkedSequence(500).forEach { chunk ->
                    session.sendPreparedStatement(
                        {
                            chunk.split {
                                into("types") {
                                    when (it) {
                                        is Transaction.AllocationUpdate -> "allocation_update"
                                        is Transaction.Charge -> "charge"
                                        is Transaction.Deposit -> "deposit"
                                    }
                                }

                                into("affected_allocations") { it.affectedAllocationId.toLong() }
                                into("performed_by") { it.actionPerformedBy }
                                into("changes") { it.change }
                                into("descriptions") { it.description }
                                into("source_allocations") {
                                    when (it) {
                                        is Transaction.Deposit -> it.sourceAllocationId
                                        is Transaction.Charge -> it.sourceAllocationId
                                        is Transaction.AllocationUpdate -> null
                                    }
                                }
                                into("product_ids") {
                                    if (it is Transaction.Charge) it.productId else null
                                }
                                into("periods") {
                                    if (it is Transaction.Charge) it.periods else null
                                }
                                into("units") {
                                    if (it is Transaction.Charge) it.units else null
                                }
                                into("start_dates") {
                                    when (it) {
                                        is Transaction.Deposit -> it.startDate
                                        is Transaction.Charge -> null
                                        is Transaction.AllocationUpdate -> it.startDate
                                    }
                                }
                                into("end_dates") {
                                    when (it) {
                                        is Transaction.Deposit -> it.endDate
                                        is Transaction.Charge -> null
                                        is Transaction.AllocationUpdate -> it.endDate
                                    }
                                }
                                into("transaction_ids") { it.transactionId }
                                into("initial_transaction_ids") { it.initialTransactionId }
                            }
                        },
                        """
                            insert into accounting.transactions
                                (type, created_at, affected_allocation_id, action_performed_by, change, description, 
                                 source_allocation_id, product_id, periods, units, start_date, end_date, transaction_id, 
                                 initial_transaction_id) 
                            select
                                unnest(:types::accounting.transaction_type[]),
                                now(),
                                unnest(:affected_allocations::bigint[]),
                                unnest(:performed_by::text[]),
                                unnest(:changes::bigint[]),
                                unnest(:descriptions::text[]),
                                unnest(:source_allocations::bigint[]),
                                unnest(:product_ids::bigint[]),
                                unnest(:periods::bigint[]),
                                unnest(:units::bigint[]),
                                to_timestamp(unnest(:start_dates::bigint[]) / 1000),
                                to_timestamp(unnest(:end_dates::bigint[]) / 1000),
                                unnest(:transaction_ids::text[]),
                                unnest(:initial_transaction_ids::text[])
                        """
                    )
                }

                dirtyTransactions.asSequence().filterIsInstance<Transaction.Deposit>().chunkedSequence(500)
                    .forEach { chunk ->
                        session.sendPreparedStatement(
                            {
                                chunk.split {
                                    into("allocations") { it.affectedAllocationId }
                                    into("balances") { it.change }
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
                .filterIsInstance<Transaction.Deposit>()
                .map { wallets[allocations[it.affectedAllocationId.toInt()]!!.associatedWallet]!!.paysFor.provider }
                .toSet()

            if (depositForProviders.isNotEmpty()) {
                depositForProviders.forEach { provider ->
                    val comms = providers.prepareCommunication(provider)
                    DepositNotificationsProvider(provider).pullRequest.call(Unit, comms.client)
                }
            }

            //Clear dirty checks
            wallets.asSequence().filterNotNull().filter { it.isDirty }.forEach { it.isDirty = false }

            allocations.asSequence().filterNotNull().filter { it.isDirty }.forEach { it.isDirty = false }

            dirtyTransactions.clear()
            nextSynchronization = Time.now() + 30_000
            log.info("Synching of DB is done!")
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
                column("Type", (ChargeType.DIFFERENTIAL_QUOTA.name.length * 1.5).toInt())
                column("Dirty")

                for (wallet in wallets) {
                    if (wallet == null) continue
                    cell(wallet.id)
                    cell(wallet.owner)
                    cell(wallet.paysFor.name)
                    cell(wallet.chargeType)
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
            column("Initial", 20)
            column("Current", 20)
            column("Local", 20)
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
                cell(alloc.initialBalance)
                cell(alloc.currentBalance)
                cell(alloc.localBalance)
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

    val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    suspend fun retrieveProjectInfoFromId(
        id: String,
        allowCacheRefill: Boolean = true
    ): Pair<ProjectWithTitle, String> {
        if (!id.matches(UUID_REGEX)) return Pair(ProjectWithTitle(id, id), id)

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

private class ProductCache(private val db: DBContext) {
    private val products = AtomicReference<List<Pair<Product, Long>>>(emptyList())
    private val fillMutex = Mutex()

    suspend fun fillCache() {
        val before = products.get()
        fillMutex.withLock {
            val current = products.get()
            if (before != current) return

            val productCollector = HashMap<ProductReference, Pair<Product, Long>>()

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
                        val id = row.getLong(1)!!
                        val reference = ProductReference(product.name, product.category.name, product.category.provider)
                        val existing = productCollector[reference]

                        when {
                            existing == null -> {
                                productCollector[reference] = Pair(product, id)
                            }

                            product.version > existing.first.version -> {
                                productCollector[reference] = Pair(product, id)
                            }

                            else -> {
                                // Do nothing
                            }
                        }
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

    suspend fun findAllFreeProducts(): List<Product> {
        val products = products.get()
        return products.filter { it.first.freeToUse }.map { it.first }
    }

    suspend fun retrieveProduct(reference: ProductReference, allowCacheRefill: Boolean = true): Pair<Product, Long>? {
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

    suspend fun retrieveProductType(category: ProductCategoryId, allowCacheRefill: Boolean = true): ProductType? {
        val products = products.get()
        val product = products.find {
            it.first.category.name == category.name &&
                    it.first.category.provider == category.provider
        }

        if (product == null && allowCacheRefill) {
            fillCache()
            return retrieveProductType(category, false)
        }

        return product?.first?.productType
    }

    suspend fun retrieveChargeType(category: ProductCategoryId, allowCacheRefill: Boolean = true): ChargeType? {
        val products = products.get()
        val product = products.find {
            it.first.category.name == category.name &&
                    it.first.category.provider == category.provider
        }

        if (product == null && allowCacheRefill) {
            fillCache()
            return retrieveChargeType(category, false)
        }

        return product?.first?.chargeType
    }

    suspend fun retrieveUnitType(category: ProductCategoryId, allowCacheRefill: Boolean = true): ProductPriceUnit? {
        val products = products.get()
        val product = products.find {
            it.first.category.name == category.name &&
                    it.first.category.provider == category.provider
        }

        if (product == null && allowCacheRefill) {
            fillCache()
            return retrieveUnitType(category, false)
        }

        return product?.first?.unitOfPrice
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
