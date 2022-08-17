package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession

class FeatureIngress(
    private val domainPrefix: String,
    private val domainSuffix: String,
    private val db: DBContext,
    private val k8: K8Dependencies,
) : JobFeature {
    suspend fun create(ingresses: BulkRequest<Ingress>) {
        IngressControl.chargeCredits.call(
            bulkRequestOf(ingresses.items.map { ResourceChargeCredits(it.id, it.id, 1) }),
            k8.serviceClient
        ).orThrow()

        db.withSession { session ->
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

                session.prepareStatement(
                    "insert into ucloud_compute_ingresses (id, domain) values (:id, :domain)"
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("id", ingress.id)
                        bindString("domain", ingress.specification.domain)
                    }
                )
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
        db.withSession { session ->
            ingresses.items.forEach { ingress ->
                session.prepareStatement(
                    "delete from ucloud_compute_bound_ingress where ingress_id = :id"
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("id", ingress.id)
                    }
                )

                session.prepareStatement(
                    "delete from ucloud_compute_ingresses where id = :id"
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("id", ingress.id)
                    }
                )
            }
        }
    }

    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
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
        return "${domainPrefix}$jobId-$jobRank.$domainSuffix"
    }

    companion object : Loggable {
        override val log = logger()
        private val regex = Regex("([-_a-z0-9]){5,255}")
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
