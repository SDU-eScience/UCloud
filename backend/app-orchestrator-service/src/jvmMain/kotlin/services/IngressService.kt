package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.PaymentModel
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.throwError
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import java.util.*

class IngressService(
    private val db: AsyncDBSessionFactory,
    private val dao: IngressDao,
    private val providers: Providers,
    private val projectCache: ProjectCache,
    private val productCache: ProductCache,
    orchestrator: JobOrchestrator,
    private val paymentService: PaymentService,
) {
    init {
        orchestrator.addListener(object : JobListener {
            override suspend fun onVerified(ctx: DBContext, job: Job) {
                val ingressPoints = job.ingressPoints
                if (ingressPoints.isEmpty()) return

                val computeProvider = job.specification.product.provider
                val jobProject = job.owner.project
                val jobLauncher = job.owner.launchedBy

                ctx.withSession { session ->
                    ingressPoints.forEach { ingress ->
                        val errorMessage = "Cannot use your public link: ${ingress.id}. " +
                            "It does not exist or is not usable from your current project."
                        val retrievedIngress = dao.retrieve(
                            session,
                            IngressId(ingress.id),
                            IngressDataIncludeFlags(includeProduct = true)
                        ) ?: throw RPCException(errorMessage, HttpStatusCode.BadRequest)
                        val product = retrievedIngress.resolvedProduct!!

                        if (jobProject != retrievedIngress.owner.project) {
                            throw RPCException(errorMessage, HttpStatusCode.BadRequest)
                        }

                        if (jobProject == null && jobLauncher != retrievedIngress.owner.createdBy) {
                            throw RPCException(errorMessage, HttpStatusCode.BadRequest)
                        }

                        if (jobProject != null && projectCache.retrieveRole(jobLauncher, jobProject) == null) {
                            throw RPCException(errorMessage, HttpStatusCode.BadRequest)
                        }

                        if (retrievedIngress.specification.product.provider != computeProvider) {
                            throw RPCException(
                                "Cannot use ingress provided by " +
                                    "${retrievedIngress.specification.product.provider} in job provided by $computeProvider",
                                HttpStatusCode.BadRequest
                            )
                        }

                        if (retrievedIngress.status.state != IngressState.READY) {
                            throw RPCException(
                                "Ingress ${retrievedIngress.specification.domain} is not ready",
                                HttpStatusCode.BadRequest
                            )
                        }

                        if (retrievedIngress.status.boundTo != null) {
                            throw RPCException(
                                "Ingress ${retrievedIngress.specification.domain} is already in use",
                                HttpStatusCode.BadRequest
                            )
                        }

                        if (product.paymentModel == PaymentModel.FREE_BUT_REQUIRE_BALANCE) {
                            paymentService.creditCheck(
                                product,
                                job.owner.project ?: job.owner.launchedBy,
                                if (job.owner.project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER
                            )
                        }
                    }
                }
            }

            override suspend fun onCreate(ctx: DBContext, job: Job) {
                ctx.withSession { session ->
                    job.ingressPoints.forEach { ingress ->
                        dao.insertUpdate(
                            session,
                            IngressId(ingress.id),
                            IngressUpdate(
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
                    job.ingressPoints.forEach { ingress ->
                        dao.insertUpdate(
                            session,
                            IngressId(ingress.id),
                            IngressUpdate(
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
        flags: IngressDataIncludeFlags,
        filters: IngressFilters,
    ): PageV2<Ingress> {
        if (project != null && projectCache.retrieveRole(actor.safeUsername(), project) == null) {
            throw RPCException("You are not a member of the supplied project", HttpStatusCode.Forbidden)
        }

        return dao.browse(db, actor, project, pagination, flags, filters)
    }

    suspend fun retrieve(
        actor: Actor,
        id: IngressId,
        flags: IngressDataIncludeFlags,
    ): Ingress {
        val notFoundMessage = "Permission denied or ingress does not exist"
        val result = dao.retrieve(db, id, flags) ?: throw RPCException(notFoundMessage, HttpStatusCode.NotFound)

        val (username, project) = result.owner
        if (actor is Actor.User && actor.principal.role == Role.PROVIDER) {
            providers.verifyProvider(result.specification.product.provider, actor)
        } else {
            if (project != null && projectCache.retrieveRole(actor.safeUsername(), project) == null) {
                throw RPCException(notFoundMessage, HttpStatusCode.NotFound)
            }

            if (project == null && username != actor.safeUsername()) {
                throw RPCException(notFoundMessage, HttpStatusCode.NotFound)
            }
        }

        return result
    }

    suspend fun delete(
        actor: Actor,
        deletionRequest: BulkRequest<IngressRetrieve>
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
                    throw RPCException("Refusing to delete ingress which is in active use", HttpStatusCode.BadRequest)
                }
            }

            // TODO This could cause problems when a failure happens for a single provider
            // Verification will technically fix it up later, however.

            // All should be good now, time to call the providers
            for ((provider, ingress) in byProvider) {
                val comms = providers.prepareCommunication(provider)
                val api = comms.ingressApi ?: continue // Provider no longer supports ingress. Silently skip.
                api.delete.call(bulkRequestOf(ingress), comms.client)
            }
        }
    }

    suspend fun create(
        actor: Actor,
        project: String?,
        request: BulkRequest<IngressSpecification>
    ): List<String> {
        if (project != null && projectCache.retrieveRole(actor.safeUsername(), project) == null) {
            throw RPCException("You are not a member of the supplied project", HttpStatusCode.Forbidden)
        }

        return request.items.groupBy { it.product.provider }.flatMap { (provider, specs) ->
            val comms = providers.prepareCommunication(provider)
            val api = comms.ingressApi
                ?: throw RPCException("Ingress is not supported by this provider: $provider", HttpStatusCode.BadRequest)

            val settingsByProduct = specs.groupBy { it.product }.map { (product, _) ->
                product to api.retrieveSettings
                    .call(product, comms.client)
                    .orRethrowAs {
                        if (it.statusCode == HttpStatusCode.NotFound) {
                            throw RPCException("Invalid product", HttpStatusCode.NotFound)
                        } else {
                            throw RPCException(it.error?.why ?: it.statusCode.description, it.statusCode)
                        }
                    }
            }.toMap()

            // NOTE(Dan): It is important that this is performed in a single transaction to allow the provider to
            // immediately start calling us back about these resources, even before it has successfully created the
            // resource. This allows the provider to, for example, perform a charge on the resource before it has
            // been marked as 'created'.
            val ingress = db.withSession { session ->
                specs.map { spec ->
                    val product =
                        productCache.find<Product.Ingress>(
                            spec.product.provider,
                            spec.product.id,
                            spec.product.category
                        ) ?: throw RPCException("Invalid product", HttpStatusCode.BadRequest)

                    val settings = settingsByProduct[spec.product]
                        ?: throw RPCException("Invalid product", HttpStatusCode.BadRequest)

                    val isValid = spec.domain.startsWith(settings.domainPrefix) &&
                        spec.domain.endsWith(settings.domainSuffix)

                    if (!isValid) {
                        throw RPCException(
                            "Invalid ingress supplied. Example: " +
                                "${settings.domainPrefix}XXXX${settings.domainSuffix}", HttpStatusCode.BadRequest
                        )
                    }

                    val requestedPart = spec.domain
                        .removePrefix(settings.domainPrefix)
                        .removeSuffix(settings.domainSuffix)
                        .toLowerCase()

                    // A few more sanity checks
                    if (!requestedPart.matches(Regex("[a-z0-9_-]+"))) {
                        throw RPCException("Ingress contains invalid characters", HttpStatusCode.BadRequest)
                    }

                    if (requestedPart.length > 100) {
                        throw RPCException("Supplied ingress is too long", HttpStatusCode.BadRequest)
                    }

                    val id = UUID.randomUUID().toString()
                    val ingress = Ingress(
                        id,
                        IngressSpecification(
                            spec.domain.toLowerCase(),
                            spec.product,
                        ),
                        IngressOwner(actor.safeUsername(), project),
                        Time.now(),
                        IngressStatus(null, IngressState.PREPARING),
                        IngressBilling(product.pricePerUnit, 0L),
                        resolvedProduct = product
                    )

                    dao.create(session, ingress)
                    ingress
                }
            }

            val createResp = api.create.call(
                bulkRequestOf(ingress),
                comms.client
            )

            if (!createResp.statusCode.isSuccess()) {
                delete(Actor.System, bulkRequestOf(ingress.map { IngressRetrieve(it.id) }))
                createResp.throwError()
            }

            ingress.map { it.id }
        }
    }

    suspend fun update(
        actor: Actor,
        request: IngressControlUpdateRequest
    ) {
        db.withSession { session ->
            val now = Time.now()
            for ((id, requests) in request.items.groupBy { it.id }) {
                val ingress = dao.retrieve(session, IngressId(id), IngressDataIncludeFlags())
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

                providers.verifyProvider(ingress.specification.product.provider, actor)

                requests.forEach { request ->
                    dao.insertUpdate(
                        session,
                        IngressId(id),
                        IngressUpdate(
                            now,
                            request.state,
                            request.status,
                            request.clearBindingToJob == true,
                            newBinding = null
                        )
                    )
                }
            }
        }
    }

    suspend fun retrieveSettings(
        actor: Actor,
        requestedProduct: ProductReference
    ): IngressSettings {
        val comms = providers.prepareCommunication(requestedProduct.provider)
        val ingressApi = comms.ingressApi ?: throw RPCException("Ingress not supported", HttpStatusCode.BadRequest)
        return ingressApi.retrieveSettings.call(
            requestedProduct,
            comms.client
        ).orThrow()
    }

    suspend fun charge(
        actor: Actor,
        request: IngressControlChargeCreditsRequest
    ): IngressControlChargeCreditsResponse {
        val insufficient = ArrayList<IngressId>()
        val duplicates = ArrayList<IngressId>()

        db.withSession { session ->
            for ((id, requests) in request.items.groupBy { it.id }) {
                val ingress = dao.retrieve(session, IngressId(id), IngressDataIncludeFlags())
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

                providers.verifyProvider(ingress.specification.product.provider, actor)

                requests.forEach { request ->
                    val chargeResult = paymentService.charge(
                        Payment.OfIngress(ingress, request.units, request.chargeId)
                    )

                    when (chargeResult) {
                        is PaymentService.ChargeResult.Charged -> {
                            dao.chargeCredits(session, IngressId(id), chargeResult.amountCharged)
                        }

                        PaymentService.ChargeResult.InsufficientFunds -> {
                            insufficient.add(IngressId(id))
                        }

                        PaymentService.ChargeResult.Duplicate -> {
                            duplicates.add(IngressId(id))
                        }
                    }
                }
            }
        }

        return IngressControlChargeCreditsResponse(insufficient, duplicates)
    }
}
