package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.PaymentModel
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.throwError
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import java.util.*

class LicenseService(
    private val db: AsyncDBSessionFactory,
    private val dao: LicenseDao,
    private val providers: Providers,
    private val projectCache: ProjectCache,
    private val productCache: ProductCache,
    orchestrator: JobOrchestrator,
    private val paymentService: PaymentService,
) {
    init {
        orchestrator.addListener(object : JobListener {
            override suspend fun onVerified(ctx: DBContext, job: Job) {
                val licenses = job.specification.parameters?.values?.filterIsInstance<AppParameterValue.License>()
                    ?: emptyList()
                if (licenses.isEmpty()) return

                val computeProvider = job.specification.product.provider
                val jobProject = job.owner.project
                val jobLauncher = job.owner.createdBy

                ctx.withSession { session ->
                    licenses.forEach { license ->
                        val retrievedLicense = dao.retrieve(
                            session,
                            LicenseId(license.id),
                            LicenseDataIncludeFlags(includeAcl = true, includeProduct = true)
                        ) ?: throw RPCException("Invalid license: ${license.id}", HttpStatusCode.BadRequest)
                        val product = retrievedLicense.resolvedProduct!!

                        if (jobProject != retrievedLicense.owner.project) {
                            throw RPCException("Invalid license: ${license.id}", HttpStatusCode.BadRequest)
                        }

                        if (jobProject == null && jobLauncher != retrievedLicense.owner.createdBy) {
                            throw RPCException("Invalid license: ${license.id}", HttpStatusCode.BadRequest)
                        }

                        if (retrievedLicense.specification.product.provider != computeProvider) {
                            throw RPCException(
                                "Cannot use license provided by " +
                                    "${retrievedLicense.specification.product.provider} in job provided by " +
                                    computeProvider,
                                HttpStatusCode.BadRequest
                            )
                        }

                        if (jobProject != null &&
                            projectCache.retrieveRole(jobLauncher, jobProject)?.isAdmin() != true
                        ) {
                            // We are not an admin. This means we must have an entry in the acl
                            val projectStatus = projectCache.retrieveProjectStatus(jobLauncher)
                            val groups = projectStatus.userStatus?.groups ?: emptyList()
                            val hasAccess = retrievedLicense.acl!!.any { entry ->
                                val entity = entry.entity
                                if (!entry.permissions.any { it == LicensePermission.USE }) return@any false
                                if (entity !is AclEntity.ProjectGroup) return@any false

                                groups.any { it.group == entity.group && it.project == entity.projectId }
                            }

                            if (!hasAccess) {
                                throw RPCException(
                                    "You do not have permissions to use this license. " +
                                        "Contact your PI to get access",
                                    HttpStatusCode.Forbidden
                                )
                            }
                        }

                        if (retrievedLicense.status.state != LicenseState.READY) {
                            throw RPCException(
                                "Ingress ${retrievedLicense.specification.product.id} is not ready",
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
        })
    }

    suspend fun browse(
        actor: Actor,
        project: String?,
        pagination: NormalizedPaginationRequestV2,
        flags: LicenseDataIncludeFlags,
        filters: LicenseFilters,
    ): PageV2<License> {
        if (project != null && projectCache.retrieveRole(actor.safeUsername(), project) == null) {
            throw RPCException("You are not a member of the supplied project", HttpStatusCode.Forbidden)
        }

        return dao.browse(db, actor, project, pagination, flags, filters)
    }

    suspend fun retrieve(
        actor: Actor,
        id: LicenseId,
        flags: LicenseDataIncludeFlags,
    ): License {
        val notFoundMessage = "Permission denied or license does not exist"
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

    private suspend fun checkWritePermission(
        actor: Actor,
        license: License,
    ) {
        if (actor != Actor.System) {
            val project = license.owner.project
            if (project != null && projectCache.retrieveRole(actor.safeUsername(), project) == null) {
                throw RPCException(genericErrorMessage, HttpStatusCode.NotFound)
            }

            if (project == null && license.owner.createdBy != actor.safeUsername()) {
                throw RPCException(genericErrorMessage, HttpStatusCode.NotFound)
            }
        }
    }

    private val genericErrorMessage = "Not found or permission denied"

    suspend fun delete(
        actor: Actor,
        deletionRequest: BulkRequest<LicenseId>,
    ) {
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
                checkWritePermission(actor, item)
            }

            // TODO This could cause problems when a failure happens for a single provider
            // Verification will technically fix it up later, however.

            // All should be good now, time to call the providers
            for ((provider, ingress) in byProvider) {
                val comms = providers.prepareCommunication(provider)
                val api = comms.licenseApi ?: continue // Provider no longer supports ingress. Silently skip.
                api.delete.call(bulkRequestOf(ingress), comms.client)
            }
        }
    }

    suspend fun create(
        actor: Actor,
        project: String?,
        request: BulkRequest<LicenseCreateRequestItem>,
    ): List<String> {
        if (project != null && projectCache.retrieveRole(actor.safeUsername(), project) == null) {
            throw RPCException("You are not a member of the supplied project", HttpStatusCode.Forbidden)
        }

        return request.items.groupBy { it.product.provider }.flatMap { (provider, specs) ->
            val comms = providers.prepareCommunication(provider)
            val api = comms.licenseApi
                ?: throw RPCException("License is not supported by this provider: $provider", HttpStatusCode.BadRequest)

            // NOTE(Dan): It is important that this is performed in a single transaction to allow the provider to
            // immediately start calling us back about these resources, even before it has successfully created the
            // resource. This allows the provider to, for example, perform a charge on the resource before it has
            // been marked as 'created'.
            val license = db.withSession { session ->
                specs.map { spec ->
                    val product =
                        productCache.find<Product.License>(
                            spec.product.provider,
                            spec.product.id,
                            spec.product.category
                        ) ?: throw RPCException("Invalid product", HttpStatusCode.BadRequest)

                    val id = UUID.randomUUID().toString()
                    val ingress = License(
                        id,
                        LicenseSpecification(
                            spec.product,
                        ),
                        ResourceOwner(actor.safeUsername(), project),
                        Time.now(),
                        LicenseStatus(LicenseState.PREPARING),
                        LicenseBilling(product.pricePerUnit, 0L),
                        resolvedProduct = product
                    )

                    dao.create(session, ingress)
                    ingress
                }
            }

            val createResp = api.create.call(bulkRequestOf(license), comms.client)
            if (!createResp.statusCode.isSuccess()) {
                delete(Actor.System, bulkRequestOf(license.map { LicenseRetrieve(it.id) }))
                createResp.throwError()
            }

            license.map { it.id }
        }
    }

    suspend fun updateAcl(
        actor: Actor,
        request: BulkRequest<LicensesUpdateAclRequestItem>,
    ) {
        db.withSession { session ->
            request.items.forEach { update ->
                val license = dao.updateAcl(session, update, update.acl)
                checkWritePermission(actor, license)
            }
        }
    }

    suspend fun update(
        actor: Actor,
        request: LicenseControlUpdateRequest,
    ) {
        db.withSession { session ->
            val now = Time.now()
            for ((id, requests) in request.items.groupBy { it.id }) {
                val ingress = dao.retrieve(session, LicenseId(id), LicenseDataIncludeFlags())
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

                providers.verifyProvider(ingress.specification.product.provider, actor)

                requests.forEach { request ->
                    dao.insertUpdate(
                        session,
                        LicenseId(id),
                        LicenseUpdate(
                            now,
                            request.state,
                            request.status,
                        )
                    )
                }
            }
        }
    }

    suspend fun charge(
        actor: Actor,
        request: LicenseControlChargeCreditsRequest,
    ): LicenseControlChargeCreditsResponse {
        val insufficient = ArrayList<LicenseId>()
        val duplicates = ArrayList<LicenseId>()

        db.withSession { session ->
            for ((id, requests) in request.items.groupBy { it.id }) {
                val license = dao.retrieve(session, LicenseId(id), LicenseDataIncludeFlags())
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

                providers.verifyProvider(license.specification.product.provider, actor)

                requests.forEach { request ->
                    val chargeResult = paymentService.charge(
                        Payment.OfLicense(license, request.units, request.chargeId)
                    )

                    when (chargeResult) {
                        is PaymentService.ChargeResult.Charged -> {
                            dao.chargeCredits(session, LicenseId(id), chargeResult.amountCharged)
                        }

                        PaymentService.ChargeResult.InsufficientFunds -> {
                            insufficient.add(LicenseId(id))
                        }

                        PaymentService.ChargeResult.Duplicate -> {
                            duplicates.add(LicenseId(id))
                        }
                    }
                }
            }
        }

        return LicenseControlChargeCreditsResponse(insufficient, duplicates)
    }
}
