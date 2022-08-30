package dk.sdu.cloud.app.kubernetes.api

import dk.sdu.cloud.*
import dk.sdu.cloud.app.orchestrator.api.NetworkIPProvider
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class K8NetworkStatus(val capacity: Long, val used: Long)

@Serializable
data class K8Subnet(val externalCidr: String, val internalCidr: String)

typealias KubernetesIPMaintenanceCreateRequest = BulkRequest<K8Subnet>
typealias KubernetesIPMaintenanceCreateResponse = Unit

@Serializable
data class KubernetesIPMaintenanceBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2
typealias KubernetesIPMaintenanceBrowseResponse = PageV2<K8Subnet>

typealias KubernetesIPMaintenanceRetrieveStatusRequest = Unit
typealias KubernetesIPMaintenanceRetrieveStatusResponse = K8NetworkStatus

@TSNamespace("compute.ucloud.networkip.maintenance")
class KubernetesNetworkIPMaintenance(providerId: String) : CallDescriptionContainer("compute.networkip.ucloud.maintenance") {
    val baseContext = NetworkIPProvider(providerId).baseContext + "/maintenance"

    val create = call("create", BulkRequest.serializer(K8Subnet.serializer()), KubernetesIPMaintenanceCreateResponse.serializer(), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, roles = Roles.PRIVILEGED)
    }

    val browse = call("browse", KubernetesIPMaintenanceBrowseRequest.serializer(), PageV2.serializer(K8Subnet.serializer()), CommonErrorMessage.serializer()) {
        httpBrowse(baseContext, roles = Roles.PRIVILEGED)
    }

    val retrieveStatus = call("retrieveStatus", KubernetesIPMaintenanceRetrieveStatusRequest.serializer(), KubernetesIPMaintenanceRetrieveStatusResponse.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, roles = Roles.PRIVILEGED)
    }
}
