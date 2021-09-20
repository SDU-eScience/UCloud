package dk.sdu.cloud.provider.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.calls.*
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
    override val filterProviderId: String? = null,
    override val filterIds: String? = null,
    val filterName: String? = null,
) : ResourceIncludeFlags

@Serializable
sealed class ProvidersRequestApprovalRequest {
    @Serializable
    @SerialName("information")
    data class Information(val specification: ProviderSpecification): ProvidersRequestApprovalRequest()

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
