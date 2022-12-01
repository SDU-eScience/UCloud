package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.Time
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.random.Random

@Serializable
@UCloudApiDoc(
    """
    Wallets hold allocations which grant access to a provider's resources.
 
    You can find more information about WalletAllocations
    [here](/docs/developer-guide/accounting-and-projects/accounting/wallets.md).
""", importance = 1000
)
data class Wallet(
    val owner: WalletOwner,
    val paysFor: ProductCategoryId,
    val allocations: List<WalletAllocation>,
    val chargePolicy: AllocationSelectorPolicy,
    val productType: ProductType? = null,
    val chargeType: ChargeType? = null,
    val unit: ProductPriceUnit? = null,
) : DocVisualizable {
    @OptIn(ExperimentalStdlibApi::class)
    override fun visualize(): DocVisualization {
        return DocVisualization.Card(
            "${paysFor.name}@${paysFor.provider} (Wallet)",
            buildList {
                add(DocStatLine.of("owner" to visualizeValue(owner)))
                if (chargeType != null && unit != null) {
                    addAll(Product.visualizePaymentModel(null, chargeType, unit))
                }
            },
            allocations.map { alloc ->
                if (chargeType != null && unit != null && productType != null) {
                    DocVisualization.Card(
                        "${alloc.allocationPath.joinToString("/")} (WalletAllocation)",
                        buildList {
                            val initial = explainBalance(alloc.initialBalance, productType, unit)
                            val current = explainBalance(alloc.balance, productType, unit)
                            val local = explainBalance(alloc.localBalance, productType, unit)
                            add(DocStatLine.of("Initial balance" to DocVisualization.Inline(initial)))
                            add(DocStatLine.of("Current balance" to DocVisualization.Inline(current)))
                            add(DocStatLine.of("Local balance" to DocVisualization.Inline(local)))

                            if (alloc.endDate == null) {
                                add(DocStatLine.of("" to DocVisualization.Inline("No expiration")))
                            } else {
                                add(DocStatLine.of("" to DocVisualization.Inline("Active")))
                            }
                        },
                        emptyList()
                    )
                } else {
                    visualizeValue(alloc)
                }
            }
        )
    }

    companion object {
        fun explainBalance(
            balance: Long,
            productType: ProductType,
            unit: ProductPriceUnit
        ): String {
            val (suffix, normalizationFactor) = when (productType) {
                ProductType.STORAGE -> {
                    when (unit) {
                        ProductPriceUnit.PER_UNIT -> Pair("GB", 1.0)
                        ProductPriceUnit.CREDITS_PER_UNIT -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_MINUTE -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_HOUR -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_DAY -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.UNITS_PER_MINUTE -> Pair("GB minutes", 1.0)
                        ProductPriceUnit.UNITS_PER_HOUR -> Pair("GB hours", 1.0)
                        ProductPriceUnit.UNITS_PER_DAY -> Pair("GB days", 1.0)
                    }
                }
                ProductType.COMPUTE -> {
                    when (unit) {
                        ProductPriceUnit.PER_UNIT -> Pair("Core minutes", 1.0)
                        ProductPriceUnit.CREDITS_PER_UNIT -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_MINUTE -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_HOUR -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_DAY -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.UNITS_PER_MINUTE -> Pair("Core minutes", 1.0)
                        ProductPriceUnit.UNITS_PER_HOUR -> Pair("Core hours", 1.0)
                        ProductPriceUnit.UNITS_PER_DAY -> Pair("Core days", 1.0)
                    }
                }
                ProductType.INGRESS -> {
                    when (unit) {
                        ProductPriceUnit.PER_UNIT -> Pair("Ingresses", 1.0)
                        ProductPriceUnit.CREDITS_PER_UNIT -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_MINUTE -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_HOUR -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_DAY -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.UNITS_PER_MINUTE -> Pair("Ingress minutes", 1.0)
                        ProductPriceUnit.UNITS_PER_HOUR -> Pair("Ingress hours", 1.0)
                        ProductPriceUnit.UNITS_PER_DAY -> Pair("Ingress days", 1.0)
                    }
                }
                ProductType.LICENSE -> {
                    when (unit) {
                        ProductPriceUnit.PER_UNIT -> Pair("Licenses", 1.0)
                        ProductPriceUnit.CREDITS_PER_UNIT -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_MINUTE -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_HOUR -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_DAY -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.UNITS_PER_MINUTE -> Pair("License minutes", 1.0)
                        ProductPriceUnit.UNITS_PER_HOUR -> Pair("License hours", 1.0)
                        ProductPriceUnit.UNITS_PER_DAY -> Pair("License days", 1.0)
                    }
                }
                ProductType.NETWORK_IP -> {
                    when (unit) {
                        ProductPriceUnit.PER_UNIT -> Pair("IP addresses", 1.0)
                        ProductPriceUnit.CREDITS_PER_UNIT -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_MINUTE -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_HOUR -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.CREDITS_PER_DAY -> Pair("DKK", 1 / 1_000_000.0)
                        ProductPriceUnit.UNITS_PER_MINUTE -> Pair("IP address minutes", 1.0)
                        ProductPriceUnit.UNITS_PER_HOUR -> Pair("IP address hours", 1.0)
                        ProductPriceUnit.UNITS_PER_DAY -> Pair("IP address days", 1.0)
                    }
                }
            }

            return "${balance * normalizationFactor} $suffix"
        }
    }
}

/*
 * EXPIRE_FIRST takes the wallet allocation with end date closes to now.
 * ORDERED takes the wallet allocation in a user specified order.
 */
@Serializable
@UCloudApiDoc("A policy for how to select a WalletAllocation in a single Wallet")
enum class AllocationSelectorPolicy {
    @UCloudApiDoc("Use the WalletAllocation which is closest to expiration")
    EXPIRE_FIRST,
    // ORDERED (Planned not yet implemented)
}

