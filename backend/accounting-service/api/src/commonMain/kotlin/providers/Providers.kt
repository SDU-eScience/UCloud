package dk.sdu.cloud.provider.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.Roles
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.auth.api.AccessToken
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.auth.api.AuthProvidersRefreshRequestItem
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.CALL_REF_LINK
import dk.sdu.cloud.calls.TYPE_REF
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.comment
import dk.sdu.cloud.calls.description
import dk.sdu.cloud.calls.httpRetrieve
import dk.sdu.cloud.calls.httpUpdate
import dk.sdu.cloud.calls.provider
import dk.sdu.cloud.calls.serializerEntry
import dk.sdu.cloud.calls.serializerLookupTable
import dk.sdu.cloud.calls.success
import dk.sdu.cloud.calls.ucloudCore
import dk.sdu.cloud.calls.useCase
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Provider(
    override val id: String,
    override val specification: ProviderSpecification,
    val refreshToken: String,
    val publicKey: String,
    override val createdAt: Long,
    override val status: ProviderStatus,
    override val updates: List<ProviderUpdate>,
    override val owner: ResourceOwner,
    override val permissions: ResourcePermissions? = null
) : Resource<Product, ProviderSupport> {
    override val billing = ResourceBilling.Free
    override val acl: List<ResourceAclEntry>? = null

    override fun toString(): String {
        return "Provider(id='$id', specification=$specification, createdAt=$createdAt, status=$status, " +
                "billing=$billing, owner=$owner)"
    }

    companion object {
        const val UCLOUD_CORE_PROVIDER = "ucloud_core"
    }
}

typealias ProviderAclPermission = Permission

@Serializable
data class ProviderSpecification(
    val id: String,
    val domain: String,
    val https: Boolean,
    val port: Int? = null,
) : ResourceSpecification {
    override val product: ProductReference = ProductReference("", "", Provider.UCLOUD_CORE_PROVIDER)
}

fun ProviderSpecification.addProviderInfoToRelativeUrl(url: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    return buildString {
        if (https) {
            append("https://")
        } else {
            append("http://")
        }

        append(domain)

        if (port != null) {
            append(":")
            append(port)
        }

        append('/')
        append(url.removePrefix("/"))
    }
}

@Serializable
data class ProviderSupport(override val product: ProductReference) : ProductSupport

@Serializable
data class ProviderStatus(
    override var resolvedSupport: ResolvedSupport<Product, ProviderSupport>? = null,
    override var resolvedProduct: Product? = null
) : ResourceStatus<Product, ProviderSupport>

@Serializable
data class ProviderUpdate(
    override val timestamp: Long,
    override val status: String? = null,
) : ResourceUpdate

@Serializable
data class ProvidersUpdateAclRequestItem(
    val id: String,
    val acl: List<ResourceAclEntry>
)

@Serializable
data class ProvidersRenewRefreshTokenRequestItem(val id: String)
typealias ProvidersRenewRefreshTokenResponse = Unit

typealias ProvidersRetrieveRequest = FindByStringId
typealias ProvidersRetrieveResponse = Provider

@Serializable
data class ProvidersBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null
) : WithPaginationRequestV2

typealias ProvidersBrowseResponse = PageV2<Provider>

typealias ProvidersRetrieveSpecificationRequest = FindByStringId
typealias ProvidersRetrieveSpecificationResponse = ProviderSpecification

@Serializable
data class ProviderIncludeFlags(
    override val includeOthers: Boolean = false,
    override val includeUpdates: Boolean = false,
    override val includeSupport: Boolean = false,
    override val includeProduct: Boolean = false,
    override val filterCreatedBy: String? = null,
    override val filterCreatedAfter: Long? = null,
    override val filterCreatedBefore: Long? = null,
    override val filterProvider: String? = null,
    override val filterProductId: String? = null,
    override val filterProductCategory: String? = null,
    override val filterProviderIds: String? = null,
    override val filterIds: String? = null,
    val filterName: String? = null,
) : ResourceIncludeFlags

@Serializable
sealed class ProvidersRequestApprovalRequest {
    @Serializable
    @SerialName("information")
    data class Information(val specification: ProviderSpecification) : ProvidersRequestApprovalRequest()

    @Serializable
    @SerialName("sign")
    data class Sign(val token: String) : ProvidersRequestApprovalRequest()
}

@Serializable
sealed class ProvidersRequestApprovalResponse {
    @Serializable
    @SerialName("requires_signature")
    data class RequiresSignature(val token: String) : ProvidersRequestApprovalResponse()

    @Serializable
    @SerialName("awaiting_admin_approval")
    data class AwaitingAdministratorApproval(val token: String) : ProvidersRequestApprovalResponse()
}

