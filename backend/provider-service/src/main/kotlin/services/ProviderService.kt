package dk.sdu.cloud.provider.services

import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*

class ProviderService(
    private val db: AsyncDBSessionFactory,
    private val dao: ProviderDao,
) {
    suspend fun create(
        actor: Actor,
        project: String?,
        request: BulkRequest<ProviderSpecification>,
    ) {
        db.withSession { session ->
            request.items.forEach {
                dao.create(session, actor, project, it)
            }
        }

        // And now we insert the refresh tokens
        TODO()
    }

    suspend fun retrieveProvider(
        actor: Actor,
        id: String,
    ): Provider {
        return db.withSession { dao.retrieveProvider(it, actor, id) }
    }

    suspend fun browseProviders(
        actor: Actor,
        project: String?,
        pagination: NormalizedPaginationRequestV2
    ): PageV2<Provider> {
        return dao.browseProviders(db, actor, project, pagination)
    }

    suspend fun updateToken(
        actor: Actor,
        id: String,
    ) {
        db.withSession { session ->
            if (!dao.hasPermission(session, actor, id, ProviderAclPermission.EDIT)) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

            val newToken: String = TODO("Fetch token")
            dao.updateToken(session, actor, id, newToken)
        }
    }

    suspend fun updateAcl(
        actor: Actor,
        id: String,
        newAcl: List<ProviderAclEntry>,
    ) {
        dao.updateAcl(db, actor, id, newAcl)
    }

    suspend fun updateManifest(
        actor: Actor,
        id: String,
        newManifest: ProviderManifest,
    ) {
        dao.updateManifest(db, actor, id, newManifest)
    }
}
