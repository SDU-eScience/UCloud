package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.checkDeicReferenceFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class WalletAllocationV2(
    val id: String,
    val allocationPath: List<String>,
    val localUsage: Long,
    val quota: Long,
    val treeUsage: Long? = null,

    val startDate: Long,
    val endDate: Long,

    val grantedIn: Long? = null,
    val deicAllocationId: String? = null,

    val canAllocate: Boolean = false,
    val allowSubAllocationsToAllocate: Boolean = true
) {
    //TODO(HENRIK) NOT CORRECT?
    fun isLocked():Boolean = localUsage > quota

    init {
        checkDeicReferenceFormat(deicAllocationId)
    }
}

@Serializable
data class WalletAllocationsV2InternalRetrieveRequest (
    val owner: WalletOwner,
    val categoryId: ProductCategoryIdV2
)
@Serializable
data class WalletAllocationsV2InternalRetrieveResponse(
    val allocations: List<WalletAllocationV2>
)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("A parent allocator's view of a `WalletAllocation`")
@UCloudApiInternal(InternalLevel.BETA)
data class SubAllocationV2(
    val id: String,
    val path: String,
    val startDate: Long,
    val endDate: Long?,

    val productCategoryId: ProductCategory,

    val workspaceId: String,
    val workspaceTitle: String,
    val workspaceIsProject: Boolean,
    val projectPI: String?,

    val remaining: Long,
    val initialBalance: Long,

    val grantedIn: Long?
)

interface SubAllocationQuery : WithPaginationRequestV2 {
    val filterType: ProductType?
}

@Serializable
@UCloudApiInternal(InternalLevel.BETA)
data class WalletsSearchSubAllocationsV2Request(
    val query: String,
    override val filterType: ProductType? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : SubAllocationQuery

@Serializable
@UCloudApiInternal(InternalLevel.BETA)
data class WalletsBrowseSubAllocationsV2Request(
    override val filterType: ProductType? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : SubAllocationQuery

typealias WalletsBrowseSubAllocationsV2Response = PageV2<SubAllocationV2>

@Serializable
@UCloudApiInternal(InternalLevel.BETA)
data class WalletsRetrieveRecipientRequest(
    val query: String,
)

@Serializable
@UCloudApiInternal(InternalLevel.BETA)
data class WalletsRetrieveRecipientResponse(
    val id: String,
    val isProject: Boolean,
    val title: String,
    val principalInvestigator: String,
    val numberOfMembers: Int,
)

typealias ResetState = Unit

@Serializable
@UCloudApiInternal(InternalLevel.BETA)
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
@UCloudApiInternal(InternalLevel.BETA)
data class ProviderWalletSummaryV2(
    val id: String,
    val owner: WalletOwner,
    val categoryId: ProductCategory,

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

@UCloudApiInternal(InternalLevel.BETA)
object WalletAllocationsV2 : CallDescriptionContainer("accounting.walletallocations") {
    const val baseContext = "/api/accounting/walletallocations"

    @UCloudApiExperimental(ExperimentalLevel.BETA)
    val register = call("register", BulkRequest.serializer(RegisterWalletRequestItem.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "register", roles = Roles.PROVIDER)

        documentation {
            summary = "Registers an allocation created outside of UCloud"
            description = "Be careful with using this endpoint as it might go away in a future release."
        }
    }

    val retrieveAllocationsInternal = call(
        "retrieveAllocationsInternal",
        WalletAllocationsV2InternalRetrieveRequest.serializer(),
        WalletAllocationsV2InternalRetrieveResponse.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpUpdate(baseContext, "retrieveAllocationsInternalV2")

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

    val searchSubAllocations = call("searchSubAllocations", WalletsSearchSubAllocationsV2Request.serializer(), PageV2.serializer(SubAllocationV2.serializer()), CommonErrorMessage.serializer()) {
        httpSearch(baseContext, "subAllocationV2")
        documentation {
            summary = "Searches the catalog of sub-allocations"
            description = """
                This endpoint will find all $TYPE_REF WalletAllocation s which are direct children of one of your
                accessible $TYPE_REF WalletAllocation s.
            """.trimIndent()
        }
    }

    val browseSubAllocations = call("browseSubAllocations", WalletsBrowseSubAllocationsV2Request.serializer(), PageV2.serializer(SubAllocationV2.serializer()), CommonErrorMessage.serializer()) {
        httpBrowse(baseContext, "subAllocation")

        documentation {
            summary = "Browses the catalog of sub-allocations"
            description = """
                This endpoint will find all $TYPE_REF WalletAllocation s which are direct children of one of your
                accessible $TYPE_REF WalletAllocation s.
            """.trimIndent()
        }
    }

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
