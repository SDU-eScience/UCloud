package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.checkDeicReferenceFormat
import dk.sdu.cloud.provider.api.translateToChargeType
import dk.sdu.cloud.provider.api.translateToProductPriceUnit
import dk.sdu.cloud.service.Time
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@UCloudApiInternal(InternalLevel.BETA)
object AccountingV2 : CallDescriptionContainer("accounting.v2") {
    const val baseContext = "/api/accounting/v2"

    private fun StringBuilder.ln(data: Any?) = appendLine(data)
    private val callRef = "$CALL_REF $namespace"
    override fun documentation() {
        this.description = buildString {
            ln("Tracks resource usage")
            ln("")

            documentationAllocations()
            documentationReportingFromProvider()
            documentationInternalUtilities()
        }
    }

    // Allocation management
    // =================================================================================================================
    val rootAllocate = RootAllocate.call
    val subAllocate = SubAllocate.call
    val updateAllocation = UpdateAllocation.call
    val browseSubAllocations = BrowseSubAllocations.call
    val searchSubAllocations = SearchSubAllocations.call
    val retrieveAllocationRecipient = RetrieveAllocationRecipient.call
    val browseAllocationsInternal = BrowseAllocationsInternal.call

    private fun StringBuilder.documentationAllocations() {
        ln("""
            The goal of UCloud's accounting system is to:

            1. Allow or deny access to a provider's service catalog
            2. Track consumption of resources at the workspace level
            3. Generate visualizations and reports which track historical consumption data
            
            ## Allocations: Granting access to a service catalog

            UCloud achieves the first point by having the ability to grant resource allocations. A resource allocation 
            is also known as a `WalletAllocation`. They grant a workspace the ability to use `Product`s from a 
            specific `ProductCategory`. Unless otherwise stated, a workspace must always hold an allocation to use a 
            product. If a workspace does not hold an allocation, then the accounting system will deny access to them. 
            An allocation sets several limits on how the workspace can use the products. This includes:

            - An allocation is only valid for the `Product`s belonging to a single category. For example, if a 
              workspace has an allocation for `u1-standard` then it does not grant access to `u1-gpu`.
            - An allocation has a start date and an end date. Outside of this period, the allocation is invalid.
            - Each allocation have an associated quota. If a workspace is using more than the quota allows, then the 
              provider should deny access to the `Product`.

            ---

            __📝NOTE:__ It is the responsibility of the provider and not UCloud's accounting system to deny access 
            to a resource when the quota is exceeded. UCloud assists in this process by telling providers when a 
            workspace exceeds their quota. But the responsibility lies with the providers, as they usually have more 
            information. UCloud will only check for the existence of a valid allocation before forwarding the request.

            ---

            Resource allocations are hierarchical in UCloud. In practice, this means that all allocations can have 
            either 0 or 1 parent allocation. Allocations which do not have a parent are root allocations. Only UCloud 
            administrators/provider administrators can create root allocations. Administrators of a workspace can 
            "sub-allocate" their own allocations. This will create a new allocation which has one of their existing 
            allocations as the parent. UCloud allows for over-allocation when creating sub-allocations. UCloud avoids 
            over-spending by making sure that the usage in a sub-tree doesn't exceed the quota specified in the root 
            of the sub-tree. For example, consider the following sub-allocation created by a workspace administrator:

            ![](/backend/accounting-service/wiki/allocations-2-1.png)

            They can even create another which is even larger.

            ![](/backend/accounting-service/wiki/allocations-2-2.png)

            The sub-allocations themselves can continue to create new sub-allocations. These hierarchies can be as 
            complex as they need to be.

            ![](/backend/accounting-service/wiki/allocations-2-3.png)

            In the above example neither "Research 1" or "Research 2" can have a usage above 10GB due to their 
            parent. Similarly, if the combined usage goes above 10GB then UCloud will lock both of the allocations.
            
            ### Summary
            
            __Important concepts:__
            
            - $TYPE_REF WalletAllocation: Stores a resource allocation which grants a workspace access to a
              ProductCategory
            - $TYPE_REF Wallet: Combines multiple allocations, belonging to the same workspace for a specific category.
              The accounting system spreads out usages evenly across all allocations in a Wallet.
            - Allocations form a hierarchy. Over-allocation is allowed but the combined usage in a single allocation 
              tree must not exceed the quota in the root.
              
            __Important calls:__
            
            - $callRef.rootAllocate and $callRef.subAllocate: Create new allocations.
            - $callRef.updateAllocation: Update an allocation.
            - $callRef.browseSubAllocations, $callRef.searchSubAllocations $callRef.browseAllocationsInternal: Browse 
              through your sub allocations.
            - $callRef.browseWallets and $callRef.browseWalletsInternal: Browse through your wallets.
              
        """.trimIndent())
    }

