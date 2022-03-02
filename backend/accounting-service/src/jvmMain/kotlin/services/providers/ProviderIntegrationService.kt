package dk.sdu.cloud.accounting.services.providers

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.NormalizedPaginationRequestV2
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.paginateV2
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession

class ProviderIntegrationService(
    private val db: DBContext,
    private val providers: ProviderService,
    private val serviceClient: AuthenticatedClient,
    private val devMode: Boolean = false,
) {
    private val communicationCache = SimpleCache<String, Communication>(
        maxAge = 1000 * 60 * 60L,
        lookup = { providerResourceId ->
            val spec = providers.retrieve(
                ActorAndProject(Actor.System, null),
                providerResourceId,
                null
            ).specification

            val hostInfo = HostInfo(spec.domain, if (spec.https) "https" else "http", spec.port)
            val auth = RefreshingJWTAuthenticator(
                serviceClient.client,
                JwtRefresher.ProviderOrchestrator(serviceClient, spec.id)
            )

            val httpClient = auth.authenticateClient(OutgoingHttpCall).withFixedHost(hostInfo)
            val integrationProvider = IntegrationProvider(spec.id)

            val isEnabled = integrationProvider.retrieveManifest.call(Unit, httpClient).orNull()?.enabled ?: false
            if (!isEnabled) return@SimpleCache null

            Communication(integrationProvider, httpClient, spec)
        }
    )

    private data class Communication(
        val api: IntegrationProvider,
        val client: AuthenticatedClient,
        val spec: ProviderSpecification,
    )

    suspend fun connect(
        actorAndProject: ActorAndProject,
        provider: String,
    ): IntegrationConnectResponse {
        // TODO This would ideally check if the user has been approved
        val (integrationProvider, httpClient, providerSpec) = communicationCache.get(provider) ?:
            throw RPCException("Connection is not supported by this provider", HttpStatusCode.BadRequest)

        val connection = integrationProvider.connect.call(
            IntegrationProviderConnectRequest(actorAndProject.actor.safeUsername()),
            httpClient
        ).orRethrowAs {
            val errorMessage = it.error?.why ?: it.statusCode.description
            throw RPCException("Connection has failed ($errorMessage)", HttpStatusCode.BadGateway)
        }

        if (connection.redirectTo.startsWith("http://") || connection.redirectTo.startsWith("https://")) {
            return IntegrationConnectResponse(connection.redirectTo)
        } else {
            return IntegrationConnectResponse(
                buildString {
                    append("http")
                    if (providerSpec.https) append("s")
                    append("://")
                    if (providerSpec.domain == "integration-module" && devMode) {
                        append("localhost")
                    } else {
                        append(providerSpec.domain)
                    }
                    if (providerSpec.port != null) {
                        append(":")
                        append(providerSpec.port)
                    }
                    append("/")
                    append(connection.redirectTo.removePrefix("/"))
                }
            )
        }
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: NormalizedPaginationRequestV2
    ): PageV2<IntegrationBrowseResponseItem> {
        return db.paginateV2(
            actorAndProject.actor,
            request,
            create = { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("username", actorAndProject.actor.safeUsername())
                        },
                        """
                            declare c cursor for
                            with
                                connected as (
                                    select provider_id
                                    from provider.connected_with
                                    where username = :username
                                ),
                                available as(
                                    select p.resource, p.unique_name
                                    from
                                        project.project_members pm join
                                        accounting.wallet_owner wo on
                                            pm.project_id = wo.project_id and
                                            pm.username = :username join
                                        accounting.wallets w on w.owned_by = wo.id join
                                        accounting.product_categories pc on w.category = pc.id join
                                        provider.providers p on pc.provider = p.unique_name
                                    union
                                    select p.resource, p.unique_name
                                    from
                                        accounting.wallet_owner wo join
                                        accounting.wallets w on w.owned_by = wo.id join
                                        accounting.product_categories pc on w.category = pc.id join
                                        provider.providers p on pc.provider = p.unique_name
                                    where
                                        wo.username = :username
                                )
                            select
                                a.resource as provider_id,
                                c.provider_id is not null as is_connected,
                                a.unique_name as provider_name
                            from
                                available a left join
                                connected c on c.provider_id = a.resource
                            order by provider_id
                        """
                    )
            },
            mapper = { _, rows ->
                rows.mapNotNull { row ->
                    val connectionSupported = communicationCache.get(row.getLong(0)!!.toString()) != null

                    IntegrationBrowseResponseItem(
                        row.getLong(0)!!.toString(),
                        if (!connectionSupported) true else row.getBoolean(1)!!,
                        row.getString(2)!!,
                    )
                }
            }
        )
    }

    suspend fun clearConnection(
        username: String,
        providerId: String,
    ) {
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                        setParameter("provider_id", providerId)
                    },
                    """
                        delete from provider.connected_with
                        where
                            username = :username and
                            provider_id = :provider_id
                    """
                )
        }
    }

    suspend fun approveConnection(
        actor: Actor,
        username: String,
    ) {
        val providerId = actor.safeUsername().removePrefix(AuthProviders.PROVIDER_PREFIX)
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                        setParameter("provider_id", providerId)
                    },
                    """
                        insert into provider.connected_with (username, provider_id)
                        select :username, p.resource
                        from provider.providers p
                        where p.unique_name = :provider_id
                        on conflict do nothing 
                    """
                )
        }
    }
}