@Serializable
data class ProvidersApproveRequest(val token: String)
typealias ProvidersApproveResponse = FindByStringId

object Providers : ResourceApi<Provider, ProviderSpecification, ProviderUpdate, ProviderIncludeFlags, ProviderStatus,
        Product, ProviderSupport>("providers") {
    init {
        serializerLookupTable = mapOf(
            serializerEntry(ProvidersRequestApprovalRequest.serializer()),
            serializerEntry(ProvidersRequestApprovalResponse.serializer()),
        )

        description = """
            Providers, the backbone of UCloud, expose compute and storage resources to end-users.

            UCloud/Core is an orchestrator of $TYPE_REF Resource s. This means, that the core doesn't actually know how 
            to serve files or run computational workloads. Instead, the core must ask one or more $TYPE_REF Provider s 
            to fulfil requests from the user.

            ![](/backend/accounting-service/wiki/overview.png)

            __Figure:__ UCloud/Core receives a request from the user and forwards it to a provider.

            The core isn't a simple proxy. Before passing the request, UCloud performs the following tasks:

            - __Authentication:__ UCloud ensures that users have authenticated.
            - __Authorization:__ The $TYPE_REF dk.sdu.cloud.project.api.Project system of UCloud brings role-based 
              authorization to all $TYPE_REF Resource s. The core verifies all actions before forwarding the request.
            - __Resolving references:__ UCloud maintains a catalog of all $TYPE_REF Resource s in the system. All user 
              requests only contain a reference to these $TYPE_REF Resource s. UCloud verifies and resolves all 
              references before proxying the request.

            The communication between UCloud/Core and the provider happens through the __provider APIs__. Throughout the 
            developer guide, you will find various sections describing these APIs. These APIs contain both an ingoing 
            (from the provider's perspective) and outgoing APIs. This allows for bidirectional communication between 
            both parties. In almost all cases, the communication from the user goes through UCloud/Core. The only 
            exception to this rule is when the data involved is either sensitive or large. In these cases, UCloud will 
            only be responsible for facilitating direct communication. A common example of this is 
            [file uploads]($CALL_REF_LINK files.createUpload).
        """.trimIndent()
    }

    private const val authenticationUseCase = "authentication"

    override fun documentation() {
        useCase(
            authenticationUseCase,
            "Provider authenticating with UCloud/Core",
            preConditions = listOf(
                "The provider has already been registered with UCloud/Core",
            ),
            flow = {
                val ucloud = ucloudCore()
                val provider = provider()

                success(
                    AuthProviders.refresh,
                    bulkRequestOf(
                        AuthProvidersRefreshRequestItem("fb69e4367ee0fe4c76a4a926394aee547a41d998")
                    ),
                    BulkResponse(
                        listOf(
                            AccessToken(
                                "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9." +
                                        "eyJzdWIiOiIjUF9leGFtcGxlIiwicm9sZSI6IlBST1ZJREVSIiwiaWF0IjoxNjMzNTIxMDA5LCJleHAiOjE2MzM1MjE5MTl9." +
                                        "P4zL-LBeahsga4eH0GqKpBmPf-Sa7pU70QhiXB1BchBe0DE9zuJ_6fws9cs9NOIo"
                            )
                        )
                    ),
                    provider
                )

                comment("📝 Note: The tokens shown here are not representative of tokens you will see in practice")
            }
        )
    }

    override val typeInfo = ResourceTypeInfo<Provider, ProviderSpecification, ProviderUpdate, ProviderIncludeFlags,
            ProviderStatus, Product, ProviderSupport>()

    override val create get() = super.create!!
    override val delete: Nothing? = null
    override val search get() = super.search!!

    val renewToken = call<BulkRequest<ProvidersRenewRefreshTokenRequestItem>,
            ProvidersRenewRefreshTokenResponse, CommonErrorMessage>("renewToken") {
        httpUpdate(baseContext, "renewToken")
    }

    val retrieveSpecification = call<ProvidersRetrieveSpecificationRequest,
            ProvidersRetrieveSpecificationResponse, CommonErrorMessage>("retrieveSpecification") {
        httpRetrieve(baseContext, "specification", roles = Roles.PRIVILEGED)
    }

    val requestApproval = call<ProvidersRequestApprovalRequest, ProvidersRequestApprovalResponse, CommonErrorMessage>(
        "requestApproval"
    ) {
        httpUpdate(baseContext, "requestApproval", roles = Roles.PUBLIC)
    }

    val approve = call<ProvidersApproveRequest, ProvidersApproveResponse, CommonErrorMessage>("approve") {
        httpUpdate(baseContext, "approve", roles = Roles.PUBLIC)
    }
}
