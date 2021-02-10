package dk.sdu.cloud.provider.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.PaginationRequestV2Consistency
import dk.sdu.cloud.service.WithPaginationRequestV2

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
    override val acl: List<ProviderAclEntry>
) : Resource<ProviderAclPermission>

enum class ProviderAclPermission {
    EDIT
}

class ProviderSpecification(
    val id: String,
    val domain: String,
    val https: Boolean,
    val port: Int? = null,
    val manifest: ProviderManifest = ProviderManifest(),
) : ResourceSpecification {
    override val product: ProductReference? = null
}

class ProviderStatus : ResourceStatus
class ProviderBilling(override val pricePerUnit: Long, override val creditsCharged: Long) : ResourceBilling

data class ProviderUpdate(
    override val timestamp: Long,
    override val status: String?,
) : ResourceUpdate

data class ProviderOwner(
    override val createdBy: String,
    override val project: String?,
) : ResourceOwner

data class ProviderAclEntry(
    override val entity: AclEntity,
    override val permissions: List<ProviderAclPermission>,
) : ResourceAclEntry<ProviderAclPermission>

@UCloudApiDoc("""The `ProviderManifest` contains general metadata about the provider.

The manifest, for example, includes information about which `features` are supported by a provider. """)
data class ProviderManifest(
    @UCloudApiDoc("Contains information about the features supported by this provider")
    val features: ManifestFeatureSupport = ManifestFeatureSupport(),
)

data class ManifestAndId(
    val id: String,
    val manifest: ProviderManifest
)

@UCloudApiDoc("""Contains information about the features supported by this provider
    
Features are by-default always disabled. There is _no_ minimum set of features a provider needs to support.""")
data class ManifestFeatureSupport(
    @UCloudApiDoc("Determines which compute related features are supported by this provider")
    val compute: Compute = Compute(),
) {
    data class Compute(
        @UCloudApiDoc("Support for `Tool`s using the `DOCKER` backend")
        val docker: Docker = Docker(),

        @UCloudApiDoc("Support for `Tool`s using the `VIRTUAL_MACHINE` backend")
        val virtualMachine: VirtualMachine = VirtualMachine(),
    ) {
        data class Docker(
            @UCloudApiDoc("Flag to enable/disable this feature\n\nAll other flags are ignored if this is `false`.")
            var enabled: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the interactive interface of `WEB` `Application`s")
            var web: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the interactive interface of `VNC` `Application`s")
            var vnc: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable `BATCH` `Application`s")
            var batch: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the log API")
            var logs: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the interactive terminal API")
            var terminal: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable connection between peering `Job`s")
            var peers: Boolean = false,
        )

        data class VirtualMachine(
            @UCloudApiDoc("Flag to enable/disable this feature\n\nAll other flags are ignored if this is `false`.")
            var enabled: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the log API")
            var logs: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the VNC API")
            var vnc: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the interactive terminal API")
            var terminal: Boolean = false,
        )
    }
}

data class ProvidersUpdateAclRequestItem(
    val id: String,
    val acl: List<ProviderAclEntry>
)

data class ProvidersRenewRefreshTokenRequestItem(val id: String)
typealias ProvidersRenewRefreshTokenResponse = Unit

typealias ProvidersRetrieveRequest = FindByStringId
typealias ProvidersRetrieveResponse = Provider

data class ProvidersBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null
) : WithPaginationRequestV2

typealias ProvidersBrowseResponse = PageV2<Provider>

object Providers : CallDescriptionContainer("providers") {
    const val baseContext = "/api/providers"

    val create = call<BulkRequest<ProviderSpecification>, BulkResponse<FindByStringId>, CommonErrorMessage>("create") {
        httpCreate(baseContext, roles = Roles.PRIVILEGED)
    }

    val updateManifest = call<BulkRequest<ManifestAndId>, Unit, CommonErrorMessage>("updateManifest") {
        httpUpdate(baseContext, "updateManifest")
    }

    val updateAcl = call<BulkRequest<ProvidersUpdateAclRequestItem>, Unit, CommonErrorMessage>("updateAcl") {
        httpUpdate(baseContext, "updateAcl")
    }

    val renewToken = call<BulkRequest<ProvidersRenewRefreshTokenRequestItem>,
        ProvidersRenewRefreshTokenResponse, CommonErrorMessage>("renewToken") {
        httpUpdate(baseContext, "renewToken")
    }

    val retrieve = call<ProvidersRetrieveRequest, ProvidersRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext)
    }

    val browse = call<ProvidersBrowseRequest, ProvidersBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }
}