@Serializable
@UCloudApiDoc(
    """
    An allocation grants access to resources
    
    You can find more information about WalletAllocations
    [here](/docs/developer-guide/accounting-and-projects/accounting/wallets.md).
""", importance = 990
)
data class WalletAllocation(
    @UCloudApiDoc("A unique ID of this allocation")
    val id: String,
    @UCloudApiDoc(
        """
        A path, starting from the top, through the allocations that will be charged, when a charge is made

        Note that this allocation path will always include, as its last element, this allocation.
    """
    )
    val allocationPath: List<String>,
    @UCloudApiDoc("The current balance of this wallet allocation's subtree")
    val balance: Long,
    @UCloudApiDoc("The initial balance which was granted to this allocation")
    val initialBalance: Long,
    @UCloudApiDoc("The current balance of this wallet allocation")
    val localBalance: Long,
    @UCloudApiDoc("Timestamp for when this allocation becomes valid")
    val startDate: Long,
    @UCloudApiDoc(
        "Timestamp for when this allocation becomes invalid, null indicates that this allocation does not " +
            "expire automatically"
    )
    val endDate: Long?,
    @UCloudApiDoc(
        "ID reference to which grant application this allocation was granted in"
    )
    val grantedIn: Long?,
    val maxUsableBalance: Long? = null,
    @UCloudApiDoc("A property which indicates if this allocation can be used to create sub-allocations")
    val canAllocate: Boolean = false,
    @UCloudApiDoc("A property which indicates that new sub-allocations of this allocation by default should have canAllocate = true")
    val allowSubAllocationsToAllocate: Boolean = true,
)

@Serializable
data class PushWalletChangeRequestItem(
    val allocationId: String,
    val amount: Long,
)

@Serializable
data class RegisterWalletRequestItem(
    val owner: WalletOwner,
    val uniqueAllocationId: String,
    val categoryId: String,
    val balance: Long,
    val providerGeneratedId: String? = null,
)

@Serializable
data class WalletBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
    override val filterEmptyAllocations: Boolean? = null,
    override val includeMaxUsableBalance: Boolean? = null,
    val filterType: ProductType? = null
) : WithPaginationRequestV2, BrowseAllocationsFlags

interface BrowseAllocationsFlags{
    val includeMaxUsableBalance: Boolean?
    val filterEmptyAllocations: Boolean?
}

@Serializable
data class WalletsInternalRetrieveRequest(
    val owner: WalletOwner
)
@Serializable
data class WalletsInternalRetrieveResponse(
    val wallets: List<Wallet>
)
@Serializable
data class WalletAllocationsInternalRetrieveRequest (
    val owner: WalletOwner,
    val categoryId: ProductCategoryId
)
@Serializable
data class WalletAllocationsInternalRetrieveResponse(
    val allocations: List<WalletAllocation>
)

typealias PushWalletChangeResponse = Unit

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("A parent allocator's view of a `WalletAllocation`")
data class SubAllocation(
    val id: String,
    val path: String,
    val startDate: Long,
    val endDate: Long?,

    val productCategoryId: ProductCategoryId,
    val productType: ProductType,
    val chargeType: ChargeType,
    val unit: ProductPriceUnit,

    val workspaceId: String,
    val workspaceTitle: String,
    val workspaceIsProject: Boolean,
    val projectPI: String?,

    val remaining: Long,
    val initialBalance: Long
)

