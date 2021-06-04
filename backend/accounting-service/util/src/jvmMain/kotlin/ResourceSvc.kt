package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.db.async.DBContext

interface ResourceSvc<
    R : Resource<*>,
    F : ResourceIncludeFlags,
    Spec : ResourceSpecification,
    Update : ResourceUpdate,
    Prod : Product,
    Support : ProductSupport> {
    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: WithPaginationRequestV2,
        flags: F?,
        ctx: DBContext? = null,
    ): PageV2<R>

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
        flags: F?,
        ctx: DBContext? = null,
    ): R

    suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<Spec>,
    ): BulkResponse<FindByStringId?>

    suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdatedAcl>
    ): BulkResponse<Unit?>

    suspend fun delete(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ): BulkResponse<Unit?>

    suspend fun addUpdate(
        actorAndProject: ActorAndProject,
        updates: BulkRequest<ResourceUpdateAndId<Update>>
    )

    suspend fun register(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ProviderRegisteredResource<Spec>>
    ): BulkResponse<FindByStringId>

    suspend fun retrieveProducts(
        actorAndProject: ActorAndProject,
    ): SupportByProvider<Prod, Support>

    suspend fun chargeCredits(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ResourceChargeCredits>
    ): ResourceChargeCreditsResponse
}
