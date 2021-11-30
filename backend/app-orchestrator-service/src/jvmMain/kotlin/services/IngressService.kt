package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductArea
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

class IngressService(
    db: AsyncDBSessionFactory,
    providers: Providers<ComputeCommunication>,
    support: ProviderSupport<ComputeCommunication, Product.Ingress, IngressSupport>,
    serviceClient: AuthenticatedClient,
    orchestrator: JobOrchestrator,
) : JobBoundResource<Ingress, IngressSpecification, IngressUpdate, IngressIncludeFlags, IngressStatus,
        Product.Ingress, IngressSupport, ComputeCommunication, AppParameterValue.Ingress>(db, providers, support, serviceClient, orchestrator) {
    override val table = SqlObject.Table("app_orchestrator.ingresses")
    override val sortColumns: Map<String, SqlObject.Column> = mapOf(
        "domain" to SqlObject.Column(table, "domain")
    )
    override val defaultSortColumn = SqlObject.Column(table, "domain")
    override val currentStateColumn = SqlObject.Column(table, "current_state")
    override val statusBoundToColumn = SqlObject.Column(table, "status_bound_to")
    override val productArea: ProductArea = ProductArea.INGRESS
    override val serializer: KSerializer<Ingress> = serializer()
    override val updateSerializer: KSerializer<IngressUpdate> = serializer()

    override fun boundUpdate(binding: JobBinding): IngressUpdate = IngressUpdate(binding = binding)

    override fun isReady(res: Ingress): Boolean = res.status.state == IngressState.READY
    override fun resourcesFromJob(job: Job): List<AppParameterValue.Ingress> = job.ingressPoints

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
            session
                .sendPreparedStatement(
                    {
                        setParameter("resource", id)
                        setParameter("domain", spec.domain)
                    },
                    """
                        insert into app_orchestrator.ingresses (domain, resource) values (:domain, :resource) 
                        on conflict (resource) do update set domain = excluded.domain
                    """
                )
        }
    }
    override suspend fun browseQuery(actorAndProject: ActorAndProject, flags: IngressIncludeFlags?, query: String?): PartialQuery {
        return PartialQuery(
            {
                setParameter("query", query)
                setParameter("filter_state", flags?.filterState?.name)
            },
            """
                select i.*
                from
                    accessible_resources resc join
                    app_orchestrator.ingresses i on (resc.r).id = resource
                where
                    (:query::text is null or domain ilike ('%' || :query || '%')) and
                    (:filter_state::text is null or :filter_state = current_state)
            """
        )
    }
}