interface SubAllocationQuery : WithPaginationRequestV2 {
    val filterType: ProductType?
}

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class WalletsSearchSubAllocationsRequest(
    val query: String,
    override val filterType: ProductType? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : SubAllocationQuery

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class WalletsBrowseSubAllocationsRequest(
    override val filterType: ProductType? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : SubAllocationQuery

typealias WalletsBrowseSubAllocationsResponse = PageV2<SubAllocation>

@Serializable
data class WalletsRetrieveRecipientRequest(
    val query: String,
)

@Serializable
data class WalletsRetrieveRecipientResponse(
    val id: String,
    val isProject: Boolean,
    val title: String,
    val principalInvestigator: String,
    val numberOfMembers: Int,
)

typealias TestResetCaches = Unit

@Serializable
data class WalletsRetrieveProviderSummaryRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,

    val filterOwnerId: String? = null,
    val filterOwnerIsProject: Boolean? = null,

    val filterCategory: String? = null,
) : WithPaginationRequestV2

@Serializable
data class ProviderWalletSummary(
    val id: String,
    val owner: WalletOwner,
    val categoryId: ProductCategoryId,
    val productType: ProductType,
    val chargeType: ChargeType,
    val unitOfPrice: ProductPriceUnit,

    @UCloudApiDoc("""
        Maximum balance usable until a charge would fail
        
        This balance is calculated when the data is requested and thus can immediately become invalid due to changes
        in the tree.
    """)
    val maxUsableBalance: Long,

    @UCloudApiDoc("""
        Maximum balance usable as promised by a top-level grant giver 
        
        This balance is calculated when the data is requested and thus can immediately become invalid due to changes
        in the tree.
    """)
    val maxPromisedBalance: Long,

    @UCloudApiDoc("The earliest timestamp which allows for the balance to be consumed")
    val notBefore: Long,

    @UCloudApiDoc("The earliest timestamp at which the reported balance is no longer fully usable")
    val notAfter: Long?,
)

object Wallets : CallDescriptionContainer("accounting.wallets") {
    const val baseContext = "/api/accounting/wallets"

    init {
        val Resource = "$TYPE_REF dk.sdu.cloud.provider.api.Resource"
        val Provider = "$TYPE_REF dk.sdu.cloud.provider.api.Provider"

        description = """
            Wallets hold allocations which grant access to a provider's resources.

            $TYPE_REF Wallet s are the core abstraction used in the accounting system of UCloud. This feature builds
            on top of various other features of UCloud. Here is a quick recap:

            - The users of UCloud are members of 
              [Workspaces and Projects](/docs/developer-guide/accounting-and-projects/projects/projects.md). These form 
              the foundation of all collaboration in UCloud.
            - UCloud is an orchestrator of $Resource s. UCloud delegates the responsibility of hosting $Resource s to 
              $Provider s.
            - $Provider s define which services they support using $TYPE_REF Product s.
            - All $TYPE_REF Product s belong in a $TYPE_REF ProductCategory . The category contains similar 
              $TYPE_REF Product s. Under normal circumstances, all products in a category run on the same system.
            - $TYPE_REF Product s define a payment model. The model supports quotas (`DIFFERENTIAL_QUOTA`), one-time 
              payments and periodic payments (`ABSOLUTE`). All absolute payment models support paying in a 
              product-specific unit or in DKK.
            - All $TYPE_REF Product s in a category share the exact same payment model

            Allocators grant access to $Resource s via $TYPE_REF WalletAllocation s. In a simplified view, an 
            allocation is:

            - An initial balance, specified in the "unit of allocation" which the $TYPE_REF Product specifies. 
              For example: 1000 DKK or 500 Core Hours.
            - Start date and optional end date.
            - An optional parent allocation.
            - A current balance, the balance remaining for this allocation and all descendants.
            - A local balance, the balance remaining if it had no descendants

            UCloud combines allocations of the same category into a $TYPE_REF Wallet. Every $TYPE_REF Wallet has 
            exactly one owner, [a workspace](/docs/developer-guide/accounting-and-projects/projects/projects.md). 
            $TYPE_REF Wallet s create a natural hierarchical structure. Below we show an example of this:

            ![](/backend/accounting-service/wiki/allocations.png)
            
            __Figure:__ Allocations create a natural _allocation hierarchy_.
        """.trimIndent()
    }

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    val push = call("push", BulkRequest.serializer(PushWalletChangeRequestItem.serializer()), PushWalletChangeResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "push", roles = Roles.PROVIDER)

        documentation {
            summary = "Pushes a Wallet to the catalog"
        }
    }

    val testResetCaches = call("resetCache", TestResetCaches.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve("resetCache")

        auth {
            roles = Roles.SERVICE
            access = AccessRight.READ_WRITE
        }
    }

    val register = call("register", BulkRequest.serializer(RegisterWalletRequestItem.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "register", roles = Roles.PROVIDER)

        documentation {
            summary = "Registers an allocation created outside of UCloud"
        }
    }

    val browse = call("browse", WalletBrowseRequest.serializer(), PageV2.serializer(Wallet.serializer()), CommonErrorMessage.serializer()) {
        httpBrowse(baseContext)

        documentation {
            summary = "Browses the catalog of accessible Wallets"
        }
    }

    val retrieveWalletsInternal = call(
        "retrieveWalletsInternal",
        WalletsInternalRetrieveRequest.serializer(),
        WalletsInternalRetrieveResponse.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpUpdate(baseContext, "retrieveWalletsInternal")

        auth {
            access = AccessRight.READ
            roles = Roles.PRIVILEGED
        }

        documentation {
            summary = "Retrieves a list of up-to-date wallets from the in-memory DB"
            description = """
                This endpoint will return a list of $TYPE_REF Wallet s which are related to the active workspace.
                This is mainly for backend use. For frontend, use the browse call instead for a paginated response
            """.trimIndent()
        }
    }

    val retrieveAllocationsInternal = call(
        "retrieveAllocationsInternal",
        WalletAllocationsInternalRetrieveRequest.serializer(),
        WalletAllocationsInternalRetrieveResponse.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpUpdate(baseContext, "retrieveAllocationsInternal")

        auth {
            access = AccessRight.READ
            roles = Roles.PRIVILEGED
        }

        documentation {
            summary = "Retrieves a list of product specific up-to-date allocation from the in-memory DB"
            description = """
                This endpoint will return a list of $TYPE_REF WalletAllocation s which are related to the given product
                available to the user.
                This is mainly for backend use. For frontend, use the browse call instead for a paginated response
            """.trimIndent()
        }
    }

    val searchSubAllocations = call("searchSubAllocations", WalletsSearchSubAllocationsRequest.serializer(), PageV2.serializer(SubAllocation.serializer()), CommonErrorMessage.serializer()) {
        httpSearch(baseContext, "subAllocation")
        documentation {
            summary = "Searches the catalog of sub-allocations"
            description = """
                This endpoint will find all $TYPE_REF WalletAllocation s which are direct children of one of your
                accessible $TYPE_REF WalletAllocation s.
            """.trimIndent()
        }
    }

    val browseSubAllocations = call("browseSubAllocations", WalletsBrowseSubAllocationsRequest.serializer(), PageV2.serializer(SubAllocation.serializer()), CommonErrorMessage.serializer()) {
        httpBrowse(baseContext, "subAllocation")

        documentation {
            summary = "Browses the catalog of sub-allocations"
            description = """
                This endpoint will find all $TYPE_REF WalletAllocation s which are direct children of one of your
                accessible $TYPE_REF WalletAllocation s.
            """.trimIndent()
        }
    }

    @UCloudApiExperimental(ExperimentalLevel.BETA)
    val retrieveRecipient = call("retrieveRecipient", WalletsRetrieveRecipientRequest.serializer(), WalletsRetrieveRecipientResponse.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, "recipient")

        documentation {
            summary = "Retrieves information about a potential WalletAllocation recipient"
            description = """
                You can use this endpoint to find information about a Workspace. This is useful when creating a 
                sub-allocation.
            """.trimIndent()
        }
    }

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    val retrieveProviderSummary = call("retrieveProviderSummary", WalletsRetrieveProviderSummaryRequest.serializer(), PageV2.serializer(ProviderWalletSummary.serializer()), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, "providerSummary", roles = Roles.PROVIDER)

        documentation {
            summary = "Retrieves a provider summary of relevant wallets"
            description = """
                This endpoint is only usable by providers. The endpoint will return a stable sorted summary of all
                allocations that a provider currently has.
            """.trimIndent()
        }
    }
}

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiOwnedBy(Wallets::class)
sealed class WalletOwner : DocVisualizable {
    @Serializable
    @SerialName("user")
    data class User(val username: String) : WalletOwner() {
        override fun visualize(): DocVisualization = DocVisualization.Inline("$username (User)")
    }

