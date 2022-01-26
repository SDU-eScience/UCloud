package dk.sdu.cloud.accounting.services.providers

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductArea
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.auth.api.AuthProvidersRenewRequestItem
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.provider.api.ProviderSupport
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.serializer
import java.util.*

private typealias Super = ResourceService<Provider, ProviderSpecification, ProviderUpdate, ProviderIncludeFlags,
        ProviderStatus, Product, ProviderSupport, ProviderComms>

class ProviderService(
    db: AsyncDBSessionFactory,
    providers: Providers<ProviderComms>,
    support: dk.sdu.cloud.accounting.util.ProviderSupport<ProviderComms, Product, ProviderSupport>,
    serviceClient: AuthenticatedClient,
) : Super(db, providers, support, serviceClient) {
    override val isCoreResource: Boolean = true
    override val table = SqlObject.Table("provider.providers")
    override val defaultSortColumn = SqlObject.Column(table, "resource")
    override val sortColumns = mapOf(
        "resource" to SqlObject.Column(table, "resource")
    )

    override val productArea: ProductArea = ProductArea.COMPUTE

    override val updateSerializer = serializer<ProviderUpdate>()
    override val serializer = serializer<Provider>()

    override fun userApi() = dk.sdu.cloud.provider.api.Providers
    override fun controlApi() = throw IllegalArgumentException("Not supported")
    override fun providerApi(comms: ProviderComms) = throw IllegalArgumentException("Not supported")

    override suspend fun createSpecifications(
        actorAndProject: ActorAndProject,
        idWithSpec: List<Pair<Long, ProviderSpecification>>,
        session: AsyncDBConnection,
        allowDuplicates: Boolean
    ) {
        val (actor) = actorAndProject
        if (actor != Actor.System && (actor !is Actor.User || actor.principal.role !in Roles.PRIVILEGED)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        for ((id, spec) in idWithSpec) {
            val info = requestApproval(
                actorAndProject.actor,
                ProvidersRequestApprovalRequest.Information(spec),
                session
            ) as ProvidersRequestApprovalResponse.RequiresSignature

            val adminApproval = requestApproval(
                actorAndProject.actor,
                ProvidersRequestApprovalRequest.Sign(info.token),
                session
            ) as ProvidersRequestApprovalResponse.AwaitingAdministratorApproval

            approveRequest(ProvidersApproveRequest(adminApproval.token), session, id)
        }
    }

    override suspend fun browseQuery(actorAndProject: ActorAndProject, flags: ProviderIncludeFlags?, query: String?): PartialQuery {
        return PartialQuery(
            {
                setParameter("query", query)
                setParameter("name", flags?.filterName)
            },
            """
                select p.*
                from
                    accessible_resources resc join
                    provider.providers p on (resc.r).id = resource
                where
                    (:name::text is null or unique_name = :name) and
                    (:query::text is null or unique_name ilike '%' || :query || '%')
            """
        )
    }

    suspend fun renewToken(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ProvidersRenewRefreshTokenRequestItem>
    ) {
        val providers = retrieveBulk(actorAndProject, request.items.map { it.id }, listOf(Permission.ADMIN))

        // If we crash here the user will need to click renew again!
        val renewedTokens = AuthProviders.renew.call(
            bulkRequestOf(providers.map { req -> AuthProvidersRenewRequestItem(req.specification.id) }),
            serviceClient
        ).orThrow()

        db.withSession { session ->
            renewedTokens.responses.zip(request.items).forEach { (resp, req) ->
                session.sendPreparedStatement(
                    {
                        setParameter("refresh_token", resp.refreshToken)
                        setParameter("public_key", resp.publicKey)
                        setParameter("id", req.id.toLongOrNull())
                    },
                    """
                        update provider.providers
                        set
                            refresh_token = :refresh_token,
                            public_key = :public_key
                        where resource = :id::bigint
                    """
                )
            }
        }
    }

    suspend fun retrieveSpecification(
        actorAndProject: ActorAndProject,
        name: String,
    ): ProviderSpecification {
        val (actor) = actorAndProject
        if (actor != Actor.System && (actor !is Actor.User || actor.principal.role !in Roles.PRIVILEGED)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        return browse(
            ActorAndProject(Actor.System, null),
            ResourceBrowseRequest(ProviderIncludeFlags(filterName = name)),
            useProject = false,
        ).items.singleOrNull()?.specification ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }

    private suspend fun registerRequest(
        ctx: DBContext,
        sharedSecret: String,
        specification: ProviderSpecification,
    ) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {},
                """
                    delete from provider.approval_request
                    where created_at < now() - '5 minutes'::interval
                """
            )
            session
                .sendPreparedStatement(
                    {
                        setParameter("shared_secret", sharedSecret)
                        setParameter("requested_id", specification.id)
                        setParameter("domain", specification.domain)
                        setParameter("https", specification.https)
                        setParameter("port", specification.port ?: if (specification.https) 443 else 80)
                    },
                    """
                        insert into provider.approval_request
                            (shared_secret, requested_id, domain, https, port)
                        values
                            (:shared_secret, :requested_id, :domain, :https, :port::int);
                    """,
                )
        }
    }

    private suspend fun signRequest(
        ctx: DBContext,
        sharedSecret: String,
        actor: Actor.User
    ): Boolean {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", actor.username)
                        setParameter("shared_secret", sharedSecret)
                    },
                    """
                        update provider.approval_request
                        set
                            signed_by = :username
                        where
                            shared_secret = :shared_secret and
                            signed_by is null and
                            created_at >= now() - '5 minutes'::interval
                    """,
                )
                .rowsAffected >= 1L
        }
    }

    suspend fun requestApproval(
        actor: Actor,
        request: ProvidersRequestApprovalRequest,
        ctx: DBContext = db,
    ): ProvidersRequestApprovalResponse {
        return ctx.withSession { session ->
            when (request) {
                is ProvidersRequestApprovalRequest.Information -> {
                    val token = UUID.randomUUID().toString()
                    registerRequest(session, token, request.specification)
                    ProvidersRequestApprovalResponse.RequiresSignature(token)
                }

                is ProvidersRequestApprovalRequest.Sign -> {
                    if (actor !is Actor.User) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                    if (!signRequest(session, request.token, actor)) {
                        throw RPCException("Bad token provided", HttpStatusCode.BadRequest)
                    }
                    ProvidersRequestApprovalResponse.AwaitingAdministratorApproval(request.token)
                }
            }
        }
    }

    suspend fun approveRequest(
        request: ProvidersApproveRequest,
        ctx: DBContext = db,
        predefinedResource: Long? = null,
    ): FindByStringId {
        val keypair = AuthProviders.generateKeyPair.call(
            Unit,
            serviceClient
        ).orRethrowAs { throw RPCException("Unable to generate a key pair", HttpStatusCode.InternalServerError) }

        try {
            return ctx.withSession { session ->
                data class WelcomeInfo(val name: String, val domain: String, val https: Boolean, val port: Int?,
                                       val refreshToken: String, val resource: Long)
                val provider = session
                    .sendPreparedStatement(
                        {
                            setParameter("token", request.token)
                            setParameter("public", keypair.publicKey)
                            setParameter("private", keypair.privateKey)
                            setParameter("predefined_resource", predefinedResource)
                        },
                        """
                            select unique_name, domain, https, port, refresh_token, resource
                            from provider.approve_request(:token, :public, :private, :predefined_resource)
                        """,
                    )
                    .rows
                    .singleOrNull()
                    ?.let { WelcomeInfo(it.getString(0)!!, it.getString(1)!!, it.getBoolean(2)!!, it.getInt(3),
                        it.getString(4)!!, it.getLong(5)!!) }
                    ?: throw RPCException("Unable to approve request", HttpStatusCode.BadRequest)

                if (predefinedResource == null) {
                    val ip = IntegrationProvider(provider.name)
                    ip.welcome.call(
                        IntegrationProviderWelcomeRequest(
                            request.token,
                            ProviderWelcomeTokens(provider.refreshToken, keypair.publicKey)
                        ),
                        serviceClient.withoutAuthentication().noAuth().withFixedHost(
                            HostInfo(
                                provider.domain,
                                if (provider.https) "https" else "http",
                                provider.port
                            )
                        )
                    )//.orRethrowAs { throw RPCException("Could not connect to provider", HttpStatusCode.BadRequest) }
                }

                FindByStringId(provider.resource.toString())
            }
        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == PostgresErrorCodes.RAISE_EXCEPTION) {
                throw RPCException(ex.errorMessage.message ?: "Unknown error", HttpStatusCode.BadRequest)
            }
            throw ex
        }
    }
}
