package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductArea
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import kotlinx.serialization.KSerializer

class IngressService(
    projectCache: ProjectCache,
    db: AsyncDBSessionFactory,
    providers: Providers<ComputeCommunication>,
    support: ProviderSupport<ComputeCommunication, Product.Ingress, IngressSupport>,
    serviceClient: AuthenticatedClient,
    orchestrator: JobResourceService,
) : JobBoundResource<Ingress, IngressSpecification, IngressUpdate, IngressIncludeFlags, IngressStatus,
        Product.Ingress, IngressSupport, ComputeCommunication, AppParameterValue.Ingress>(projectCache, db, providers, support, serviceClient, orchestrator) {
    override val table = SqlObject.Table("app_orchestrator.ingresses")
    override val sortColumns: Map<String, SqlObject.Column> = mapOf(
        "domain" to SqlObject.Column(table, "domain")
    )
    override val defaultSortColumn = SqlObject.Column(table, "domain")
    override val currentStateColumn = SqlObject.Column(table, "current_state")
    override val statusBoundToColumn = SqlObject.Column(table, "status_bound_to")
    override val productArea: ProductArea = ProductArea.INGRESS
    override val serializer: KSerializer<Ingress> = Ingress.serializer()
    override val updateSerializer: KSerializer<IngressUpdate> = IngressUpdate.serializer()
    override val browseStrategy: ResourceBrowseStrategy = ResourceBrowseStrategy.NEW

    override fun boundUpdate(binding: JobBinding): IngressUpdate = IngressUpdate(binding = binding)

    override fun isReady(res: Ingress): Boolean = res.status.state == IngressState.READY
    override fun resourcesFromJob(job: JobSpecification): List<AppParameterValue.Ingress> = job.ingressPoints

    override fun userApi() = Ingresses
    override fun controlApi() = IngressControl
    override fun providerApi(comms: ProviderComms): IngressProvider = IngressProvider(comms.provider.id)

    override suspend fun createSpecifications(
        actorAndProject: ActorAndProject,
        idWithSpec: List<Pair<Long, IngressSpecification>>,
        session: AsyncDBConnection,
        allowDuplicates: Boolean
    ) {
        for ((id, spec) in idWithSpec) {
            val prefix = support.retrieveProductSupport(spec.product).support.domainPrefix
            val suffix = support.retrieveProductSupport(spec.product).support.domainSuffix
            val userSpecificToken = spec.domain.substringAfter(prefix).substringBefore(suffix)
            if (userSpecificToken.contains(".") || userSpecificToken.contains(" ")) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Link cannot contain '.' or whitespaces")
            }
            session
                .sendPreparedStatement(
                    {
                        setParameter("resource", id)
                        setParameter("domain", spec.domain.lowercase())
                    },
                    """
                        insert into app_orchestrator.ingresses (domain, resource) values (:domain, :resource) 
                        on conflict (resource) do update set domain = excluded.domain
                    """,
                    "ingress spec create"
                )
        }
    }

    override suspend fun applyFilters(actor: Actor, query: String?, flags: IngressIncludeFlags?): PartialQuery {
        return PartialQuery(
            {
                if (flags?.filterState != null) setParameter("filter_state", flags.filterState?.name)
                if (query != null) setParameter("query", query)
            },
            buildString {
                appendLine("(")
                appendLine("  true")
                if (flags?.filterState != null) {
                    appendLine("  and spec.current_state = :filter_state")
                }
                if (query != null) {
                    appendLine("  and spec.domain ilike ('%' || :query || '%')")
                }
                appendLine(")")
            }
        )
    }

    override suspend fun browseQuery(actorAndProject: ActorAndProject, flags: IngressIncludeFlags?, query: String?): PartialQuery {
        return PartialQuery(
            {
                setParameter("query", query)
                setParameter("filter_state", flags?.filterState?.name)
            },
            """
                select (resc.spec).*
                from relevant_resources resc
            """
        )
    }
}
