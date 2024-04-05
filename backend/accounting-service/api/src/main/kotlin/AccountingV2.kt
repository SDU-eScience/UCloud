package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
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
    val updateAllocation = UpdateAllocation.call

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
            val category: ProductCategoryIdV2,
            val quota: Long,
            val start: Long,
            val end: Long,
        )

        val call = call(
            "rootAllocate",
            BulkRequest.serializer(RequestItem.serializer()),
            BulkResponse.serializer(FindByStringId.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "rootAllocate")
            }
        )
    }

    object UpdateAllocation {
        @Serializable
        @UCloudApiInternal(InternalLevel.BETA)
        data class RequestItem(
            val allocationId: Long,

            val newQuota: Long? = null,
            var newStart: Long? = null,
            val newEnd: Long? = null,

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

            val filterType: ProductType? = null,

            val includeChildren: Boolean = false,
            val childrenQuery: String? = null,
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
    val reportUsage = ReportUsage.call
    val checkProviderUsable = CheckProviderUsable.call

    private fun StringBuilder.documentationReportingFromProvider() {}

    object ReportUsage {
        val call = call(
            "reportUsage",
            BulkRequest.serializer(UsageReportItem.serializer()),
            BulkResponse.serializer(Boolean.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "reportUsage", roles = Roles.PROVIDER)
            }
        )
    }

    object CheckProviderUsable {
        @Serializable
        data class RequestItem(
            val owner: WalletOwner,
            val category: ProductCategoryIdV2,
        )

        @Serializable
        data class ResponseItem(
            val maxUsable: Long,
        )

        val call = call(
            "checkProviderUsable",
            BulkRequest.serializer(RequestItem.serializer()),
            BulkResponse.serializer(ResponseItem.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "checkProviderUsable", roles = Roles.PROVIDER)
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
            val useProject: Boolean,
            val filterProductType: ProductType? = null,
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
data class AllocationGroup(
    val id: Int,
    val allocations: List<Alloc>,
    val usage: Long,
) {
    @Serializable
    data class Alloc(
        val id: Long,
        val startDate: Long,
        val endDate: Long,
        val quota: Long,
        val grantedIn: Long?,
        val retiredUsage: Long?,
    )
}

@Serializable
data class AllocationGroupWithParent(
    val parent: ParentOrChildWallet?,
    val group: AllocationGroup,
)

@Serializable
data class AllocationGroupWithChild(
    val child: ParentOrChildWallet,
    val group: AllocationGroup,
)

@Serializable
data class UsageReportItem(
    val isDeltaCharge: Boolean,
    val owner: WalletOwner,
    val categoryIdV2: ProductCategoryIdV2,
    val usage: Long,
    val description: ChargeDescription
)

@Serializable
data class ChargeDescription(
    val scope: String? = null,
    val description: String? = null,
)

@Serializable
data class WalletV2(
    val owner: WalletOwner,
    val paysFor: ProductCategory,

    val allocationGroups: List<AllocationGroupWithParent>,
    val children: List<AllocationGroupWithChild>?,

    val totalUsage: Long,
    val localUsage: Long,
    val maxUsable: Long,
    val quota: Long,
    val totalAllocated: Long,

    val lastSignificantUpdateAt: Long,
)

@Serializable
data class ParentOrChildWallet(
    val projectId: String?,
    val projectTitle: String,
)
