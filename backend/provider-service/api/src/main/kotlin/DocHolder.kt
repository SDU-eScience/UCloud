package dk.sdu.cloud.provider.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.httpCreate

@Deprecated("Used for documentation purposes only", level = DeprecationLevel.WARNING)
data class ResourceDoc(
    override val id: String,
    override val createdAt: Long,
    override val status: ResourceStatus,
    override val updates: List<ResourceUpdate>,
    override val product: ProductReference,
    override val billing: ResourceBilling,
    override val owner: ResourceOwner,
    override val acl: List<ResourceAclEntry<Nothing?>>?
) : Resource<Nothing?> {
    init {
        error("Used only for documentation. Do not instantiate.")
    }
}

@Deprecated("Used for documentation purposes only", level = DeprecationLevel.WARNING)
object ResourcesDoc : CallDescriptionContainer("doc.provider.resources") {
    val baseContext = "/doc/resources"

    val create = call<BulkRequest<ResourceDoc>, Unit, CommonErrorMessage>("create") {
        httpCreate(baseContext)
    }
}