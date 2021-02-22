package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.PaymentModel
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.throwError
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import java.util.*

class NetworkIPService(
    private val db: AsyncDBSessionFactory,
    private val dao: NetworkIPDao,
    private val providers: Providers,
    private val projectCache: ProjectCache,
    private val productCache: ProductCache,
    orchestrator: JobOrchestrator,
    private val paymentService: PaymentService,
    private val devMode: Boolean = false,
) {
    init {
        orchestrator.addListener(object : JobListener {
            override suspend fun onVerified(ctx: DBContext, job: Job) {
                val networks = job.networks
                if (networks.isEmpty()) return

                val computeProvider = job.specification.product.provider
                val jobProject = job.owner.project
                val jobLauncher = job.owner.createdBy

                ctx.withSession { session ->
                    networks.forEach { network ->
                        val errorMessage = "Cannot use your IP: ${network.id}. " +
                            "It does not exist or is not usable from your current project."
                        val retrievedNetwork = dao.retrieve(
                            session,
                            NetworkIPId(network.id),
                            NetworkIPDataIncludeFlags(includeProduct = true)
                        ) ?: throw RPCException(errorMessage, HttpStatusCode.BadRequest)
                        val product = retrievedNetwork.resolvedProduct!!

                        if (jobProject != retrievedNetwork.owner.project) {
                            throw RPCException(errorMessage, HttpStatusCode.BadRequest)
                        }

                        if (jobProject == null && jobLauncher != retrievedNetwork.owner.createdBy) {
                            throw RPCException(errorMessage, HttpStatusCode.BadRequest)
                        }

                        if (jobProject != null &&
                            projectCache.retrieveRole(jobLauncher, jobProject)?.isAdmin() != true
                        ) {
                            // We are not an admin. This means we must have an entry in the acl
                            val projectStatus = projectCache.retrieveProjectStatus(jobLauncher)
                            val groups = projectStatus.userStatus?.groups ?: emptyList()
                            val hasAccess = retrievedNetwork.acl!!.any { entry ->
                                val entity = entry.entity
                                if (!entry.permissions.any { it == NetworkIPPermission.USE }) return@any false
                                if (entity !is AclEntity.ProjectGroup) return@any false

                                groups.any { it.group == entity.group && it.project == entity.projectId }
                            }

                            if (!hasAccess) {
                                throw RPCException(
                                    "You do not have permissions to use this network IP. " +
                                        "Contact your PI to get access",
                                    HttpStatusCode.Forbidden
                                )
                            }
                        }

                        if (retrievedNetwork.specification.product.provider != computeProvider) {
                            throw RPCException(
                                "Cannot use network provided by " +
                                    "${retrievedNetwork.specification.product.provider} in job provided by $computeProvider",
                                HttpStatusCode.BadRequest
                            )
                        }

                        if (retrievedNetwork.status.state != NetworkIPState.READY) {
                            throw RPCException(
                                "Network ${retrievedNetwork.id} is not ready",
                                HttpStatusCode.BadRequest
                            )
                        }

                        if (retrievedNetwork.status.boundTo != null) {
                            throw RPCException(
                                "Network ${retrievedNetwork.id} is already in use",
                                HttpStatusCode.BadRequest
                            )
                        }

                        if (product.paymentModel == PaymentModel.FREE_BUT_REQUIRE_BALANCE) {
                            paymentService.creditCheck(
                                product,
                                job.owner.project ?: job.owner.createdBy,
                                if (job.owner.project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER
                            )
                        }
                    }
                }
            }

            override suspend fun onCreate(ctx: DBContext, job: Job) {
                ctx.withSession { session ->
                    job.networks.forEach { network ->
                        dao.insertUpdate(
                            session,
                            NetworkIPId(network.id),
                            NetworkIPUpdate(
                                Time.now(),
                                didBind = true,
                                newBinding = job.id
                            )
                        )
                    }
                }
            }

            override suspend fun onTermination(ctx: DBContext, job: Job) {
                ctx.withSession { session ->
                    job.networks.forEach { network ->
                        dao.insertUpdate(
                            session,
                            NetworkIPId(network.id),
                            NetworkIPUpdate(
                                Time.now(),
                                didBind = true,
                                newBinding = null
                            )
                        )
                    }
                }
            }
        })
    }

    suspend fun browse(
        actor: Actor,
        project: String?,
        pagination: NormalizedPaginationRequestV2,
        flags: NetworkIPDataIncludeFlags,
        filters: NetworkIPFilters,
    ): PageV2<NetworkIP> {
        if (project != null && projectCache.retrieveRole(actor.safeUsername(), project) == null) {
            throw RPCException("You are not a member of the supplied project", HttpStatusCode.Forbidden)
        }

        return dao.browse(db, actor, project, pagination, flags, filters)
    }

    suspend fun retrieve(
        actor: Actor,
        id: NetworkIPId,
        flags: NetworkIPDataIncludeFlags,
    ): NetworkIP {
        val notFoundMessage = "Permission denied or network does not exist"
        val result = dao.retrieve(db, id, flags) ?: throw RPCException(notFoundMessage, HttpStatusCode.NotFound)

        val (username, project) = result.owner
        if (actor is Actor.User &&
            (actor.principal.role == Role.PROVIDER || actor.principal.role == Role.SERVICE)) {
            providers.verifyProvider(result.specification.product.provider, actor)
        } else {
            if (project != null && projectCache.retrieveRole(actor.safeUsername(), project) == null) {
                log.debug("Actor is not a member of the project $actor $project")
                throw RPCException(notFoundMessage, HttpStatusCode.NotFound)
            }

            if (project == null && username != actor.safeUsername()) {
                log.debug("Actor is not owner of the personal workspace")
                throw RPCException(notFoundMessage, HttpStatusCode.NotFound)
            }
        }

        return result
    }

    suspend fun delete(
        actor: Actor,
        deletionRequest: BulkRequest<NetworkIPRetrieve>
    ) {
        val genericErrorMessage = "Not found or permission denied"

        db.withSession { session ->
            val ids = deletionRequest.items.map { it.id }
            val deletedItems = dao.delete(session, ids)
            val byProvider = deletedItems.groupBy { it.specification.product.provider }

            // Verify that the items were found
            if (ids.toSet().size != deletedItems.size) {
                throw RPCException(genericErrorMessage, HttpStatusCode.NotFound)
            }

            // Verify permissions before calling provider
            for (item in deletedItems) {
                if (actor != Actor.System) {
                    val project = item.owner.project
                    if (project != null && projectCache.retrieveRole(actor.safeUsername(), project) == null) {
                        throw RPCException(genericErrorMessage, HttpStatusCode.NotFound)
                    }

                    if (project == null && item.owner.createdBy != actor.safeUsername()) {
                        throw RPCException(genericErrorMessage, HttpStatusCode.NotFound)
                    }
                }

                if (item.status.boundTo != null) {
                    // TODO It would make sense to verify the ingress/job with the provider at this point
                    //  (in case something is stuck)
                    throw RPCException("Refusing to delete network which is in active use", HttpStatusCode.BadRequest)
                }
            }

            // TODO This could cause problems when a failure happens for a single provider
            // Verification will technically fix it up later, however.

            // All should be good now, time to call the providers
            for ((provider, network) in byProvider) {
                val comms = providers.prepareCommunication(provider)
                val api = comms.networkApi ?: continue // Provider no longer supports ingress. Silently skip.
                api.delete.call(bulkRequestOf(network), comms.client)
            }
        }
    }

    suspend fun create(
        actor: Actor,
        project: String?,
        request: BulkRequest<NetworkIPSpecification>
    ): List<String> {
        if (project != null && projectCache.retrieveRole(actor.safeUsername(), project) == null) {
            throw RPCException("You are not a member of the supplied project", HttpStatusCode.Forbidden)
        }

        return request.items.groupBy { it.product.provider }.flatMap { (provider, specs) ->
            val comms = providers.prepareCommunication(provider)
            val api = comms.networkApi
                ?: throw RPCException("Network is not supported by this provider: $provider", HttpStatusCode.BadRequest)

            // NOTE(Dan): It is important that this is performed in a single transaction to allow the provider to
            // immediately start calling us back about these resources, even before it has successfully created the
            // resource. This allows the provider to, for example, perform a charge on the resource before it has
            // been marked as 'created'.
            val network = db.withSession { session ->
                specs.map { spec ->
                    val product =
                        productCache.find<Product.NetworkIP>(
                            spec.product.provider,
                            spec.product.id,
                            spec.product.category
                        ) ?: throw RPCException("Invalid product", HttpStatusCode.BadRequest)

                    val id = UUID.randomUUID().toString()
                    val network = NetworkIP(
                        id,
                        NetworkIPSpecification(
                            spec.product,
                            spec.firewall,
                        ),
                        NetworkIPOwner(actor.safeUsername(), project),
                        Time.now(),
                        NetworkIPStatus(NetworkIPState.PREPARING),
                        NetworkIPBilling(product.pricePerUnit, 0L),
                        resolvedProduct = product
                    )

                    dao.create(session, network)
                    network
                }
            }

            val createResp = api.create.call(
                bulkRequestOf(network),
                comms.client
            )

            if (!createResp.statusCode.isSuccess()) {
                delete(Actor.System, bulkRequestOf(network.map { NetworkIPRetrieve(it.id) }))
                createResp.throwError()
            }

            network.map { it.id }
        }
    }

    suspend fun update(
        actor: Actor,
        request: NetworkIPControlUpdateRequest
    ) {
        db.withSession { session ->
            val now = Time.now()
            for ((id, requests) in request.items.groupBy { it.id }) {
                val network = dao.retrieve(session, NetworkIPId(id), NetworkIPDataIncludeFlags())
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

                providers.verifyProvider(network.specification.product.provider, actor)

                requests.forEach { request ->
                    dao.insertUpdate(
                        session,
                        NetworkIPId(id),
                        NetworkIPUpdate(
                            now,
                            request.state,
                            request.status,
                            request.clearBindingToJob == true,
                            newBinding = null,
                            changeIpAddress = request.changeIpAddress,
                            newIpAddress = request.newIpAddress,
                        ),
                    )
                }
            }
        }
    }

    suspend fun charge(
        actor: Actor,
        request: NetworkIPControlChargeCreditsRequest
    ): NetworkIPControlChargeCreditsResponse {
        val insufficient = ArrayList<NetworkIPId>()
        val duplicates = ArrayList<NetworkIPId>()

        db.withSession { session ->
            for ((id, requests) in request.items.groupBy { it.id }) {
                val network = dao.retrieve(session, NetworkIPId(id), NetworkIPDataIncludeFlags())
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

                providers.verifyProvider(network.specification.product.provider, actor)

                requests.forEach { request ->
                    val chargeResult = paymentService.charge(
                        Payment.OfNetworkIP(network, request.units, request.chargeId)
                    )

                    when (chargeResult) {
                        is PaymentService.ChargeResult.Charged -> {
                            dao.chargeCredits(session, NetworkIPId(id), chargeResult.amountCharged)
                        }

                        PaymentService.ChargeResult.InsufficientFunds -> {
                            insufficient.add(NetworkIPId(id))
                        }

                        PaymentService.ChargeResult.Duplicate -> {
                            duplicates.add(NetworkIPId(id))
                        }
                    }
                }
            }
        }

        return NetworkIPControlChargeCreditsResponse(insufficient, duplicates)
    }

    suspend fun updateAcl(
        actor: Actor,
        request: BulkRequest<NetworkIPsUpdateAclRequestItem>,
    ) {
        db.withSession { session ->
            request.items.forEach { update ->
                val network = dao.updateAcl(session, update, update.acl)
                checkWritePermission(actor, network)
            }
        }
    }

    suspend fun updateFirewall(
        actor: Actor,
        request: BulkRequest<FirewallAndId>,
    ) {
        db.withSession { session ->
            val networks = request.items.map { update ->
                val network = dao.updateFirewall(session, update, update.firewall)
                checkWritePermission(actor, network)

                network
            }

            networks.groupBy { it.specification.product.provider }.forEach { (provider, networks) ->
                val comms = providers.prepareCommunication(provider)
                comms.networkApi?.updateFirewall?.call(
                    bulkRequestOf(
                        networks.mapNotNull {
                            FirewallAndId(it.id, it.specification.firewall ?: return@mapNotNull null)
                        }
                    ),
                    comms.client
                )?.orThrow()
            }
        }
    }

    private suspend fun checkWritePermission(
        actor: Actor,
        network: NetworkIP,
    ) {
        if (actor != Actor.System) {
            val project = network.owner.project
            if (project != null && projectCache.retrieveRole(actor.safeUsername(), project) == null) {
                throw RPCException(genericErrorMessage, HttpStatusCode.NotFound)
            }

            if (project == null && network.owner.createdBy != actor.safeUsername()) {
                throw RPCException(genericErrorMessage, HttpStatusCode.NotFound)
            }
        }
    }

    private val genericErrorMessage = "Not found or permission denied"

    companion object : Loggable {
        override val log = logger()
    }
}
