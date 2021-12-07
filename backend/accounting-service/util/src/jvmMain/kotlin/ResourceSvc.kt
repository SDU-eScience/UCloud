package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.*

interface ResourceSvc<
    R : Resource<Prod, Support>,
    F : ResourceIncludeFlags,
    Spec : ResourceSpecification,
    Update : ResourceUpdate,
    Prod : Product,
    Support : ProductSupport> {
    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: ResourceBrowseRequest<F>,
        useProject: Boolean = true,
        ctx: DBContext? = null,
    ): PageV2<R>

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
        flags: F?,
        ctx: DBContext? = null,
        asProvider: Boolean = false,
    ): R

    suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<Spec>,
        ctx: DBContext? = null,
    ): BulkResponse<FindByStringId?> {
        throw RPCException("Operation not supported", HttpStatusCode.NotFound)
    }

    suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdatedAcl>
    ): BulkResponse<Unit?>

    suspend fun delete(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ): BulkResponse<Unit?> {
        throw RPCException("Operation not supported", HttpStatusCode.NotFound)
    }

    suspend fun addUpdate(
        actorAndProject: ActorAndProject,
        updates: BulkRequest<ResourceUpdateAndId<Update>>,
        requireAll: Boolean = true,
    ) {
        throw RPCException("Operation not supported", HttpStatusCode.NotFound)
    }

    suspend fun register(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ProviderRegisteredResource<Spec>>
    ): BulkResponse<FindByStringId> {
        throw RPCException("Operation not supported", HttpStatusCode.NotFound)
    }

    suspend fun retrieveProducts(
        actorAndProject: ActorAndProject,
    ): SupportByProvider<Prod, Support>

    suspend fun chargeCredits(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ResourceChargeCredits>,
        checkOnly: Boolean = false,
    ): ResourceChargeCreditsResponse {
        throw RPCException("Operation not supported", HttpStatusCode.NotFound)
    }

    suspend fun search(
        actorAndProject: ActorAndProject,
        request: ResourceSearchRequest<F>,
        ctx: DBContext? = null
    ): PageV2<R> {
        throw RPCException("Operation not supported", HttpStatusCode.NotFound)
    }
}
