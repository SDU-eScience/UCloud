package dk.sdu.cloud.provider.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
@Deprecated("Used for documentation purposes only", level = DeprecationLevel.WARNING)
data class ResourceDoc(
    override val id: String,
    override val createdAt: Long,
    override val status: ResourceStatus,
    override val updates: List<ResourceUpdate>,
    override val specification: ResourceSpecification,
    override val billing: ResourceBilling,
    override val owner: ResourceOwner,
    override val acl: List<ResourceAclEntry<@Contextual Nothing?>>? = null,
    override val permissions: ResourcePermissions? = null
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

    val browse = call<PaginationRequestV2, PageV2<ResourceDoc>, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }
}