    object RootAllocate {
        @Serializable
        data class RequestItem(
            val owner: WalletOwner,
            val productCategory: ProductCategoryIdV2,
            val quota: Long,
            val start: Long,
            val end: Long,

            val deicAllocationId: String? = null,

            val forcedSync: Boolean = false
        )

        val call = call(
            "rootAllocate",
            BulkRequest.serializer(RequestItem.serializer()),
            BulkResponse.serializer(FindByStringId.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "rootAllocate", roles = Roles.ADMIN)
            }
        )
    }

    object SubAllocate {
        @Serializable
        data class RequestItem(
            val parentAllocation: String,
            val owner: WalletOwner,
            val quota: Long,
            val start: Long,
            val end: Long?,

            val dry: Boolean = false,

            val grantedIn: Long? = null,
            val deicAllocationId: String? = null
        )

        val call = call(
            "subAllocate",
            BulkRequest.serializer(RequestItem.serializer()),
            BulkResponse.serializer(FindByStringId.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "subAllocate")
            }
        )
    }

    object UpdateAllocation {
        @Serializable
        @UCloudApiInternal(InternalLevel.BETA)
        data class RequestItem(
            val allocationId: String,

            val newQuota: Long?,
            var newStart: Long?,
            val newEnd: Long?,

            val reason: String
        )

        val call = call(
            "updateAllocation",
            BulkRequest.serializer(RequestItem.serializer()),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "updateAllocation")
            }
        )
    }

    object BrowseSubAllocations {
        @Serializable
        @UCloudApiInternal(InternalLevel.BETA)
        data class Request(
            override val filterType: ProductType? = null,
            override val itemsPerPage: Int? = null,
            override val next: String? = null,
            override val consistency: PaginationRequestV2Consistency? = null,
            override val itemsToSkip: Long? = null,
        ) : SubAllocationQuery

        val call = call(
            "browseSubAllocations",
            Request.serializer(),
            PageV2.serializer(SubAllocationV2.serializer()),
            CommonErrorMessage.serializer()
        ) {
            httpBrowse(baseContext, "subAllocation")

            documentation {
                summary = "Browses the catalog of sub-allocations"
                description = """
                    This endpoint will find all $TYPE_REF WalletAllocation s which are direct children of one of your
                    accessible $TYPE_REF WalletAllocation s.
                """.trimIndent()
            }
        }
    }

    object SearchSubAllocations {
        @Serializable
        @UCloudApiInternal(InternalLevel.BETA)
        data class Request(
            val query: String,
            override val filterType: ProductType? = null,
            override val itemsPerPage: Int? = null,
            override val next: String? = null,
            override val consistency: PaginationRequestV2Consistency? = null,
            override val itemsToSkip: Long? = null,
        ) : SubAllocationQuery

        val call = call(
            "searchSubAllocations",
            WalletsSearchSubAllocationsV2Request.serializer(),
            PageV2.serializer(SubAllocationV2.serializer()),
            CommonErrorMessage.serializer()
        ) {
            httpSearch(baseContext, "subAllocation")
            documentation {
                summary = "Searches the catalog of sub-allocations"
                description = """
                This endpoint will find all $TYPE_REF WalletAllocation s which are direct children of one of your
                accessible $TYPE_REF WalletAllocation s.
            """.trimIndent()
            }
        }
    }

    object RetrieveAllocationRecipient {
        @Serializable
        @UCloudApiInternal(InternalLevel.BETA)
        data class Request(
            val query: String,
        )

        @Serializable
        @UCloudApiInternal(InternalLevel.BETA)
        data class Response(
            val id: String,
            val isProject: Boolean,
            val title: String,
            val principalInvestigator: String,
            val numberOfMembers: Int,
        )

        val call = call(
            "retrieveAllocationRecipient",
            Request.serializer(),
            Response.serializer(),
            CommonErrorMessage.serializer()
        ) {
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

    object BrowseAllocationsInternal {
        @Serializable
        data class Request(
            val owner: WalletOwner,
            val categoryId: ProductCategoryIdV2
        )

        @Serializable
        data class Response(
            val allocations: List<WalletAllocationV2>
        )

        val call = call(
            "retrieveAllocationsInternal",
            Request.serializer(),
            Response.serializer(),
            CommonErrorMessage.serializer()
        ) {
            httpUpdate(baseContext, "retrieveAllocationsInternal", roles = Roles.PRIVILEGED)

            documentation {
                summary = "Retrieves a list of product specific up-to-date allocation from the in-memory DB"
                description = """
                This endpoint will return a list of $TYPE_REF WalletAllocation s which are related to the given product
                available to the user.
                This is mainly for backend use. For frontend, use the browse call instead for a paginated response
            """.trimIndent()
            }
        }
    }

    // Interaction with wallets
    // =================================================================================================================
    val browseWallets = BrowseWallets.call
    val browseWalletsInternal = BrowseWalletsInternal.call

    object BrowseWallets {
        @Serializable
        @UCloudApiInternal(InternalLevel.BETA)
        data class Request(
            override val itemsPerPage: Int? = null,
            override val next: String? = null,
            override val consistency: PaginationRequestV2Consistency? = null,
            override val itemsToSkip: Long? = null,

            val filterType: ProductType? = null
        ) : WithPaginationRequestV2

        val call = call(
            "browseWallets",
            Request.serializer(),
            PageV2.serializer(WalletV2.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpBrowse(baseContext, "wallets")

                documentation {
                    summary = "Browses the catalog of accessible Wallets"
                }
            }
        )
    }

    object BrowseWalletsInternal {
        @Serializable
        data class Request(
            val owner: WalletOwner
        )

        @Serializable
        data class Response(
            val wallets: List<WalletV2>
        )

        val call = call(
            "browseWalletsInternal",
            Request.serializer(),
            Response.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "browseWalletsInternal", roles = Roles.PRIVILEGED)

                documentation {
                    summary = "Retrieves a list of up-to-date wallets"
                    description = """
                        This endpoint will return a list of $TYPE_REF Wallet s which are related to the active 
                        workspace. This is mainly for backend use. For frontend, use the browse call instead for a
                        paginated response
                    """.trimIndent()
                }
            }
        )
    }

    // Reporting from the provider
    // =================================================================================================================
    val reportDelta = ReportDelta.call
    val reportTotalUsage = ReportTotalUsage.call

    private fun StringBuilder.documentationReportingFromProvider() {}

    object ReportDelta {
        val call = call(
            "reportDelta",
            BulkRequest.serializer(UsageReportItem.serializer()),
            BulkResponse.serializer(Boolean.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "reportDelta", roles = Roles.PROVIDER)
            }
        )
    }

    object ReportTotalUsage {
        val call = call(
            "reportTotalUsage",
            BulkRequest.serializer(UsageReportItem.serializer()),
            BulkResponse.serializer(Boolean.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "reportTotalUsage", roles = Roles.PROVIDER)
            }
        )
    }

    // Internal service + provider utilities
    // =================================================================================================================
    val findRelevantProviders = FindRelevantProviders.call
    val browseProviderAllocations = BrowseProviderAllocations.call

    private fun StringBuilder.documentationInternalUtilities() {}

    object FindRelevantProviders {
        @Serializable
        data class RequestItem(
            val username: String,
            val project: String? = null,
            val useProject: Boolean
        )

        @Serializable
        data class Response(
            val providers: List<String>
        )

        val call = call(
            "findRelevantProviders",
            BulkRequest.serializer(RequestItem.serializer()),
            BulkResponse.serializer(Response.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "findRelevantProviders", Roles.PRIVILEGED)
            }
        )
    }

    object BrowseProviderAllocations {
        @Serializable
        @UCloudApiInternal(InternalLevel.BETA)
        data class Request(
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
        data class ResponseItem(
            val id: String,
            val owner: WalletOwner,
            val categoryId: ProductCategory,

            @UCloudApiDoc("The earliest timestamp which allows for the balance to be consumed")
            val notBefore: Long,

            @UCloudApiDoc("The earliest timestamp at which the reported balance is no longer fully usable")
            val notAfter: Long?,

            val quota: Long,
        )

        val call = call(
            "browseProviderAllocations",
            Request.serializer(),
            PageV2.serializer(ResponseItem.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "browseProviderAllocations", roles = Roles.PROVIDER)

                documentation {
                    summary = "Browses allocations relevant for a specific provider"
                    description = """
                        This endpoint is only usable by providers. The endpoint will return a stable results. 
                    """.trimIndent()
                }
            }
        )
    }
}

