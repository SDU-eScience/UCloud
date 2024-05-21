package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.WithStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.accounting.util.ProviderSupport
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.db.async.*

abstract class JobBoundResource<Res, Spec, Update, Flags, Status, Prod, Support, Comms, Val>(
    projectCache: ProjectCache,
    db: AsyncDBSessionFactory,
    providers: Providers<Comms>,
    support: ProviderSupport<Comms, Prod, Support>,
    serviceClient: AuthenticatedClient,
    protected val orchestrator: JobResourceService,
) : ResourceService<Res, Spec, Update, Flags, Status, Prod, Support, Comms>(projectCache, db, providers, support, serviceClient)
        where Res : Resource<Prod, Support>, Spec : ResourceSpecification, Update : JobBoundUpdate<*>,
              Flags : ResourceIncludeFlags, Status : JobBoundStatus<Prod, Support>, Prod : Product,
              Support : ProductSupport, Comms : ProviderComms, Val : WithStringId {
    protected abstract val currentStateColumn: SqlObject.Column
    protected abstract val statusBoundToColumn: SqlObject.Column

    protected abstract fun resourcesFromJob(job: JobSpecification): List<Val>
    protected abstract fun isReady(res: Res): Boolean
    protected abstract fun boundUpdate(binding: JobBinding): Update
    protected open fun bindsExclusively(): Boolean = true

    init {
        orchestrator.addListener(object : JobResourceService.JobListener {
            override suspend fun onVerified(actorAndProject: ActorAndProject, specification: JobSpecification) {
                val resources = resourcesFromJob(specification)
                if (resources.isEmpty()) return

                val computeProvider = specification.product.provider

                val allResources = retrieveBulk(
                    actorAndProject,
                    resources.map { it.id },
                    listOf(Permission.EDIT),
                    simpleFlags = SimpleResourceIncludeFlags(includeSupport = true)
                )

                for (resource in allResources) {
                    @Suppress("UNCHECKED_CAST")
                    if (!isReady(resource)) {
                        val productName = resource.specification.product.id
                        throw RPCException(
                            "$productName with ID '${resource.id}' is not ready. " +
                                "Try again later or with a different resource.",
                            HttpStatusCode.BadRequest
                        )
                    }
                    if (((resource.status as Status).boundTo.isNotEmpty() && bindsExclusively())) {
                        val productName = resource.specification.product.id
                        val jobIds = (resource.status as Status).boundTo.joinToString(", ")

                        throw RPCException(
                            "$productName with ID ${resource.id} is currently in use by a different job ($jobIds)",
                            HttpStatusCode.BadRequest
                        )
                    }

                    if (resource.owner.project != actorAndProject.project) {
                        val productName = resource.specification.product.id
                        throw RPCException(
                            "You are not allowed to use $productName with ID ${resource.id} in a different project.",
                            HttpStatusCode.BadRequest
                        )
                    }

                    if (resource.specification.product.provider != computeProvider) {
                        val productName = resource.specification.product.id
                        throw RPCException(
                            "You cannot use $productName with this machine. " +
                                    "You can try the same request with a different machine.",
                            HttpStatusCode.BadRequest
                        )
                    }
                }
            }

            override suspend fun onCreate(job: Job) {
                val resources = resourcesFromJob(job.specification)
                if (resources.isEmpty()) return

                val actorAndProject = ActorAndProject(Actor.System, job.owner.project)

                addUpdate(
                    actorAndProject,
                    BulkRequest(
                        resources.map { resource ->
                            ResourceUpdateAndId(
                                resource.id,
                                boundUpdate(JobBinding(JobBindKind.BIND, job.id))
                            )
                        }
                    )
                )
            }

            override suspend fun onTermination(job: Job) {
                val resources = resourcesFromJob(job.specification)
                if (resources.isEmpty()) return

                val actorAndProject = ActorAndProject(
                    Actor.SystemOnBehalfOfUser(job.owner.createdBy),
                    job.owner.project
                )

                addUpdate(
                    actorAndProject,
                    BulkRequest(
                        resources.map { resource ->
                            ResourceUpdateAndId(
                                resource.id,
                                boundUpdate(JobBinding(JobBindKind.UNBIND, job.id))
                            )
                        }
                    ),
                    requireAll = false,
                )
            }
        })
    }

    override suspend fun onUpdate(
        resources: List<Res>,
        updates: List<ResourceUpdateAndId<Update>>,
        session: AsyncDBConnection
    ) {
        val tableName = table.verify({ session })
        val currentStateColumn = currentStateColumn.verify({ session })
        val statusBoundToColumn = statusBoundToColumn.verify({ session })
        session
            .sendPreparedStatement(
                {
                    val ids by parameterList<Long>()
                    val bindMode by parameterList<String?>()
                    val binding by parameterList<Long?>()
                    val newState by parameterList<String?>()

                    for (update in updates) {
                        ids.add(update.id.toLong())
                        bindMode.add(update.update.binding?.kind?.name)
                        binding.add(update.update.binding?.job?.toLong())
                        newState.add(update.update.state?.name)
                    }
                },
                """
                    with new_updates as (
                        select
                            unnest(:ids::bigint[]) as id, 
                            unnest(:bind_mode::text[]) as bind_mode,
                            unnest(:binding::bigint[]) as binding,
                            unnest(:new_state::text[]) as new_state
                    )
                    update $tableName i
                    set
                        $currentStateColumn = coalesce(u.new_state, $currentStateColumn),
                        $statusBoundToColumn = case
                            when u.bind_mode = 'BIND' then $statusBoundToColumn || u.binding
                            when u.bind_mode = 'UNBIND' then array_remove($statusBoundToColumn, u.binding)
                            else $statusBoundToColumn
                        end
                    from new_updates u
                    where u.id = i.resource
                """,
                "${this::class.simpleName} on bound update"
            )
    }
}