    @Serializable
    @SerialName("project")
    data class Project(val projectId: String) : WalletOwner() {
        override fun visualize(): DocVisualization = DocVisualization.Inline("$projectId (Project)")
    }
}

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class ChargeWalletRequestItem(
    @UCloudApiDoc("The payer of this charge")
    val payer: WalletOwner,
    @UCloudApiDoc(
        """
        The number of units that this charge is about
        
        The unit itself is defined by the product. The unit can, for example, describe that the 'units' describe the
        number of minutes/hours/days.
    """
    )
    val units: Long,
    @UCloudApiDoc("The number of products involved in this charge, for example the number of nodes")
    val periods: Long,
    @UCloudApiDoc("A reference to the product which the service is charging for")
    val product: ProductReference,
    @UCloudApiDoc("The username of the user who generated this request")
    val performedBy: String,
    @UCloudApiDoc("A description of the charge this is used purely for presentation purposes")
    val description: String,
    @UCloudApiDoc("An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions")
    var transactionId: String = Random.nextLong().toString() + Time.now()
) {
    init {
        checkMinimumValue(this::periods, periods, 1)
        checkMinimumValue(this::units, units, 0)
    }
}

typealias ChargeWalletResponse = BulkResponse<Boolean>

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class DepositToWalletRequestItem(
    @UCloudApiDoc("The recipient of this deposit")
    val recipient: WalletOwner,
    @UCloudApiDoc("A reference to the source allocation which the deposit will draw from")
    val sourceAllocation: String,
    @UCloudApiDoc("The amount of credits to deposit into the recipient's wallet")
    val amount: Long,
    @UCloudApiDoc("A description of this change. This is used purely for presentation purposes.")
    val description: String,
    @UCloudApiDoc(
        """
        A timestamp for when this deposit should become valid
        
        This value must overlap with the source allocation. A value of null indicates that the allocation becomes valid
        immediately.
    """
    )
    var startDate: Long? = null,
    @UCloudApiDoc(
        """
        A timestamp for when this deposit should become invalid
        
        This value must overlap with the source allocation. A value of null indicates that the allocation will never
        expire.
    """
    )
    val endDate: Long? = null,
    @UCloudApiDoc("An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions")
    var transactionId: String = Random.nextLong().toString() + Time.now(),
    val dry: Boolean = false
)

typealias DepositToWalletResponse = Unit

typealias ForceInMemoryDBSyncRequest = Unit
typealias ForceInMemoryDBSyncResponse = Unit

@Serializable
data class UpdateAllocationRequestItem(
    val id: String,
    val balance: Long,
    var startDate: Long,
    val endDate: Long? = null,
    val reason: String,
    @UCloudApiDoc("An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions")
    var transactionId: String = Random.nextLong().toString() + Time.now()
)

typealias UpdateAllocationResponse = Unit

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("See `DepositToWalletRequestItem`")
@Serializable
data class RootDepositRequestItem(
    val categoryId: ProductCategoryId,
    val recipient: WalletOwner,
    val amount: Long,
    val description: String,
    var startDate: Long? = null,
    val endDate: Long? = null,
    var transactionId: String = Random.nextLong().toString() + Time.now(),
    val providerGeneratedId: String? = null,
    val forcedSync: Boolean = false
)

object Accounting : CallDescriptionContainer("accounting") {
    const val baseContext = "/api/accounting"

    init {
        serializerLookupTable = mapOf(
            serializerEntry(WalletOwner.User.serializer()),
            serializerEntry(WalletOwner.Project.serializer())
        )

        description = """
            The accounting system of UCloud has three core operations.

            The three core operations of the UCloud accounting system are:

            - $CALL_REF accounting.charge: Records usage in the system. For absolute payment models, this will deduct 
              the balance and local balance of an allocation. All ancestor allocations have their balance deducted by 
              the same amount. The local balances of an ancestor remains unchanged. 
            - $CALL_REF accounting.deposit: Creates a new _sub-allocation_ from a parent allocation. The new allocation
              will have the current allocation as a parent. The balance of the parent allocation is not changed.

            ---

            __📝 NOTE:__ We recommend that you first read and understand the 
            [Wallet system](/docs/developer-guide/accounting-and-projects/accounting/wallets.md) of UCloud.

            ---
            
            __📝 Provider Note:__ This API is invoked by internal UCloud/Core services. As a 
            $TYPE_REF dk.sdu.cloud.provider.api.Provider, you will be indirectly calling this API through the outgoing
            `Control` APIs.
            
            ---
            
            We recommend that you study the examples below and look at the corresponding call documentation to 
            understand the accounting system of UCloud.
            
            ## A note on the examples

            In the examples below, we will be using a consistent set of $TYPE_REF Product s:

            - `example-slim-1` / `example-slim` @ `example`
               - Type: Compute
               - `ChargeType.ABSOLUTE`
               - `ProductPriceUnit.UNITS_PER_HOUR`
               - Price per unit: 1
            - `example-storage` / `example-storage` @ `example`
               - Type: Storage
               - `ChargeType.DIFFERENTIAL_QUOTA`
               - `ProductPriceUnit.PER_UNIT`
               - Price per unit: 1
        """.trimIndent()
    }

    private const val chargeAbsoluteSingleUseCase = "charge-absolute-single"
    private const val chargeDifferentialSingleUseCase = "charge-differential-single"
    private const val chargeAbsoluteMultiUseCase = "charge-absolute-multi"
    private const val chargeDifferentialMultiUseCase = "charge-differential-multi"
    private const val chargeAbsoluteMultiMissingUseCase = "charge-absolute-multi-missing"
    private const val chargeDifferentialMultiMissingUseCase = "charge-differential-multi-missing"
    private const val depositUseCase = "deposit"