// Types
// =====================================================================================================================
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
    init {
        checkDeicReferenceFormat(deicAllocationId)
    }

    fun isLocked(): Boolean = (localUsage >= quota) || ((treeUsage ?: 0) >= quota)
    fun isActive(): Boolean = Time.now() in startDate..endDate

    @Suppress("DEPRECATION")
    fun toV1(): WalletAllocation = WalletAllocation(
        id,
        allocationPath,
        quota - (treeUsage ?: localUsage),
        quota,
        quota - localUsage,
        startDate,
        endDate,
        grantedIn,
        quota - (treeUsage ?: localUsage),
        canAllocate,
        allowSubAllocationsToAllocate
    )
}

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("A parent allocator's view of a `WalletAllocation`")
@UCloudApiInternal(InternalLevel.BETA)
data class SubAllocationV2(
    val id: String,
    val path: String,
    val startDate: Long,
    val endDate: Long?,

    val productCategory: ProductCategory,

    val workspaceId: String,
    val workspaceTitle: String,
    val workspaceIsProject: Boolean,
    val projectPI: String?,

    val usage: Long,
    val quota: Long,

    val grantedIn: Long?
) {
    @Suppress("DEPRECATION")
    fun toV1(): SubAllocation = SubAllocation(
        id,
        path,
        startDate,
        endDate,
        ProductCategoryId(productCategory.name, productCategory.provider),
        productCategory.productType,
        translateToChargeType(productCategory),
        translateToProductPriceUnit(productCategory.productType, productCategory.name),
        workspaceId,
        workspaceTitle,
        workspaceIsProject,
        projectPI,
        quota - usage,
        quota,
        grantedIn
    )
}

