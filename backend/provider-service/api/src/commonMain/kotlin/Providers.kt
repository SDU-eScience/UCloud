package dk.sdu.cloud.provider.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductReference
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
    override val billing: ProviderBilling,
    override val owner: ProviderOwner,
    override val acl: List<ResourceAclEntry<ProviderAclPermission>>,
    override val permissions: ResourcePermissions? = null
) : Resource<ProviderAclPermission> {
    override fun toString(): String {
        return "Provider(id='$id', specification=$specification, createdAt=$createdAt, status=$status, " +
            "billing=$billing, owner=$owner)"
    }
}

@Serializable
enum class ProviderAclPermission {
    EDIT
}

@Serializable
data class ProviderSpecification(
    val id: String,
    val domain: String,
    val https: Boolean,
    val port: Int? = null,
) : ResourceSpecification {
    override val product: ProductReference? = null
}

@Serializable
class ProviderStatus : ResourceStatus
@Serializable
class ProviderBilling(override val pricePerUnit: Long, override val creditsCharged: Long) : ResourceBilling

@Serializable
data class ProviderUpdate(
    override val timestamp: Long,
    override val status: String? = null,
) : ResourceUpdate

@Serializable
data class ProviderOwner(
    override val createdBy: String,
    override val project: String? = null,
) : ResourceOwner

@Serializable
data class ProvidersUpdateAclRequestItem(
    val id: String,
    val acl: List<ResourceAclEntry<ProviderAclPermission>>
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

object Providers : CallDescriptionContainer("providers") {
    const val baseContext = "/api/providers"

    init {
        serializerLookupTable = mapOf(
            serializerEntry(ProvidersRequestApprovalRequest.serializer()),
            serializerEntry(ProvidersRequestApprovalResponse.serializer()),
        )
    }

    val create = call<BulkRequest<ProviderSpecification>, BulkResponse<FindByStringId>, CommonErrorMessage>("create") {
        httpCreate(baseContext, roles = Roles.PRIVILEGED)
    }

    val updateAcl = call<BulkRequest<ProvidersUpdateAclRequestItem>, Unit, CommonErrorMessage>("updateAcl") {
        httpUpdate(baseContext, "updateAcl")
    }

    val renewToken = call<BulkRequest<ProvidersRenewRefreshTokenRequestItem>,
        ProvidersRenewRefreshTokenResponse, CommonErrorMessage>("renewToken") {
        httpUpdate(baseContext, "renewToken")
    }

    val retrieve = call<ProvidersRetrieveRequest, ProvidersRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext, roles = Roles.AUTHENTICATED)
    }

    val browse = call<ProvidersBrowseRequest, ProvidersBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
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
