package dk.sdu.cloud.accounting.services.providers

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.invokeCall
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.providerIdOrNull
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.atomic.AtomicReference


class ProviderIntegrationService(
    private val db: DBContext,
    private val providers: ProviderService,
    private val serviceClient: AuthenticatedClient,
    private val backgroundScope: BackgroundScope,
    private val devMode: Boolean = false,
) {
    // TODO(Dan): Unify all of this communication stuff. It is getting very confusing.
    private val providerCommunications = Providers(serviceClient)
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

            val manifest = integrationProvider.retrieveManifest.call(Unit, httpClient).orNull()
            val isEnabled = manifest?.enabled ?: false
            if (!isEnabled || manifest == null) return@SimpleCache null

            Communication(integrationProvider, httpClient, spec, manifest)
        }
    )

    private val knownProviders = AtomicReference<Set<String>>(emptySet())

    private val providerConditionCache = SimpleCache<String, ProviderCondition>(
        maxAge = 1000 * 60 * 5L,
    ) { providerId ->
        val (integrationProvider, httpClient, providerSpec)  = communicationCache.get(providerId) ?:
            throw RPCException("Connection is not supported by this provider", HttpStatusCode.BadRequest)

        val result = integrationProvider.retrieveCondition.call(
            Unit,
            httpClient
        ).orNull()

        (result ?: ProviderCondition()) as ProviderCondition
    }

    private data class Communication(
        val api: IntegrationProvider,
        val client: AuthenticatedClient,
        val spec: ProviderSpecification,
        val manifest: IntegrationProviderRetrieveManifestResponse,
    )

    fun init() {
        backgroundScope.launch {
            while (coroutineContext.isActive) {
                val providerIds = db.withSession { session ->
                    session.sendPreparedStatement(
                        {},
                        """
                            select resource
                            from provider.providers p
                        """
                    ).rows.map { it.getLong(0)!!.toString() }
                }

                for (provider in providerIds) {
                    runCatching {
                        communicationCache.get(provider)
                    }
                }

                knownProviders.set(providerIds.toSet())

                delay(1000 * 60 * 1)
            }
        }
    }

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
        data class Row(
            val providerId: String,
            val providerTitle: String,
            val isConnected: Boolean
        )

        val rows = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("next", request.next?.toLongOrNull())
                },
                """
                    with
                        connected as (
                            select provider_id
                            from provider.connected_with
                            where
                                username = :username and
                                (expires_at is null or expires_at >= now())
                        ),
                        available as(
                            select p.resource, p.unique_name
                            from
                                project.project_members pm join
                                accounting.wallet_owner wo on
                                    pm.project_id = wo.project_id and
                                    pm.username = :username join
                                accounting.wallets_v2 w on w.wallet_owner = wo.id join
                                accounting.allocation_groups ag on w.id = ag.associated_wallet join
                                accounting.product_categories pc on w.product_category = pc.id join
                                provider.providers p on pc.provider = p.unique_name
                            union
                            select p.resource, p.unique_name
                            from
                                accounting.wallet_owner wo join
                                accounting.wallets_v2 w on w.wallet_owner = wo.id join
                                accounting.allocation_groups ag on w.id = ag.associated_wallet join
                                accounting.product_categories pc on w.product_category = pc.id join
                                provider.providers p on pc.provider = p.unique_name
                            where
                                wo.username = :username
                            union
                            select p.resource, p.unique_name
                            from
                                connected c
                                join provider.providers p on p.resource = c.provider_id
                        )
                    select
                        a.resource as provider_id,
                        c.provider_id is not null as is_connected,
                        a.unique_name as provider_name
                    from
                        available a left join
                        connected c on c.provider_id = a.resource
                    where
                        (
                            :next::bigint is null or
                            a.resource > :next::bigint
                        )
                    order by provider_id
                """
            ).rows.map { Row(it.getLong(0)!!.toString(), it.getString(2)!!, it.getBoolean(1)!!) }
        }

        val itemsByProviderId = HashMap<String, IntegrationBrowseResponseItem>()

        rows.forEach { row ->
            try {
                val manifest = communicationCache.get(row.providerId)

                itemsByProviderId[row.providerId] = IntegrationBrowseResponseItem(
                    row.providerId,
                    when {
                        row.providerTitle == "ucloud" -> true // NOTE(Dan): Backwards compatible
                        row.providerTitle == "aau" -> true // NOTE(Dan): Backwards compatible
                        manifest != null && !manifest.manifest.enabled -> true
                        else -> row.isConnected
                    },
                    row.providerTitle,
                    manifest?.manifest?.requiresMessageSigning ?: false,
                    false, // NOTE(Dan): Send unmanaged connection only if we are taking the other branch
                )
            } catch (ignored: Throwable) {
                // Ignored
            }
        }

        for (provider in knownProviders.get()) {
            try {
                val manifest = communicationCache.get(provider) ?: continue
                if (manifest.manifest.unmanagedConnections && !itemsByProviderId.contains(provider)) {
                    itemsByProviderId[provider] = IntegrationBrowseResponseItem(
                        provider,
                        when {
                            manifest.spec.id == "ucloud" -> true // NOTE(Dan): Backwards compatible
                            manifest.spec.id == "aau" -> true // NOTE(Dan): Backwards compatible
                            manifest != null && !manifest.manifest.enabled -> true
                            else -> false
                        },
                        manifest.spec.id,
                        manifest.manifest.requiresMessageSigning,
                        manifest.manifest.unmanagedConnections,
                    )
                }
            } catch (ignored: Throwable) {
                // Ignored
            }
        }

        val items = itemsByProviderId.values.toList().sortedBy { it.providerTitle }
        return PageV2(250, items, null)
    }

    suspend fun retrieveCondition(
        providerId: String
    ): ProviderCondition {
        return providerConditionCache.get(providerId) ?: ProviderCondition()
    }

    suspend fun clearConnection(
        actorAndProject: ActorAndProject,
        requestedUser: String?,
        requestedProvider: String?,
    ) {
        val (actor) = actorAndProject

        val actorUsername = actor.safeUsername()
        val actorProviderId =
            if (actorUsername.startsWith(AuthProviders.PROVIDER_PREFIX)) {
                actorUsername.removePrefix(AuthProviders.PROVIDER_PREFIX)
            } else {
                null
            }

        val username = when {
            actor is Actor.System -> requestedUser ?: throw RPCException("Missing username", HttpStatusCode.BadRequest)
            actorProviderId != null -> requestedUser ?: throw RPCException("Missing username", HttpStatusCode.BadRequest)
            else -> actorUsername
        }

        val provider = when {
            actor is Actor.System -> requestedProvider ?: throw RPCException("Missing provider", HttpStatusCode.BadRequest)
            actorProviderId != null -> actorProviderId
            else -> requestedProvider ?: throw RPCException("Missing provider", HttpStatusCode.BadRequest)
        }

        val shouldNotifyProvider = actorProviderId == null && actor !is Actor.System

        db.withSession { session ->
            val success = session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                        setParameter("provider_id", provider)
                    },
                    """
                        delete from provider.connected_with cw
                        using provider.providers p
                        where
                            cw.username = :username and
                            p.unique_name = :provider_id and
                            cw.provider_id = p.resource
                    """
                )
                .rowsAffected > 0L

            if (shouldNotifyProvider && success) {
                try {
                    providerCommunications.invokeCall(
                        provider,
                        actorAndProject,
                        { IntegrationProvider(it.provider.id).unlinked },
                        IntegrationProviderUnlinkedRequest(username),
                        actorAndProject.signedIntentFromUser,
                        isUserRequest = false,
                    )
                } catch (ex: RPCException) {
                    if (ex.httpStatusCode.value in 500..599) {
                        throw RPCException(
                            "The provider does not appear to be responding at the moment. Try again later.",
                            HttpStatusCode.BadGateway
                        )
                    } else {
                        // This is OK
                    }
                }
            }
        }
    }

    suspend fun approveConnection(
        actor: Actor,
        username: String,
    ) {
        val providerId = actor.safeUsername().removePrefix(AuthProviders.PROVIDER_PREFIX)
        db.withSession { session ->
            val provider = providers.browse(
                ActorAndProject(Actor.System, null),
                ResourceBrowseRequest(ProviderIncludeFlags(filterName = providerId)),
                useProject = false,
                ctx = session
            ).items.singleOrNull() ?: return@withSession

            val comms = communicationCache.get(provider.id) ?: return@withSession
            val expireAfterMs = comms.manifest.expireAfterMs

            session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                        setParameter("provider_id", providerId)
                        setParameter("expires_after", expireAfterMs?.let { it / 1000})
                    },
                    """
                        insert into provider.connected_with (username, provider_id, expires_at)
                        select :username, p.resource, now() + (:expires_after::bigint || 's')::interval
                        from provider.providers p
                        where p.unique_name = :provider_id
                        on conflict (username, provider_id) do update set expires_at = excluded.expires_at 
                    """
                )
        }
    }

    suspend fun createReverseConnection(
        actorAndProject: ActorAndProject,
        request: IntegrationControl.ReverseConnection.Request,
        ctx: DBContext = db,
    ): IntegrationControl.ReverseConnection.Response {
        val providerId = actorAndProject.actor.providerIdOrNull
            ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val token = UUID.randomUUID().toString()
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("token", token)
                    setParameter("provider_id", providerId)
                },
                """
                    insert into provider.reverse_connections(token, provider_id) 
                    values (:token, :provider_id)
                """
            )
        }

        return IntegrationControl.ReverseConnection.Response(token)
    }

    suspend fun claimReverseConnection(
        actorAndProject: ActorAndProject,
        request: Integration.ClaimReverseConnection.Request,
        ctx: DBContext = db,
    ) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {},
                """
                    delete from provider.reverse_connections
                    where
                        now() - created_at > '12 hours'::interval
                """
            )

            val providerId = session.sendPreparedStatement(
                {
                    setParameter("token", request.token)
                },
                """
                    delete from provider.reverse_connections
                    where token = :token
                    returning provider_id
                """
            ).rows.singleOrNull()?.getString(0)

            if (providerId == null) {
                throw RPCException("Invalid or bad link supplied, try again.", HttpStatusCode.NotFound)
            }

            session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("provider_id", providerId)
                },
                """
                    with mapped_id as (
                        select p.resource as provider_id
                        from provider.providers p
                        where unique_name = :provider_id
                    )
                    insert into provider.connected_with(username, provider_id, expires_at) 
                    select :username, provider_id, null
                    from mapped_id
                """
            )

            val provider = providers.browse(
                ActorAndProject(Actor.System, null),
                ResourceBrowseRequest(ProviderIncludeFlags(filterName = providerId)),
                useProject = false,
                ctx = session
            ).items.singleOrNull() ?: return@withSession

            val comms = communicationCache.get(provider.id) ?: return@withSession
            comms.api.reverseConnectionClaimed.call(
                ReverseConnectionClaimedRequest(request.token, actorAndProject.actor.safeUsername()),
                comms.client
            ).orThrow()
        }
    }
}
