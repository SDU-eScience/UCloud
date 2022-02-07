package dk.sdu.cloud.app.kubernetes.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.app.orchestrator.api.LicenseProvider
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class KubernetesLicense(
    val id: String,
    val address: String,
    val port: Int,
    val tags: List<String> = emptyList(),
    val license: String? = null,
    val category: ProductCategoryId,
    val pricePerUnit: Long = 1_000_000,
    val description: String = "",
    val hiddenInGrantApplications: Boolean = false,
    val priority: Int = 0,
)

interface KubernetesLicenseFilter {
    val tag: String?
}

typealias KubernetesLicenseCreateRequest = BulkRequest<KubernetesLicense>
typealias KubernetesLicenseCreateResponse = Unit

@Serializable
data class KubernetesLicenseBrowseRequest(
    override val tag: String? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null
) : WithPaginationRequestV2, KubernetesLicenseFilter
typealias KubernetesLicenseBrowseResponse = PageV2<KubernetesLicense>

typealias KubernetesLicenseDeleteRequest = BulkRequest<FindByStringId>
typealias KubernetesLicenseDeleteResponse = Unit

typealias KubernetesLicenseUpdateRequest = BulkRequest<KubernetesLicense>
typealias KubernetesLicenseUpdateResponse = Unit

@TSNamespace("compute.ucloud.licenses.maintenance")
class KubernetesLicenseMaintenance(providerId: String) : CallDescriptionContainer("compute.licenses.ucloud.maintenance") {
    val baseContext = LicenseProvider(providerId).baseContext + "/maintenance"

    val create = call<KubernetesLicenseCreateRequest, KubernetesLicenseCreateResponse, CommonErrorMessage>("create") {
        httpCreate(baseContext, roles = Roles.ADMIN)
    }

    val browse = call<KubernetesLicenseBrowseRequest, KubernetesLicenseBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext, roles = Roles.ADMIN)
    }

    val update = call<KubernetesLicenseUpdateRequest, KubernetesLicenseUpdateResponse, CommonErrorMessage>("update") {
        httpUpdate(baseContext, "update", roles = Roles.ADMIN)
    }
}
