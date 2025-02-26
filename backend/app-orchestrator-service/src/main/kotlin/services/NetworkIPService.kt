package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductArea
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import org.joda.time.LocalDate
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

private typealias Super = JobBoundResource<NetworkIP, NetworkIPSpecification, NetworkIPUpdate, NetworkIPFlags,
        NetworkIPStatus, Product.NetworkIP, NetworkIPSupport, ComputeCommunication, AppParameterValue.Network>

class NetworkIPService(
    projectCache: ProjectCache,
    db: AsyncDBSessionFactory,
    providers: Providers<ComputeCommunication>,
    support: ProviderSupport<ComputeCommunication, Product.NetworkIP, NetworkIPSupport>,
    serviceClient: AuthenticatedClient,
    orchestrator: JobResourceService,
) : Super(projectCache, db, providers, support, serviceClient, orchestrator) {
    override val table = SqlObject.Table("app_orchestrator.network_ips")
    override val defaultSortColumn = SqlObject.Column(table, "ip_address")
    override val currentStateColumn = SqlObject.Column(table, "current_state")
    override val statusBoundToColumn = SqlObject.Column(table, "status_bound_to")
    override val sortColumns: Map<String, SqlObject.Column> = mapOf(
        "ip" to SqlObject.Column(table, "ip_address"),
        "resource" to SqlObject.Column(table, "resource")
    )

    override val serializer = NetworkIP.serializer()
    override val updateSerializer = NetworkIPUpdate.serializer()
    override val productArea: ProductArea = ProductArea.NETWORK_IP

    override fun resourcesFromJob(job: JobSpecification): List<AppParameterValue.Network> = job.networks
    override fun isReady(res: NetworkIP): Boolean = res.status.state == NetworkIPState.READY
    override fun boundUpdate(binding: JobBinding): NetworkIPUpdate = NetworkIPUpdate(binding = binding)

    override fun userApi() = NetworkIPs
    override fun controlApi() = NetworkIPControl
    override fun providerApi(comms: ProviderComms) = NetworkIPProvider(comms.provider.id)

    override suspend fun createSpecifications(
        actorAndProject: ActorAndProject,
        idWithSpec: List<Pair<Long, NetworkIPSpecification>>,
        session: AsyncDBConnection,
        allowDuplicates: Boolean
    ) {
        session
            .sendPreparedStatement(
                {
                    val ids by parameterList<Long>()
                    for ((id, spec) in idWithSpec) {
                        ids.add(id)
                    }
                },
                """
                    insert into app_orchestrator.network_ips (ip_address, resource)
                    select null, unnest(:ids::bigint[])
                    on conflict (resource) do nothing
                """
            )
    }

    suspend fun updateFirewall(actorAndProject: ActorAndProject, request: BulkRequest<FirewallAndId>) {
        proxy.bulkProxy(
            actorAndProject,
            request,
            object : BulkProxyInstructions<ComputeCommunication, NetworkIPSupport, NetworkIP,
                    FirewallAndId, BulkRequest<FirewallAndIP>, Unit>() {
                override val isUserRequest: Boolean = true
                override fun retrieveCall(comms: ComputeCommunication) = comms.networkApi.updateFirewall

                override suspend fun verifyAndFetchResources(
                    actorAndProject: ActorAndProject,
                    request: BulkRequest<FirewallAndId>
                ): List<RequestWithRefOrResource<FirewallAndId, NetworkIP>> {
                    return request.items.zip(
                        retrieveBulk(actorAndProject, request.items.map { it.id }, listOf(Permission.EDIT))
                            .map { ProductRefOrResource.SomeResource(it) }
                    )
                }

                override suspend fun verifyRequest(
                    request: FirewallAndId,
                    res: ProductRefOrResource<NetworkIP>,
                    support: NetworkIPSupport
                ) {
                    if (support.firewall.enabled != true) {
                        throw RPCException("Firewall updates are not supported", HttpStatusCode.BadRequest)
                    }
                }

                override suspend fun beforeCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<FirewallAndId, NetworkIP>>
                ): BulkRequest<FirewallAndIP> {
                    return BulkRequest(resources.map { (req, res) ->
                        FirewallAndIP(
                            (res as ProductRefOrResource.SomeResource<NetworkIP>).resource,
                            req.firewall
                        )
                    })
                }

                override suspend fun afterCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<FirewallAndId, NetworkIP>>,
                    response: BulkResponse<Unit?>
                ) {
                    super.afterCall(provider, resources, response)
                    db.withTransaction { session ->
                        session
                            .sendPreparedStatement(
                                {
                                    val ids by parameterList<Long?>()
                                    val firewalls by parameterList<String>()

                                    for ((id, firewall) in request.items) {
                                        ids.add(id.toLongOrNull())
                                        firewalls.add(defaultMapper.encodeToString(firewall))
                                    }
                                },
                                """
                                        with entries as (
                                            select unnest(:ids::bigint[]) id, unnest(:firewalls::jsonb[]) new_firewall
                                        )
                                        update app_orchestrator.network_ips ip
                                        set firewall = e.new_firewall
                                        from entries e
                                        where e.id = ip.resource
                                    """
                            )
                    }
                }
            }
        )
    }

    override suspend fun onUpdate(
        resources: List<NetworkIP>,
        updates: List<ResourceUpdateAndId<NetworkIPUpdate>>,
        session: AsyncDBConnection
    ) {
        super.onUpdate(resources, updates, session)

        session
            .sendPreparedStatement(
                {
                    val ids = ArrayList<Long?>()
                    val ipAddresses = ArrayList<String?>()
                    setParameter("ids", ids)
                    setParameter("ip_addresses", ipAddresses)

                    for ((id, update) in updates) {
                        if (update.changeIpAddress == true) {
                            ids.add(id.toLongOrNull())
                            ipAddresses.add(update.newIpAddress)
                        }
                    }
                },
                """
                    with entries as (
                        select unnest(:ids::bigint[]) id, unnest(:ip_addresses::text[]) ip
                    )
                    update app_orchestrator.network_ips ip
                    set ip_address = e.ip
                    from entries e
                    where e.id = ip.resource
                """
            )
    }

    override suspend fun browseQuery(actorAndProject: ActorAndProject, flags: NetworkIPFlags?, query: String?): PartialQuery {
        return PartialQuery(
            {
                setParameter("query", query)
                setParameter("filter_state", flags?.filterState?.name)
            },
            """
                select i.* 
                from
                    accessible_resources resc join
                    app_orchestrator.network_ips i on (resc.r).id = resource
                where
                    (:query::text is null or ip_address ilike '%' || :query || '%') and
                    (:filter_state::text is null or current_state = :filter_state)
            """
        )
    }

    private val cleanupMutex = Mutex()
    private val cleanupConfirmationCodes = HashMap<String, List<FindByStringId>>()

    suspend fun cleanup(
        actorAndProject: ActorAndProject,
        request: NetworkIPs.Cleanup.Request
    ): NetworkIPs.Cleanup.Response {
        if (actorAndProject != ActorAndProject.System) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val since = request.since
        val confirmationCode = request.confirmationCode
        if (since == null && confirmationCode == null) return NetworkIPs.Cleanup.Response(emptyList())

        if (confirmationCode == null) {
            val parsedSince = try {
                LocalDate.parse(request.since).toDate().time
            } catch (ex: Throwable) {
                throw RPCException("Unable to parse timestamp: ${ex.message}", HttpStatusCode.BadRequest)
            }

            val ids = db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("since", parsedSince)
                        setParameter("provider", request.provider)
                    },
                    """
                        with
                            from_input as (
                                select r.id, max(job_r.created_at) as created_at
                                from
                                    app_orchestrator.network_ips ip
                                    join provider.resource r on ip.resource = r.id
                                    join accounting.products p on r.product = p.id
                                    join accounting.product_categories pc on p.category = pc.id
                                    join app_orchestrator.job_input_parameters inp on 
                                        inp.value->>'type' = 'network' and inp.value->>'id' = r.id::text
                                    join provider.resource job_r on inp.job_id = job_r.id
                                    join app_orchestrator.jobs job on inp.job_id = job.resource
                                where
                                    pc.provider = :provider
                                group by r.id
                            ),
                            from_resource as (
                                select r.id, max(job_r.created_at) as created_at
                                from
                                    app_orchestrator.network_ips ip
                                    join provider.resource r on ip.resource = r.id
                                    join accounting.products p on r.product = p.id
                                    join accounting.product_categories pc on p.category = pc.id
                                    join app_orchestrator.job_resources inp on 
                                        inp.resource->>'type' = 'network' and inp.resource->>'id' = r.id::text
                                    join provider.resource job_r on inp.job_id = job_r.id
                                    join app_orchestrator.jobs job on inp.job_id = job.resource
                                where
                                    pc.provider = :provider
                                group by r.id
                            ),
                            combined as (
                                select * from from_input
                                union
                                select * from from_resource
                            )
                        select 
                            id
                        from 
                            combined 
                        where
                            created_at <= to_timestamp(:since / 1000.0)
                        group by
                            id
                        order by
                            max(created_at)
                    """
                ).rows.map { row ->
                    FindByStringId(row.getLong(0)!!.toString())
                }
            }

            if (ids.isEmpty()) {
                return NetworkIPs.Cleanup.Response(emptyList(), null, parsedSince)
            } else {
                val newCode = UUID.randomUUID().toString()
                cleanupMutex.withLock {
                    cleanupConfirmationCodes[newCode] = ids
                }
                return NetworkIPs.Cleanup.Response(ids, newCode, parsedSince)
            }
        } else {
            val toDelete = cleanupMutex.withLock {
                cleanupConfirmationCodes.remove(confirmationCode)
            }

            val removed = ArrayList<FindByStringId>()

            if (toDelete == null) {
                throw RPCException("Bad confirmation code supplied", HttpStatusCode.BadRequest)
            } else {
                val ips = retrieveBulk(ActorAndProject.System, toDelete.map { it.id }, listOf(Permission.READ),
                    requireAll = false).filter { it.status.boundTo.isEmpty() }

                for (ip in ips) {
                    val project = ip.owner.project
                    val proxyActor = if (project != null) {
                        val pid = AppOrchestratorServices.idCards.lookupPidFromProjectId(project)
                            ?: continue
                        val projectInfo = AppOrchestratorServices.idCards.lookupProjectInformation(pid)
                            ?: continue

                        ActorAndProject(Actor.SystemOnBehalfOfUser(projectInfo.pi), project)
                    } else {
                        ActorAndProject(Actor.SystemOnBehalfOfUser(ip.owner.createdBy), null)
                    }

                    try {
                        delete(proxyActor, bulkRequestOf(FindByStringId(ip.id)))
                        removed.add(FindByStringId(ip.id))
                    } catch (ex: Throwable) {
                        log.info("Failed to cleanup IP ${ip.id}: ${ex.toReadableStacktrace()}")
                    }
                }
            }

            return NetworkIPs.Cleanup.Response(removed)
        }
    }
}
