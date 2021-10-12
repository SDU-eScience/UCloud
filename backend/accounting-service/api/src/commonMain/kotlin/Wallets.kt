package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.Roles
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.CALL_REF
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.ExperimentalLevel
import dk.sdu.cloud.calls.TYPE_REF
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiExperimental
import dk.sdu.cloud.calls.UCloudApiOwnedBy
import dk.sdu.cloud.calls.actor
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.checkMinimumValue
import dk.sdu.cloud.calls.comment
import dk.sdu.cloud.calls.description
import dk.sdu.cloud.calls.documentation
import dk.sdu.cloud.calls.httpBrowse
import dk.sdu.cloud.calls.httpRetrieve
import dk.sdu.cloud.calls.httpUpdate
import dk.sdu.cloud.calls.serializerEntry
import dk.sdu.cloud.calls.serializerLookupTable
import dk.sdu.cloud.calls.success
import dk.sdu.cloud.calls.ucloudCore
import dk.sdu.cloud.calls.useCase
import dk.sdu.cloud.service.Time
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
)

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
    val endDate: Long?
)

@Serializable
data class PushWalletChangeRequestItem(
    val owner: WalletOwner,
    val amount: Long,
    val productId: ProductReference,
)

@Serializable
data class WalletBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
    val filterType: ProductType? = null
) : WithPaginationRequestV2

typealias PushWalletChangeResponse = Unit

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("A parent allocator's view of a `WalletAllocation`")
data class SubAllocation(
    val id: String,
    val startDate: Long,
    val endDate: Long?,

    val productCategoryId: ProductCategoryId,
    val productType: ProductType,
    val chargeType: ChargeType,
    val unit: ProductPriceUnit,

    val workspaceId: String,
    val workspaceTitle: String,
    val workspaceIsProject: Boolean,

    val remaining: Long,
)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class WalletsBrowseSubAllocationsRequest(
    val filterType: ProductType? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

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

    val push = call<BulkRequest<PushWalletChangeRequestItem>, PushWalletChangeResponse, CommonErrorMessage>(
        "push"
    ) {
        httpUpdate(baseContext, "push", roles = Roles.SERVICE)

        documentation {
            summary = "Pushes a Wallet to the catalog (Not yet implemented)"
        }
    }

    val browse = call<WalletBrowseRequest, PageV2<Wallet>, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)

        documentation {
            summary = "Browses the catalog of accessible Wallets"
        }
    }

    val browseSubAllocations = call<WalletsBrowseSubAllocationsRequest, WalletsBrowseSubAllocationsResponse,
            CommonErrorMessage>("browseSubAllocations") {
        httpBrowse(baseContext, "subAllocation")

        documentation {
            summary = "Browses the catalog of sub-allocations"
            description = """
                This endpoint will find all $TYPE_REF WalletAllocation s which are direct children of one of your
                accessible $TYPE_REF WalletAllocation s.
            """.trimIndent()
        }
    }

    val retrieveRecipient = call<WalletsRetrieveRecipientRequest, WalletsRetrieveRecipientResponse,
            CommonErrorMessage>("retrieveRecipient") {
        httpRetrieve(baseContext, "recipient")

        documentation {
            summary = "Retrieves information about a potential WalletAllocation recipient"
            description = """
                You can use this endpoint to find information about a Workspace. This is useful when creating a 
                sub-allocation.
            """.trimIndent()
        }
    }
}

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiOwnedBy(Wallets::class)
sealed class WalletOwner {
    @Serializable
    @SerialName("user")
    data class User(val username: String) : WalletOwner()

    @Serializable
    @SerialName("project")
    data class Project(val projectId: String) : WalletOwner()
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
    val numberOfProducts: Long,
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
        checkMinimumValue(this::numberOfProducts, numberOfProducts, 1)
        checkMinimumValue(this::units, units, 1)
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
    val startDate: Long? = null,
    @UCloudApiDoc(
        """
        A timestamp for when this deposit should become invalid
        
        This value must overlap with the source allocation. A value of null indicates that the allocation will never
        expire.
    """
    )
    val endDate: Long? = null,
    @UCloudApiDoc("An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions")
    var transactionId: String = Random.nextLong().toString() + Time.now()
)

