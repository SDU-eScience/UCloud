package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.provider.api.UpdatedAcl
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.util.*
import kotlin.collections.ArrayList

class IngressService(
    db: AsyncDBSessionFactory,
    providers: Providers<ComputeCommunication>,
    support: ProviderSupport<ComputeCommunication, Product.Ingress, IngressSettings>,
    serviceClient: AuthenticatedClient,
    private val orchestrator: JobOrchestrator,
) : ResourceService<Ingress, IngressSpecification, IngressUpdate, IngressIncludeFlags, IngressStatus,
    Product.Ingress, IngressSettings, ComputeCommunication>(db, providers, support, serviceClient) {
    override val table: String = "app_orchestrator.ingresses"
    override val sortColumn: String = "domain"
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
                    includeSupport = true
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

    override fun providerApi(comms: ProviderComms): IngressProvider = IngressProvider(comms.provider.id)

    override suspend fun createSpecification(
        resourceId: Long,
        specification: IngressSpecification,
        session: AsyncDBConnection,
        allowConflicts: Boolean
    ) {
        session
            .sendPreparedStatement(
                {
                    setParameter("resource", resourceId)
                    setParameter("domain", specification.domain)
                },
                """
                    insert into app_orchestrator.ingresses (domain, resource) values (:domain, :resource) 
                    on conflict (resource) do update set domain = excluded.domain
                """
            )
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
                },
                """
                    with new_updates as (
                        select
                            unnest(:ids) as id, 
                            unnest(:did_bind) as did_bind,
                            unnest(:new_binding) as new_binding,
                            unnest(:new_state) as new_state
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


    // TODO We must verify that delete isn't being done on a bound resource

    // TODO Old note about create
    // NOTE(Dan): It is important that this is performed in a single transaction to allow the provider to
    // immediately start calling us back about these resources, even before it has successfully created the
    // resource. This allows the provider to, for example, perform a charge on the resource before it has
    // been marked as 'created'.
}
