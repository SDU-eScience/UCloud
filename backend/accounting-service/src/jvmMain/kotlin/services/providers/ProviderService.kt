package dk.sdu.cloud.accounting.services.providers

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
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
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import java.util.*

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
        if (project == null) {
            throw RPCException(
                "A provider must belong to a project, please set the project context before creating a new provider",
                HttpStatusCode.BadRequest
            )
        }

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

        return db.withSession { session ->
            val claims = AuthProviders.claim.call(
                bulkRequestOf(
                    claimTokens.map { (_, tokenResp) -> AuthProvidersRegisterResponseItem(tokenResp.claimToken) }
                ),
                serviceClient
            ).orThrow()

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

    suspend fun requestApproval(
        actor: Actor,
        request: ProvidersRequestApprovalRequest
    ): ProvidersRequestApprovalResponse {
        return db.withSession { session ->
            when (request) {
                is ProvidersRequestApprovalRequest.Information -> {
                    val token = UUID.randomUUID().toString()
                    dao.requestApprovalInformation(session, token, request.specification)
                    ProvidersRequestApprovalResponse.RequiresSignature(token)
                }

                is ProvidersRequestApprovalRequest.Sign -> {
                    if (actor !is Actor.User) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                    if (!dao.requestApprovalSignature(session, request.token, actor)) {
                        throw RPCException("Bad token provided", HttpStatusCode.BadRequest)
                    }
                    ProvidersRequestApprovalResponse.AwaitingAdministratorApproval(request.token)
                }
            }
        }
    }

    suspend fun approveRequest(
        actor: Actor,
        request: ProvidersApproveRequest
    ): FindByStringId {
        val keypair = AuthProviders.generateKeyPair.call(
            Unit,
            serviceClient
        ).orRethrowAs { throw RPCException("Unable to generate a key pair", HttpStatusCode.InternalServerError) }

        try {
            return db.withSession { session ->
                val provider = session
                    .sendPreparedStatement(
                        {
                            setParameter("token", request.token)
                            setParameter("public", keypair.publicKey)
                            setParameter("private", keypair.privateKey)
                        },
                        """
                        select *
                        from provider.approve_request(:token, :public, :private)
                    """
                    )
                    .rows
                    .singleOrNull()
                    ?.let { dao.rowToProvider(it).provider }
                    ?: throw RPCException("Unable to approve request", HttpStatusCode.BadRequest)

                val ip = IntegrationProvider(provider.id)
                ip.welcome.call(
                    IntegrationProviderWelcomeRequest(
                        request.token,
                        provider
                    ),
                    serviceClient.withoutAuthentication().noAuth().withFixedHost(
                        HostInfo(
                            provider.specification.domain,
                            if (provider.specification.https) "https" else "http",
                            provider.specification.port
                        )
                    )
                ).orRethrowAs { throw RPCException("Could not connect to provider", HttpStatusCode.BadRequest) }

                FindByStringId(provider.id)
            }
        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == PostgresErrorCodes.RAISE_EXCEPTION) {
                throw RPCException(ex.errorMessage.message ?: "Unknown error", HttpStatusCode.BadRequest)
            }
            throw ex
        }
    }
}