typealias DepositToWalletResponse = Unit

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class TransferToWalletRequestItem(
    @UCloudApiDoc("The category to transfer from")
    val categoryId: ProductCategoryId,
    @UCloudApiDoc("The target wallet to insert the credits into")
    val target: WalletOwner,
    @UCloudApiDoc("The source wallet from where the credits is transferred from")
    val source: WalletOwner,
    @UCloudApiDoc("The amount of credits to transfer")
    val amount: Long,
    @UCloudApiDoc(
        """
        A timestamp for when this deposit should become valid
        
        This value must overlap with the source allocation. A value of null indicates that the allocation becomes valid
        immediately.
    """
    )
    val startDate: Long? = null,
    @UCloudApiDoc(
        """
        A timestamp for when this deposit should become invalid
        
        This value must overlap with the source allocation. A value of null indicates that the allocation will never
        expire.
    """
    )
    val endDate: Long? = null,
    @UCloudApiDoc("An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions")
    var transactionId: String = Random.nextLong().toString() + Time.now()
)

typealias TransferToWalletResponse = Unit

@Serializable
data class UpdateAllocationRequestItem(
    val id: String,
    val balance: Long,
    val startDate: Long,
    val endDate: Long?,
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
    val startDate: Long? = null,
    val endDate: Long? = null,
    var transactionId: String = Random.nextLong().toString() + Time.now()
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
            - $CALL_REF accounting.transfer: Creates a new root allocation from a parent allocation. The new allocation 
              will have no parents. The balance of the parent allocation is immediately removed, in full.

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

                comment("""
                    In this example, we will be performing some simple charge requests for an absolute 
                    product. Before and after each charge, we will show the current state of the system.
                    We will perform the charges on a root allocation, that is, it has no ancestors.
                """.trimIndent())

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

                comment("""
                    In this example, we will be performing some simple charge requests for a differential 
                    product. Before and after each charge, we will show the current state of the system.
                    We will perform the charges on a root allocation, that is, it has no ancestors.
                """.trimIndent())

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

                comment("The new charge reports that we are only using 50 GB, that is data was removed since last " +
                        "period.")

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
            chargeAbsoluteSingleUseCase,
            "Charging a leaf allocation (Absolute)",
            flow = {
                val ucloud = ucloudCore()
                val piRoot = actor("The PI of the root project", "piRoot")
                val piLeaf = actor("The PI of the leaf project", "piLeaf")

                val rootOwner = WalletOwner.Project("root-project")
                val leafOwner = WalletOwner.Project("leaf-project")

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
            }
        )
    }

    val charge = call<BulkRequest<ChargeWalletRequestItem>, ChargeWalletResponse, CommonErrorMessage>(
        "charge"
    ) {
        httpUpdate(baseContext, "charge", roles = Roles.SERVICE)
    }

    val deposit = call<BulkRequest<DepositToWalletRequestItem>, DepositToWalletResponse, CommonErrorMessage>(
        "deposit"
    ) {
        httpUpdate(baseContext, "deposit")
    }

    val transfer = call<BulkRequest<TransferToWalletRequestItem>, TransferToWalletResponse, CommonErrorMessage>(
        "transfer"
    ) {
        httpUpdate(baseContext, "transfer")
    }

    val updateAllocation = call<BulkRequest<UpdateAllocationRequestItem>, UpdateAllocationResponse, CommonErrorMessage>(
        "updateAllocation"
    ) {
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

    val check = call<BulkRequest<ChargeWalletRequestItem>, BulkResponse<Boolean>, CommonErrorMessage>("check") {
        httpUpdate(baseContext, "check", roles = Roles.SERVICE)

        documentation {
            summary = "Checks if one or more wallets are able to carry a charge"
            description = """
                    Checks if one or more charges would succeed without lacking credits. This will not generate a
                    transaction message, and as a result, the description will never be used.
                """.trimIndent()
        }
    }

    val rootDeposit = call<BulkRequest<RootDepositRequestItem>, Unit, CommonErrorMessage>("rootDeposit") {
        httpUpdate(baseContext, "rootDeposit", roles = Roles.PRIVILEGED)
    }
}
