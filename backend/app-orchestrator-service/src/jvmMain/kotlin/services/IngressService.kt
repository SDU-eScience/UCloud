package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.provider.api.SimpleResourceIncludeFlags
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.collections.ArrayList

class IngressService(
    db: AsyncDBSessionFactory,
    providers: Providers<ComputeCommunication>,
    support: ProviderSupport<ComputeCommunication, Product.Ingress, IngressSettings>,
    serviceClient: AuthenticatedClient,
    private val orchestrator: JobOrchestrator,
) : ResourceService<Ingress, IngressSpecification, IngressUpdate, IngressIncludeFlags, IngressStatus,
    Product.Ingress, IngressSettings, ComputeCommunication>(db, providers, support, serviceClient) {
    override val table = SqlObject.Table("app_orchestrator.ingresses")
    override val sortColumn  = SqlObject.Column(table, "domain")
    override val serializer: KSerializer<Ingress> = serializer()
    override val productArea: ProductArea = ProductArea.INGRESS
    override val updateSerializer: KSerializer<IngressUpdate> = serializer()

    init {
        orchestrator.addListener(object : JobListener {
            override suspend fun onVerified(ctx: DBContext, job: Job) {
                val ingressPoints = job.ingressPoints
                if (ingressPoints.isEmpty()) return

                val computeProvider = job.specification.product.provider
                val jobProject = job.owner.project
                val jobLauncher = job.owner.createdBy

                val actorAndProject = ActorAndProject(
                    Actor.SystemOnBehalfOfUser(job.owner.createdBy),
                    job.owner.project
                )

                val allIngresses = retrieveBulk(
                    actorAndProject,
                    ingressPoints.map { it.id },
                    null,
                    Permission.Edit,
                    simpleFlags = SimpleResourceIncludeFlags(includeSupport = true)
                )

                for (ingress in allIngresses) {
                    if (ingress.status.state != IngressState.READY || ingress.status.boundTo != null) {
                        throw RPCException(
                            "Not all public links are ready",
                            HttpStatusCode.BadRequest
                        )
                    }

                    if (ingress.owner.project != jobProject ||
                        ingress.specification.product.provider != computeProvider
                    ) {
                        throw RPCException(
                            "Not all public links can be used in this application",
                            HttpStatusCode.BadRequest
                        )
                    }

                    val product = ingress.status.support!!.product as Product.Ingress
                    if (product.paymentModel == PaymentModel.FREE_BUT_REQUIRE_BALANCE) {
                        payment.creditCheck(
                            product,
                            if (jobProject != null) jobProject else jobLauncher,
                            if (jobProject != null) WalletOwnerType.PROJECT else WalletOwnerType.USER
                        )
                    }
                }
            }

            override suspend fun onCreate(ctx: DBContext, job: Job) {
                if (job.ingressPoints.isEmpty()) return

                val actorAndProject = ActorAndProject(
                    Actor.SystemOnBehalfOfUser(job.owner.createdBy),
                    job.owner.project
                )

                addUpdate(
                    actorAndProject,
                    BulkRequest(
                        job.ingressPoints.map { ingress ->
                            ResourceUpdateAndId(
                                ingress.id,
                                IngressUpdate(didBind = true, newBinding = job.id)
                            )
                        }
                    )
                )
            }

            override suspend fun onTermination(ctx: DBContext, job: Job) {
                if (job.ingressPoints.isEmpty()) return

                val actorAndProject = ActorAndProject(
                    Actor.SystemOnBehalfOfUser(job.owner.createdBy),
                    job.owner.project
                )

                addUpdate(
                    actorAndProject,
                    BulkRequest(
                        job.ingressPoints.map { ingress ->
                            ResourceUpdateAndId(
                                ingress.id,
                                IngressUpdate(didBind = true, newBinding = null)
                            )
                        }
                    )
                )
            }
        })
    }

    override fun userApi() = Ingresses
    override fun controlApi() = IngressControl
    override fun providerApi(comms: ProviderComms): IngressProvider = IngressProvider(comms.provider.id)

    override suspend fun createSpecifications(
        idWithSpec: List<Pair<Long, IngressSpecification>>,
        session: AsyncDBConnection,
        allowConflicts: Boolean
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

    override suspend fun onUpdate(
        resources: List<Ingress>,
        updates: List<ResourceUpdateAndId<IngressUpdate>>,
        session: AsyncDBConnection
    ) {
        session
            .sendPreparedStatement(
                {
                    val ids = ArrayList<Long>()
                    val didBind = ArrayList<Boolean>()
                    val newBinding = ArrayList<String?>()
                    val newState = ArrayList<String?>()
                    for (update in updates) {
                        ids.add(update.id.toLong())
                        didBind.add(update.update.didBind)
                        newBinding.add(update.update.newBinding)
                        newState.add(update.update.state?.name)
                    }
                    setParameter("ids", ids)
                    setParameter("did_bind", didBind)
                    setParameter("new_binding", newBinding)
                    setParameter("new_state", newState)
                },
                """
                    with new_updates as (
                        select
                            unnest(:ids::bigint[]) as id, 
                            unnest(:did_bind::boolean[]) as did_bind,
                            unnest(:new_binding::text[]) as new_binding,
                            unnest(:new_state::text[]) as new_state
                    )
                    update app_orchestrator.ingresses i
                    set
                        current_state = coalesce(u.new_state, current_state),
                        status_bound_to = case
                            when u.did_bind = true then u.new_binding
                            else status_bound_to
                        end
                    from new_updates u
                    where u.id = i.resource
                """
            )
    }

    override suspend fun deleteSpecification(
        resourceIds: List<Long>,
        resources: List<Ingress>,
        session: AsyncDBConnection
    ) {
        if (resources.any { it.status.boundTo != null }) {
            throw RPCException(
                "One of your public links are currently in use and cannot be deleted",
                HttpStatusCode.BadRequest
            )
        }

        super.deleteSpecification(resourceIds, resources, session)
    }

    override suspend fun browseQuery(
        flags: IngressIncludeFlags?,
    ): PartialQuery {
        return PartialQuery(
            {
                setParameter("filter_state", flags?.filterState?.name)
            },
            """
                select *
                from app_orchestrator.ingresses
                where :filter_state::text is null or :filter_state = current_state
            """
        )
    }

    override fun searchQuery(query: String, flags: IngressIncludeFlags?): PartialQuery {
        return PartialQuery(
            {
                setParameter("query", query)
                setParameter("filter_state", flags?.filterState?.name)
            },
            """
                select *
                from app_orchestrator.ingresses
                where
                    domain ilike ('%' || :query || '%') and
                    (:filter_state::text is null or :filter_state = current_state)
            """
        )
    }
}