    override fun documentation() {
        val defaultOwner: WalletOwner = WalletOwner.Project("my-research")
        val absoluteProductReference = ProductReference("example-slim-1", "example-slim", "example")
        val differentialProductReference = ProductReference("example-storage", "example-storage", "example")

        fun allocation(
            path: List<String>,
            balance: Long,
            localBalance: Long = balance,
            initialBalance: Long = balance
        ): WalletAllocation {
            return WalletAllocation(
                path.last(), path,
                balance, initialBalance, localBalance,
                1633941615074L,
                null,
                1,
                maxUsableBalance = null
            )
        }

        fun walletPage(
            type: ChargeType,
            vararg allocations: WalletAllocation,
            owner: WalletOwner = defaultOwner,
        ): PageV2<Wallet> {
            val productCategoryId = when (type) {
                ChargeType.ABSOLUTE -> ProductCategoryId("example-slim", "example")
                ChargeType.DIFFERENTIAL_QUOTA -> ProductCategoryId("example-storage", "example")
            }

            val productType = when (type) {
                ChargeType.ABSOLUTE -> ProductType.COMPUTE
                ChargeType.DIFFERENTIAL_QUOTA -> ProductType.STORAGE
            }

            val productPriceUnit = when (type) {
                ChargeType.ABSOLUTE -> ProductPriceUnit.UNITS_PER_HOUR
                ChargeType.DIFFERENTIAL_QUOTA -> ProductPriceUnit.PER_UNIT
            }

            return PageV2(
                50,
                listOf(
                    Wallet(
                        owner,
                        productCategoryId,
                        listOf(*allocations),
                        AllocationSelectorPolicy.EXPIRE_FIRST,
                        productType,
                        type,
                        productPriceUnit
                    )
                ),
                null
            )
        }

        useCase(
            chargeAbsoluteSingleUseCase,
            "Charging a root allocation (Absolute)",
            flow = {
                val ucloud = ucloudCore()

                comment(
                    """
                    In this example, we will be performing some simple charge requests for an absolute 
                    product. Before and after each charge, we will show the current state of the system.
                    We will perform the charges on a root allocation, that is, it has no ancestors.
                """.trimIndent()
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42"), 1000, 1000, 1000)
                    ),
                    ucloud
                )

                comment("Currently, the allocation has a balance of 1000.")

                success(
                    charge,
                    bulkRequestOf(
                        ChargeWalletRequestItem(
                            defaultOwner,
                            1, 1,
                            absoluteProductReference,
                            "user",
                            "A charge for compute usage",
                            "charge-1"
                        ),
                    ),
                    ChargeWalletResponse(listOf(true)),
                    ucloud
                )

                comment("The charge returns true, indicating that we had enough credits to complete the request.")

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42"), 999, 999, 1000)
                    ),
                    ucloud
                )

                comment("As expected, a single credit was removed from our current balance and local balance.")

