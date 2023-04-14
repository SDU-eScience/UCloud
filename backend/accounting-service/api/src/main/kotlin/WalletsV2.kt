package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class WalletV2(
    val owner: WalletOwner,
    val paysFor: ProductCategory,
    val allocations: List<WalletAllocationV2>,
)

@Serializable
@UCloudApiExperimental(UCloudApiMaturity.Experimental.Level.BETA)
data class RegisterWalletRequestItem(
    val owner: WalletOwner,
    val uniqueAllocationId: String,
    val categoryId: String,
    val balance: Long,
    val providerGeneratedId: String? = null,
)

@Serializable
@UCloudApiInternal(InternalLevel.BETA)
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
data class WalletsInternalV2RetrieveResponse(
    val wallets: List<WalletV2>
)

@UCloudApiInternal(InternalLevel.BETA)
object WalletsV2 : CallDescriptionContainer("accounting.walletsv2") {
    const val baseContext = "/api/accounting/wallets"

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