interface SubAllocationQuery : WithPaginationRequestV2 {
    val filterType: ProductType?
}

@Serializable
data class UsageReportItem(
    val owner: WalletOwner,
    val categoryIdV2: ProductCategoryIdV2,
    val usage: Long,
    val description: ChargeDescription
)

@Serializable
data class ChargeDescription(
    val description: String,
    val itemized: List<ItemizedCharge>
)

@Serializable
data class ItemizedCharge(
    val description: String,
    val usage: Long? = null,
    val productId: String? = null
)

@Serializable
data class WalletV2(
    val owner: WalletOwner,
    val paysFor: ProductCategory,
    val allocations: List<WalletAllocationV2>,
) {
    @Suppress("DEPRECATION")
    fun toV1() = Wallet(
        owner,
        ProductCategoryId(paysFor.name, paysFor.provider),
        allocations.map { it.toV1() },
        AllocationSelectorPolicy.EXPIRE_FIRST,
        paysFor.productType,
        chargeType = translateToChargeType(paysFor),
        unit = translateToProductPriceUnit(paysFor.productType, paysFor.name)
    )
}

// Useful type aliases
// =====================================================================================================================
@Deprecated("Replace with WalletsBrowseRequest")
typealias WalletBrowseRequestV2 = AccountingV2.BrowseWallets.Request
typealias WalletsBrowseRequestV2 = AccountingV2.BrowseWallets.Request
typealias WalletsInternalRetrieveRequest = AccountingV2.BrowseWalletsInternal.Request
typealias WalletsInternalV2RetrieveResponse = AccountingV2.BrowseWalletsInternal.Response
typealias SubAllocationRequestItem = AccountingV2.SubAllocate.RequestItem
typealias RootAllocationRequestItem = AccountingV2.RootAllocate.RequestItem
typealias UpdateAllocationV2RequestItem = AccountingV2.UpdateAllocation.RequestItem
typealias DeltaReportItem = UsageReportItem
typealias TotalReportItem = UsageReportItem
typealias FindRelevantProvidersRequestItem = AccountingV2.FindRelevantProviders.RequestItem
typealias FindRelevantProvidersResponse = AccountingV2.FindRelevantProviders.Response
typealias WalletAllocationsV2InternalRetrieveRequest = AccountingV2.BrowseAllocationsInternal.Request
typealias WalletAllocationsV2InternalRetrieveResponse = AccountingV2.BrowseAllocationsInternal.Response
typealias WalletsSearchSubAllocationsV2Request = AccountingV2.SearchSubAllocations.Request
typealias WalletsBrowseSubAllocationsV2Request = AccountingV2.BrowseSubAllocations.Request
typealias WalletsBrowseSubAllocationsV2Response = PageV2<SubAllocationV2>
typealias WalletsRetrieveRecipientRequest = AccountingV2.RetrieveAllocationRecipient.Request
typealias WalletsRetrieveRecipientResponse = AccountingV2.RetrieveAllocationRecipient.Response
typealias WalletsRetrieveProviderSummaryRequest = AccountingV2.BrowseProviderAllocations.Request
typealias ProviderWalletSummaryV2 = AccountingV2.BrowseProviderAllocations.ResponseItem
