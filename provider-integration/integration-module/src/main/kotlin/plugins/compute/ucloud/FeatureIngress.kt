package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.accounting.api.ErrorCode
import dk.sdu.cloud.accounting.api.ProductCategoryIdV2
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.providerId
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.toReadableStacktrace
import dk.sdu.cloud.utils.forEachGraal
import dk.sdu.cloud.utils.reportConcurrentUse
import dk.sdu.cloud.utils.walletOwnerFromOwnerString
import java.util.concurrent.atomic.AtomicBoolean

class FeatureIngress(
    private val domainPrefix: String,
    private val domainSuffix: String,
    private val db: DBContext,
    private val k8: K8Dependencies,
    private val category: String,
) : JobFeature {
    private suspend fun accountNow(
        owner: String,
        ctx: DBContext = db,
    ): Boolean {
        ensureOwnerColumnIsFilled(ctx)
        val amountUsed = ctx.withSession { session ->
            var count = 0L
            session.prepareStatement("select count(*)::int8 from ucloud_compute_ingresses where owner = :owner")
                .useAndInvoke(
                    prepare = { bindString("owner", owner) },
                    readRow = { row -> count = row.getLong(0)!! },
                )
            count
        }

        return reportConcurrentUse(
            walletOwnerFromOwnerString(owner),
            ProductCategoryIdV2(category, providerId),
            amountUsed
        )
    }

    suspend fun create(ingresses: BulkRequest<Ingress>) {
        val owner = ingresses.items.map { it.owner.project ?: it.owner.createdBy }.toSet().singleOrNull()
            ?: error("Multiple owners in a single create?")

        db.withSession { session ->
            if (!accountNow(owner, session)) {
                throw RPCException(
                    "Unable to allocate a public link. Please make sure you have sufficient funds!",
                    HttpStatusCode.PaymentRequired,
                    ErrorCode.MISSING_COMPUTE_CREDITS.name,
                )
            }

            for (ingress in ingresses.items) {
                val isValid = ingress.specification.domain.startsWith(domainPrefix) &&
                    ingress.specification.domain.endsWith(domainSuffix)

                if (!isValid) {
                    throw RPCException("Received invalid request from UCloud", HttpStatusCode.BadRequest)
                }

                val id = ingress.specification.domain
                    .removePrefix(domainPrefix).removeSuffix(domainSuffix)
                if (id.length < 5) {
                    throw RPCException(
                        "Public links must be at least 5 characters long!",
                        HttpStatusCode.BadRequest
                    )
                }

                for (badWord in blacklistedWords) {
                    if (id.contains(badWord)) {
                        throw RPCException(
                            "Invalid link. Try a different name.",
                            HttpStatusCode.BadRequest
                        )
                    }
                }

                if (!id.lowercase().matches(regex) || id.lowercase().matches(uuidRegex)) {
                    throw RPCException(
                        "Invalid public link requested. Must only contain letters a-z, numbers (0-9), dashes and underscores.",
                        HttpStatusCode.BadRequest
                    )
                }

                if (id.endsWith("-") || id.endsWith("_")) {
                    throw RPCException(
                        "Public links must not end with a dash or an underscore!",
                        HttpStatusCode.BadRequest
                    )
                }

                if (!id.any { it.isLetter() }) {
                    throw RPCException(
                        "Public links must contain at least one letter!",
                        HttpStatusCode.BadRequest
                    )
                }

                try {
                    session.prepareStatement(
                        //language=postgresql
                        "insert into ucloud_compute_ingresses (id, domain, owner) values (:id, :domain, :owner)"
                    ).useAndInvokeAndDiscard(
                        prepare = {
                            bindString("id", ingress.id)
                            bindString("domain", ingress.specification.domain)
                            bindString("owner", owner)
                        }
                    )
                } catch (ex: Throwable) {
                    throw RPCException(
                        "Public link with domain already exists",
                        HttpStatusCode.BadRequest
                    )
                }
            }
        }

        IngressControl.update.call(
            bulkRequestOf(
                ingresses.items.map {
                    ResourceUpdateAndId(it.id, IngressUpdate(IngressState.READY, "Ingress is now ready"))
                }
            ),
            k8.serviceClient
        ).orThrow()
    }

    suspend fun delete(ingresses: BulkRequest<Ingress>) {
        val owner = ingresses.items.map { it.owner.project ?: it.owner.createdBy }.toSet().singleOrNull()
            ?: error("Multiple owners in a single delete?")

        db.withSession { session ->
            ingresses.items.forEach { ingress ->
                session.prepareStatement(
                    //language=postgresql
                    "delete from ucloud_compute_bound_ingress where ingress_id = :id"
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("id", ingress.id)
                    }
                )

                session.prepareStatement(
                    //language=postgresql
                    "delete from ucloud_compute_ingresses where id = :id"
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("id", ingress.id)
                    }
                )
            }

            accountNow(owner, session)
        }
    }

    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        val ingressPoints = job.ingressPoints
        if (ingressPoints.isEmpty()) return

        if (job.specification.replicas > 1) {
            // TODO(Dan): This should probably be solved at the orchestrator level
            throw RPCException(
                "UCloud/compute does not currently support ingress with multiple replicas",
                HttpStatusCode.BadRequest
            )
        }
        db.withSession { session ->
            for (ingress in ingressPoints) {
                session.prepareStatement(
                    //language=postgresql
                    """
                        insert into ucloud_compute_bound_ingress (ingress_id, job_id) 
                        values (:ingressId, :jobId) 
                        on conflict (ingress_id) do update set job_id = excluded.job_id
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("ingressId", ingress.id)
                        bindString("jobId", job.id)
                    }
                )
            }
        }
    }

    override suspend fun JobManagement.onCleanup(jobId: String) {
        db.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                "delete from ucloud_compute_bound_ingress where job_id = :jobId"
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("jobId", jobId)
                }
            )
        }
    }

    suspend fun retrieveJobIdByDomainOrNull(domain: String): String? {
        return db.withSession { session ->
            val rows = ArrayList<String>()
            session
                .prepareStatement(
                    //language=postgresql
                    """
                        select job_id
                        from ucloud_compute_bound_ingress b join ucloud_compute_ingresses i on b.ingress_id = i.id
                        where i.domain = :domain
                        limit 1
                    """
                )
                .useAndInvoke(
                    prepare = { bindString("domain", domain) },
                    readRow = { row -> rows.add(row.getString(0)!!) }
                )

            rows.singleOrNull()
        }
    }

    suspend fun retrieveDomainsByJobId(jobId: String): List<String> {
        return db.withSession { session ->
            val rows = ArrayList<String>()
            session.prepareStatement(
                //language=postgresql
                """
                    select domain
                    from ucloud_compute_bound_ingress b join ucloud_compute_ingresses i on b.ingress_id = i.id
                    where b.job_id = :jobId
                """
            )
            .useAndInvoke(
                prepare = { bindString("jobId", jobId) },
                readRow = { row -> rows.add(row.getString(0)!!) }
            )

            rows
        }
    }

    fun defaultDomainByJobIdAndRank(jobId: String, jobRank: Int): String {
        return "${domainPrefix}$jobId-$jobRank$domainSuffix"
    }

    private var nextScan = 0L
    override suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<Container>) {
        val now = Time.now()
        if (now > nextScan) {
            ensureOwnerColumnIsFilled()

            val owners = ArrayList<String>()
            db.withSession { session ->
                try {
                    session.prepareStatement(
                        """
                            select distinct owner
                            from ucloud_compute_ingresses
                        """
                    ).useAndInvoke(readRow = { row -> owners.add(row.getString(0)!!) })

                    val containers = runtime.list()
                    val resolvedJobs = containers.mapNotNull { jobCache.findJob(it.jobId) }

                    owners.forEachGraal { owner ->
                        if (!accountNow(owner, session)) {
                            val ingressesToTerminateBecauseOf = ArrayList<String>()
                            session.prepareStatement(
                                """
                                    select id
                                    from ucloud_compute_ingresses
                                    where owner = :owner
                                """
                            ).useAndInvoke(
                                prepare = { bindString("owner", owner) },
                                readRow = { row -> ingressesToTerminateBecauseOf.add(row.getString(0)!!) },
                            )

                            val jobsToTerminate = resolvedJobs
                                .asSequence()
                                .filter { (it.owner.project ?: it.owner.createdBy) == owner }
                                .filter { j -> j.ingressPoints.any { it.id in ingressesToTerminateBecauseOf } }
                                .toList()

                            jobsToTerminate.forEachGraal { job ->
                                k8.addStatus(job.id, "Terminating job because of insufficient funds (public link)")
                                containers.filter { it.jobId == job.id }.forEach { it.cancel() }
                            }
                        }
                    }
                } catch (ex: Throwable) {
                    log.warn("Caught exception while accounting public links: ${ex.toReadableStacktrace()}")
                }
            }

            nextScan = now + (1000L * 60 * 60)
        }
    }

    private val didCheckIfOwnerFillIsRequired = AtomicBoolean(false)
    private suspend fun ensureOwnerColumnIsFilled(ctx: DBContext = db) {
        if (!didCheckIfOwnerFillIsRequired.compareAndSet(false, true)) return

        val idsWithMissingOwner = ArrayList<String>()
        ctx.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    select id
                    from ucloud_compute_ingresses
                    where owner is null
                """
            ).useAndInvoke(
                readRow = { row -> idsWithMissingOwner.add(row.getString(0)!!) }
            )
        }

        if (idsWithMissingOwner.isNotEmpty()) {
            val owners = HashMap<String, String>()
            idsWithMissingOwner.forEachGraal { id ->
                val owner = IngressControl.retrieve
                    .call(ResourceRetrieveRequest(IngressIncludeFlags(), id), k8.serviceClient).orThrow().owner
                owners[id] = owner.project ?: owner.createdBy
            }

            ctx.withSession { session ->
                session.prepareStatement(
                    //language=postgresql
                    """
                        with data as (
                            select unnest(:ids) as id, unnest(:owners) as owner
                        )
                        update ucloud_compute_ingresses i
                        set owner = d.owner
                        from data d
                        where i.id = d.id
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindList("ids", owners.keys.toList())
                        bindList("owners", owners.values.toList())
                    }
                )
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
        private val regex = Regex("[a-z]([-_a-z0-9]){4,255}")
        private val uuidRegex = Regex("\\b[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}\\b")

        // This is, to put it mildly, a silly attempt at avoiding malicious URLs.
        // I am not too worried about malicious use at the moment though.
        private val blacklistedWords = hashSetOf(
            "login",
            "logon",
            "password",
            "passw0rd",
            "log1n",
            "log0n",
            "l0gon",
            "l0g0n"
        )
    }
}
