package dk.sdu.cloud.app.kubernetes.services

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*

object BoundIngressTable : SQLTable("bound_ingress") {
    val ingressId = text("ingress_id")
    val jobId = text("job_id")
}

object IngressTable : SQLTable("ingresses") {
    val id = text("id")
    val domain = text("domain")
}

class IngressService(
    val support: IngressSupport,
    private val db: DBContext,
    private val k8: K8Dependencies,
) : JobManagementPlugin {
    suspend fun create(ingresses: BulkRequest<Ingress>) {
        IngressControl.chargeCredits.call(
            bulkRequestOf(ingresses.items.map { ResourceChargeCredits(it.id, it.id, 1) }),
            k8.serviceClient
        ).orThrow()

        try {
            db.withSession { session ->
                for (ingress in ingresses.items) {
                    val isValid = ingress.specification.domain.startsWith(support.domainPrefix) &&
                        ingress.specification.domain.endsWith(support.domainSuffix)

                    if (!isValid) {
                        throw RPCException("Received invalid request from UCloud", HttpStatusCode.BadRequest)
                    }

                    val id = ingress.specification.domain
                        .removePrefix(support.domainPrefix).removeSuffix(support.domainSuffix)
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

                    session.insert(IngressTable) {
                        set(IngressTable.id, ingress.id)
                        set(IngressTable.domain, ingress.specification.domain)
                    }
                }
            }
        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == PostgresErrorCodes.UNIQUE_VIOLATION) {
                throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
            }
            throw ex
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
                session.sendPreparedStatement(
                    { setParameter("id", ingress.id) },
                    "delete from bound_ingress where ingress_id = :id"
                )

                session.sendPreparedStatement(
                    { setParameter("id", ingress.id) },
                    "delete from ingresses where id = :id"
                )
            }
        }
    }

    suspend fun verify(ingress: BulkRequest<Ingress>) {
        // We don't really need to do any verification
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
                session.sendPreparedStatement(
                    {
                        setParameter("ingressId", ingress.id)
                        setParameter("jobId", job.id)
                    },
                    """
                        insert into bound_ingress (ingress_id, job_id) 
                        values (:ingressId, :jobId) 
                        on conflict (ingress_id) do update set job_id = excluded.job_id
                    """
                )
            }
        }
    }

    override suspend fun JobManagement.onCleanup(jobId: String) {
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("jobId", jobId) },
                "delete from bound_ingress where job_id = :jobId"
            )
        }
    }

    suspend fun retrieveJobIdByDomainOrNull(domain: String): String? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("domain", domain) },
                    """
                        select job_id
                        from bound_ingress b join ingresses i on b.ingress_id = i.id
                        where i.domain = :domain
                        limit 1
                    """
                )
                .rows
                .map { it.getString(0)!! }
                .singleOrNull()
        }
    }

    suspend fun retrieveDomainsByJobId(jobId: String): List<String> {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("jobId", jobId)
                    },
                    """
                        select domain
                        from bound_ingress b join ingresses i on b.ingress_id = i.id
                        where b.job_id = :jobId
                    """
                )
                .rows
                .map { it.getString(0)!! }
        }
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
