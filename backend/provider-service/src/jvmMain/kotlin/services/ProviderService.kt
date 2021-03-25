package dk.sdu.cloud.provider.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.auth.api.AuthProvidersRegisterRequestItem
import dk.sdu.cloud.auth.api.AuthProvidersRegisterResponseItem
import dk.sdu.cloud.auth.api.AuthProvidersRenewRequestItem
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*

class ProviderService(
    private val db: AsyncDBSessionFactory,
    private val dao: ProviderDao,
    private val serviceClient: AuthenticatedClient,
) {
    suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ProviderSpecification>,
    ): BulkResponse<FindByStringId> {
        val (actor, project) = actorAndProject
        val claimTokens = db.withSession { session ->
            val tokens = AuthProviders.register.call(
                bulkRequestOf(request.items.map { AuthProvidersRegisterRequestItem(it.id) }),
                serviceClient
            ).orThrow()

            val specsToTokens = request.items.zip(tokens.responses)
            specsToTokens.forEach { (spec, createResp) ->
                dao.create(session, actor, project, spec, createResp.claimToken)
            }
            specsToTokens
        }

        val claims = AuthProviders.claim.call(
            bulkRequestOf(
                claimTokens.map { (_, tokenResp) -> AuthProvidersRegisterResponseItem(tokenResp.claimToken) }
            ),
            serviceClient
        ).orThrow()

        return db.withSession { session ->
            claims.responses.forEach {
                dao.updateToken(session, actor, it.providerId, it.refreshToken, it.publicKey)
            }

            BulkResponse(claims.responses.map { FindByStringId(it.providerId) })
        }
    }

    suspend fun retrieveProvider(
        actor: Actor,
        id: String,
    ): Provider {
        return db.withSession { dao.retrieveProvider(it, actor, id) }
    }

    suspend fun browseProviders(
        actorAndProject: ActorAndProject,
        pagination: NormalizedPaginationRequestV2,
    ): PageV2<Provider> {
        val (actor, project) = actorAndProject
        return dao.browseProviders(db, actor, project, pagination)
    }

    suspend fun renewToken(
        actor: Actor,
        request: BulkRequest<ProvidersRenewRefreshTokenRequestItem>,
    ) {
        db.withSession { session ->
            request.items.forEach { req ->
                if (!dao.hasPermission(session, actor, req.id, ProviderAclPermission.EDIT)) {
                    throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                }
            }

            // If we crash here the user will need to click renew again!
            val renewedTokens = AuthProviders.renew.call(
                bulkRequestOf(request.items.map { req -> AuthProvidersRenewRequestItem(req.id) }),
                serviceClient
            ).orThrow()

            renewedTokens.responses.forEach { resp ->
                dao.updateToken(session, actor, resp.providerId, resp.refreshToken, resp.publicKey)
            }
        }
    }

    suspend fun updateAcl(
        actor: Actor,
        request: BulkRequest<ProvidersUpdateAclRequestItem>,
    ) {
        db.withSession { session ->
            request.items.forEach { req ->
                dao.updateAcl(session, actor, req.id, req.acl)
            }
        }
    }

    suspend fun claimTheUnclaimed() {
        db.withSession { session ->
            val items = dao.findUnclaimed(session).mapNotNull {
                AuthProvidersRegisterResponseItem(it.claimToken ?: return@mapNotNull null)
            }

            if (items.isNotEmpty()) {
                val claimed = AuthProviders.claim.call(
                    bulkRequestOf(
                        items
                    ),
                    serviceClient
                ).orThrow()

                claimed.responses.forEach {
                    dao.updateToken(session, Actor.System, it.providerId, it.refreshToken, it.publicKey)
                }
            }
        }
    }
}