                success(
                    charge,
                    bulkRequestOf(
                        ChargeWalletRequestItem(
                            defaultOwner,
                            1, 1,
                            absoluteProductReference,
                            "user",
                            "A charge for compute usage",
                            "charge-1"
                        ),
                    ),
                    ChargeWalletResponse(listOf(true)),
                    ucloud
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42"), 998, 998, 1000)
                    ),
                    ucloud
                )

                comment("A second charge further deducts 1 from the balance, as expected.")
            }
        )

        useCase(
            chargeDifferentialSingleUseCase,
            "Charging a root allocation (Differential)",
            flow = {
                val ucloud = ucloudCore()

                comment(
                    """
                    In this example, we will be performing some simple charge requests for a differential 
                    product. Before and after each charge, we will show the current state of the system.
                    We will perform the charges on a root allocation, that is, it has no ancestors.
                """.trimIndent()
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42"), 1000, 1000, 1000)
                    ),
                    ucloud
                )

                comment("Currently, the allocation shows that we have 1000 GB unused.")

                success(
                    charge,
                    bulkRequestOf(
                        ChargeWalletRequestItem(
                            defaultOwner,
                            100, 1,
                            differentialProductReference,
                            "user",
                            "A charge for storage usage",
                            "charge-1"
                        ),
                    ),
                    ChargeWalletResponse(listOf(true)),
                    ucloud
                )

                comment("The charge returns true, indicating that we had enough credits to complete the request.")

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42"), 900, 900, 1000)
                    ),
                    ucloud
                )

                comment("The charge has correctly record our usage. It now shows that we have 900 GB unused.")

                success(
                    charge,
                    bulkRequestOf(
                        ChargeWalletRequestItem(
                            defaultOwner,
                            50, 1,
                            differentialProductReference,
                            "user",
                            "A charge for storage usage",
                            "charge-1"
                        ),
                    ),
                    ChargeWalletResponse(listOf(true)),
                    ucloud
                )

                comment(
                    "The new charge reports that we are only using 50 GB, that is data was removed since last " +
                        "period."
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42"), 950, 950, 1000)
                    ),
                    ucloud
                )

                comment("This results in 950 GB being unused.")
            }
        )

        useCase(
            chargeAbsoluteMultiUseCase,
            "Charging a leaf allocation (Absolute)",
            flow = {
                val ucloud = ucloudCore()
                val piRoot = actor("piRoot", "The PI of the root project")
                val piLeaf = actor("piLeaf", "The PI of the leaf project")

                val rootOwner = WalletOwner.Project("root-project")
                val leafOwner = WalletOwner.Project("leaf-project")

                comment(
                    """
                    In this example, we will show how a charge affects the rest of the allocation hierarchy. The 
                    hierarchy we use consists of a single root allocation. The root allocation has a single child, 
                    which we will be referring to as the leaf, since it has no children.
                """.trimIndent()
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42"), 1000, 1000, 1000),
                        owner = rootOwner,
                    ),
                    piRoot
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42", "52"), 500, 500, 500),
                        owner = leafOwner,
                    ),
                    piLeaf
                )

                comment(
                    "As we can see, in our initial state, the root has 1000 core hours remaining and the leaf has " +
                        "500."
                )

                comment("We now perform our charge of a single core hour.")

                success(
                    charge,
                    bulkRequestOf(
                        ChargeWalletRequestItem(
                            leafOwner,
                            1, 1,
                            absoluteProductReference,
                            "user",
                            "A charge for compute usage",
                            "charge-1"
                        ),
                    ),
                    ChargeWalletResponse(listOf(true)),
                    ucloud
                )

                comment(
                    """
                    The response, as expected, that we had enough credits for the transaction. This would have been 
                    false if _any_ of the allocation in the hierarchy runs out of credits.
                """.trimIndent()
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42"), 999, 1000, 1000),
                        owner = rootOwner,
                    ),
                    piRoot
                )

                comment(
                    """
                    On the root allocation, we see that this has subtracted a single core hour from the balance. Recall 
                    that balance shows the overall balance for the entire subtree. The local balance of the root 
                    remains unaffected, since this wasn't consumed by the root. 
                """.trimIndent()
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42", "52"), 499, 499, 500),
                        owner = leafOwner,
                    ),
                    piLeaf
                )

                comment("In the leaf allocation, we see that this has affected both the balance and the local balance.")
            }
        )

        useCase(
            chargeDifferentialMultiUseCase,
            "Charging a leaf allocation (Differential)",
            flow = {
                val ucloud = ucloudCore()
                val piRoot = actor("piRoot", "The PI of the root project")
                val piLeaf = actor("piLeaf", "The PI of the leaf project")

                val rootOwner = WalletOwner.Project("root-project")
                val leafOwner = WalletOwner.Project("leaf-project")

                comment(
                    """
                    In this example, we will show how a charge affects the rest of the allocation hierarchy. The 
                    hierarchy we use consists of a single root allocation. The root allocation has a single child, 
                    which we will be referring to as the leaf, since it has no children.
                """.trimIndent()
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42"), 1000, 1000, 1000),
                        owner = rootOwner,
                    ),
                    piRoot
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42", "52"), 500, 500, 500),
                        owner = leafOwner,
                    ),
                    piLeaf
                )

                comment("As we can see, in our initial state, the root has 1000 GB remaining and the leaf has 500.")
                comment("We now perform our charge of 100 GB on the leaf.")

                success(
                    charge,
                    bulkRequestOf(
                        ChargeWalletRequestItem(
                            leafOwner,
                            100, 1,
                            differentialProductReference,
                            "user",
                            "A charge for compute usage",
                            "charge-1"
                        ),
                    ),
                    ChargeWalletResponse(listOf(true)),
                    ucloud
                )

                comment(
                    """
                    The response, as expected, that we had enough credits for the transaction. This would have been 
                    false if _any_ of the allocation in the hierarchy runs out of credits.
                """.trimIndent()
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42"), 900, 1000, 1000),
                        owner = rootOwner,
                    ),
                    piRoot
                )

                comment(
                    """
                    On the root allocation, we see that this has subtracted 100 GB from the balance. Recall that 
                    balance shows the overall balance for the entire subtree. The local balance of the root remains 
                    unaffected, since this wasn't consumed by the root.
                """.trimIndent()
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42", "52"), 400, 400, 500),
                        owner = leafOwner,
                    ),
                    piLeaf
                )

                comment(
                    """
                    In the leaf allocation, we see that this has affected both the balance and the local balance. 
                """.trimIndent()
                )

                comment("We now attempt to perform a similar charge, of 50 GB, but this time on the root allocation.")

                success(
                    charge,
                    bulkRequestOf(
                        ChargeWalletRequestItem(
                            rootOwner,
                            50, 1,
                            differentialProductReference,
                            "user",
                            "A charge for compute usage",
                            "charge-1"
                        ),
                    ),
                    ChargeWalletResponse(listOf(true)),
                    ucloud
                )

                comment("Again, this allocation succeeds.")

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42"), 850, 950, 1000),
                        owner = rootOwner,
                    ),
                    piRoot
                )

                comment("This charge has affected the local and current balance of the root by the expected 50 GB.")

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42", "52"), 400, 400, 500),
                        owner = leafOwner,
                    ),
                    piLeaf
                )

                comment(
                    """
                    The leaf allocation remains unchanged. Any and all charges will only affect the charged allocation 
                    and their ancestors. A descendant is never directly updated by such an operation.
                """.trimIndent()
                )
            }
        )

        useCase(
            chargeAbsoluteMultiMissingUseCase,
            "Charging a leaf allocation with missing credits (Absolute)",
            flow = {
                val ucloud = ucloudCore()
                val piRoot = actor("piRoot", "The PI of the root project")
                val piNode = actor("piNode", "The PI of the node project (child of root)")
                val piLeaf = actor("piLeaf", "The PI of the leaf project (child of node)")

                val rootOwner = WalletOwner.Project("root-project")
                val nodeOwner = WalletOwner.Project("node-project")
                val leafOwner = WalletOwner.Project("leaf-project")

                comment(
                    """
                    In this example, we will show what happens when an allocation is unable to carry the full charge. 
                    We will be using a more complex hierarchy. The hierarchy will have a single root. The root has a 
                    single child, the 'node' allocation. This node has a single child allocation, the leaf. The leaf 
                    has no children.
                """.trimIndent()
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42"), 550, 1000, 1000),
                        owner = rootOwner,
                    ),
                    piRoot
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42", "52"), 50, 100, 500),
                        owner = nodeOwner,
                    ),
                    piNode
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42", "52", "62"), 450, 450, 500),
                        owner = leafOwner,
                    ),
                    piLeaf
                )

                comment(
                    """
                    As we can see from the allocations, they have already been in use. To be concrete, you can reach 
                    this state by applying a 400 core hour charge on the node and another 50 core hours on the leaf.
                """.trimIndent()
                )

                comment(
                    """
                    We now attempt to perform a charge of 100 core hours on the leaf.
                """.trimIndent()
                )

                success(
                    charge,
                    bulkRequestOf(
                        ChargeWalletRequestItem(
                            leafOwner,
                            100, 1,
                            absoluteProductReference,
                            "user",
                            "A charge for compute usage",
                            "charge-1"
                        ),
                    ),
                    ChargeWalletResponse(listOf(false)),
                    ucloud
                )

                comment(
                    """
                    Even though the leaf, seen in isolation, has enough credits. The failure occurs in the node which, 
                    before the charge, only has 50 core hours remaining.
                """.trimIndent()
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42"), 450, 1000, 1000),
                        owner = rootOwner,
                    ),
                    piRoot
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42", "52"), -50, 100, 500),
                        owner = nodeOwner,
                    ),
                    piNode
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42", "52", "62"), 350, 350, 500),
                        owner = leafOwner,
                    ),
                    piLeaf
                )

                comment(
                    """
                    When we apply the charge, the node reaches a negative balance. If any allocation reaches a negative 
                    balance, then the charge has failed. As we can see, it is possible for a balance to go into the 
                    negatives.
                """.trimIndent()
                )
            }
        )

        useCase(
            chargeDifferentialMultiMissingUseCase,
            "Charging a leaf allocation with missing credits (Differential)",
            flow = {
                val ucloud = ucloudCore()
                val piRoot = actor("piRoot", "The PI of the root project")
                val piNode = actor("piNode", "The PI of the node project (child of root)")
                val piLeaf = actor("piLeaf", "The PI of the leaf project (child of node)")

                val rootOwner = WalletOwner.Project("root-project")
                val nodeOwner = WalletOwner.Project("node-project")
                val leafOwner = WalletOwner.Project("leaf-project")

                // 400 on node
                // 50 on leaf

                comment(
                    """
                    In this example, we will show what happens when an allocation is unable to carry the full charge. 
                    We will be using a more complex hierarchy. The hierarchy will have a single root. The root has a 
                    single child, the 'node' allocation. This node has a single child allocation, the leaf. The leaf 
                    has no children.
                """.trimIndent()
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42"), 550, 1000, 1000),
                        owner = rootOwner,
                    ),
                    piRoot
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42", "52"), 50, 100, 500),
                        owner = nodeOwner,
                    ),
                    piNode
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42", "52", "62"), 450, 450, 500),
                        owner = leafOwner,
                    ),
                    piLeaf
                )

                comment(
                    """
                    As we can see from the allocations, they have already been in use. To be concrete, you can reach 
                    this state by applying a 400 GB charge on the node and another 50 GB on the leaf.
                """.trimIndent()
                )

                comment(
                    """
                    We now attempt to perform a charge of 110 GB on the leaf.
                """.trimIndent()
                )

                success(
                    charge,
                    bulkRequestOf(
                        ChargeWalletRequestItem(
                            leafOwner,
                            110, 1,
                            differentialProductReference,
                            "user",
                            "A charge for compute usage",
                            "charge-1"
                        ),
                    ),
                    ChargeWalletResponse(listOf(false)),
                    ucloud
                )

                comment(
                    """
                    Even though the leaf, seen in isolation, has enough credits. The failure occurs in the node which, 
                    before the charge, only has 50 GB remaining.
                """.trimIndent()
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42"), 490, 1000, 1000),
                        owner = rootOwner,
                    ),
                    piRoot
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42", "52"), -10, 100, 500),
                        owner = nodeOwner,
                    ),
                    piNode
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42", "52", "62"), 390, 390, 500),
                        owner = leafOwner,
                    ),
                    piLeaf
                )

                comment(
                    """
                    When we apply the charge, the node reaches a negative balance. If any allocation reaches a negative 
                    balance, then the charge has failed. As we can see, it is possible for a balance to go into the 
                    negatives.
                """.trimIndent()
                )

                comment(
                    """
                    We now assume that the leaf deletes all their data. The accounting system records this as a charge 
                    for 0 units (GB).
                """.trimIndent()
                )

                success(
                    charge,
                    bulkRequestOf(
                        ChargeWalletRequestItem(
                            leafOwner,
                            0, 1,
                            differentialProductReference,
                            "user",
                            "A charge for compute usage",
                            "charge-1"
                        ),
                    ),
                    ChargeWalletResponse(listOf(true)),
                    ucloud
                )

                comment("This charge succeeds, as it is bringing the balance back into the positive.")

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42"), 490, 1000, 1000),
                        owner = rootOwner,
                    ),
                    piRoot
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42", "52"), 100, 100, 500),
                        owner = nodeOwner,
                    ),
                    piLeaf
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.DIFFERENTIAL_QUOTA,
                        allocation(listOf("42", "52", "62"), 500, 500, 500),
                        owner = leafOwner,
                    ),
                    piLeaf
                )
            }
        )

        useCase(
            depositUseCase,
            "Creating a sub-allocation (deposit operation)",
            flow = {
                val piRoot = actor("piRoot", "The PI of the root project")
                val piLeaf = actor("piLeaf", "The PI of the leaf project (child of root)")

                val rootOwner = WalletOwner.Project("root-project")
                val leafOwner = WalletOwner.Project("leaf-project")

                comment(
                    """
                    In this example, we will show how a workspace can create a sub-allocation. The new allocation will 
                    have an existing allocation as a child. This is the recommended way of creating allocations. 
                    Resources are not immediately removed from the parent allocation. In addition, workspaces can 
                    over-allocate resources. For example, a workspace can deposit more resources than they have into 
                    sub-allocations. This doesn't create more resources in the system. As we saw from the charge 
                    examples, all allocations in a hierarchy must be able to carry a charge.
                """.trimIndent()
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42"), 500, 500, 500),
                        owner = rootOwner,
                    ),
                    piRoot
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        owner = leafOwner,
                    ),
                    piLeaf
                )

                comment(
                    """
                    Our initial state shows that the root project has 500 core hours. The leaf doesn't have any 
                    resources at the moment.
                """.trimIndent()
                )

                comment(
                    """
                    We now perform a deposit operation with the leaf workspace as the target.
                """.trimIndent()
                )

                success(
                    deposit,
                    bulkRequestOf(
                        DepositToWalletRequestItem(
                            leafOwner,
                            "42",
                            100,
                            "Create sub-allocation"
                        )
                    ),
                    Unit,
                    piRoot
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42"), 500, 500, 500),
                        owner = rootOwner,
                    ),
                    piRoot
                )

                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    walletPage(
                        ChargeType.ABSOLUTE,
                        allocation(listOf("42", "52"), 100, 100, 100),
                        owner = leafOwner,
                    ),
                    piLeaf
                )

                comment(
                    """
                    After inspecting the allocations, we see that the original (root) allocation remains unchanged. 
                    However, the leaf workspace now have a new allocation. This allocation has the root allocation as a 
                    parent, indicated by the path. 
                """.trimIndent()
                )
            }
        )
    }

    val charge = call("charge", BulkRequest.serializer(ChargeWalletRequestItem.serializer()), BulkResponse.serializer(Boolean.serializer()), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "charge", roles = Roles.PRIVILEGED)

        documentation {
            summary = "Records usage in the system"
            description = """
                Internal UCloud services invoke this endpoint to record usage from a workspace. Providers report data 
                indirectly to this API through the outgoing `Control` API. This endpoint causes changes in the balances 
                of the targeted allocation and ancestors. UCloud will change the `balance` and `localBalance` property 
                of the targeted allocation. Ancestors of the targeted allocation will only update their `balance`.

                UCloud returns a boolean, for every request, indicating if the charge was successful. A charge is 
                successful if no affected allocation went into a negative balance.

                ---
                
                __📝 NOTE:__ Unsuccessful charges are still deducted in their balances.
                
                ---

                The semantics of `charge` depends on the Product's payment model.

                __Absolute:__

                - UCloud calculates the change in balances by multiplying: the Product's pricePerUnit, the number of 
                  units, the number of periods
                - UCloud subtracts this change from the balances

                __Differential:__

                - UCloud calculates the change in balances by comparing the units with the current `localBalance`
                - UCloud subtracts this change from the balances
                - Note: This change can cause the balance to go up, if the usage is lower than last period

                #### Selecting Allocations

                The charge operation targets a wallet (by combining the ProductCategoryId and WalletOwner). This means 
                that the charge operation have multiple allocations to consider. We explain the approach for absolute 
                payment models. The approach is similar for differential products.

                UCloud first finds a set of leaf allocations which, when combined, can carry the full change. UCloud 
                first finds a set of candidates. We do this by sorting allocations by the Wallet's `chargePolicy`. By 
                default, this means that UCloud prioritizes allocations that expire soon. UCloud only considers 
                allocations which are active and have a positive balance.

                ---

                __📝 NOTE:__ UCloud does not consider ancestors at this point in the process.
                
                ---

                UCloud now creates the list of allocations which it will use. We do this by performing a rolling sum of 
                the balances. UCloud adds an allocation to the set if the rolling sum has not yet reached the total 
                amount.

                UCloud will use the full balance of each selected allocation. The only exception is the last element, 
                which might use less. If the change in balance is never reached, then UCloud will further charge the 
                first selected allocation. In this case, the priority allocation will have to pay the difference.

                Finally, the system updates the balances of each selected leaf, and all of their ancestors.
            """.trimIndent()

            useCaseReference(chargeAbsoluteSingleUseCase, "Charging a root allocation (Absolute)")
            useCaseReference(chargeAbsoluteMultiUseCase, "Charging a leaf allocation (Absolute)")
            useCaseReference(
                chargeAbsoluteMultiMissingUseCase,
                "Charging a leaf allocation with missing credits (Absolute)"
            )
            useCaseReference(chargeDifferentialSingleUseCase, "Charging a root allocation (Differential)")
            useCaseReference(chargeDifferentialMultiUseCase, "Charging a leaf allocation (Differential)")
            useCaseReference(
                chargeDifferentialMultiMissingUseCase,
                "Charging a leaf allocation with missing credits (Differential)"
            )
        }
    }

    val deposit = call("deposit", BulkRequest.serializer(DepositToWalletRequestItem.serializer()), DepositToWalletResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "deposit")

        documentation {
            summary = "Creates a new sub-allocation from a parent allocation"
            description = """
                The new allocation will have the current allocation as a parent. The balance of the parent allocation 
                is not changed.
            """.trimIndent()

            useCaseReference(depositUseCase, "Creating a sub-allocation")
        }
    }

    val updateAllocation = call("updateAllocation", BulkRequest.serializer(UpdateAllocationRequestItem.serializer()), UpdateAllocationResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "allocation")

        documentation {
            summary = "Update an existing allocation"

            description = """
                    Updates one or more existing allocations. This endpoint will use all the provided values. That is,
                    you must provide all values, even if they do not change. This will generate a transaction indicating
                    the change. This will set the initial balance of the allocation, as if it was initially created with
                    this value.
                    
                    The constraints that are in place during a standard creation are still in place when updating the
                    values. This means that the new start and end dates _must_ overlap with the values of all ancestors.
                """.trimIndent()
        }
    }

    val check = call("check", BulkRequest.serializer(ChargeWalletRequestItem.serializer()), BulkResponse.serializer(Boolean.serializer()), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "check", roles = Roles.SERVICE)

        documentation {
            summary = "Checks if one or more wallets are able to carry a charge"
            description = """
                    Checks if one or more charges would succeed without lacking credits. This will not generate a
                    transaction message, and as a result, the description will never be used.
                """.trimIndent()
        }
    }

    val rootDeposit = call("rootDeposit", BulkRequest.serializer(RootDepositRequestItem.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "rootDeposit", roles = Roles.PRIVILEGED)
    }
}
