package dk.sdu.cloud.accounting.services.providers

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.NormalizedPaginationRequestV2
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.provider.api.IntegrationBrowseResponseItem
import dk.sdu.cloud.provider.api.IntegrationConnectResponse
import dk.sdu.cloud.provider.api.IntegrationProvider
import dk.sdu.cloud.provider.api.IntegrationProviderConnectRequest
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.paginateV2
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*

class ProviderIntegrationService(
    private val db: DBContext,
    private val providers: ProviderService,
    private val serviceClient: AuthenticatedClient,
    private val devMode: Boolean = false,
) {
    suspend fun connect(
        actorAndProject: ActorAndProject,
        provider: String,
    ): IntegrationConnectResponse {
        // TODO This would ideally check if the user has been approved
        val providerSpec = providers.retrieveProvider(Actor.System, provider).specification
        val hostInfo = HostInfo(providerSpec.domain, if (providerSpec.https) "https" else "http", providerSpec.port)
        val auth = RefreshingJWTAuthenticator(
            serviceClient.client,
            JwtRefresher.ProviderOrchestrator(serviceClient, provider)
        )
        val httpClient = auth.authenticateClient(OutgoingHttpCall).withFixedHost(hostInfo)

        val integrationProvider = IntegrationProvider(provider)

        integrationProvider.retrieveManifest.call(Unit, httpClient).orNull()?.enabled?.takeIf { it }
            ?: throw RPCException("Connection is not supported by this provider", HttpStatusCode.BadRequest)

        val connection = integrationProvider.connect.call(
            IntegrationProviderConnectRequest(actorAndProject.actor.safeUsername()),
            httpClient
        ).orRethrowAs {
            val errorMessage = it.error?.why ?: it.statusCode.description
            throw RPCException("Connection with $provider has failed ($errorMessage)", HttpStatusCode.BadGateway)
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
                                    select w.product_provider
                                    from
                                        project.project_members pm join
                                        accounting.wallets w on
                                            pm.project_id = w.account_id and
                                            w.account_type = 'PROJECT'
                                    where username = :username
                                    union
                                    select w.product_provider
                                    from accounting.wallets w
                                    where w.account_id = :username and w.account_type = 'USER'
                                )
                            select
                                a.product_provider as provider_id, 
                                c.provider_id is not null as is_connected
                            from
                                available a left join
                                connected c on c.provider_id = a.product_provider
                            order by provider_id
                        """
                    )
            },
            mapper = { _, rows ->
                rows.map { row ->
                    IntegrationBrowseResponseItem(
                        row.getString(0)!!,
                        row.getBoolean(1)!!,
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
                        insert into provider.connected_with (username, provider_id) values
                        (:username, :provider_id) on conflict do nothing 
                    """
                )
        }
    }
}
