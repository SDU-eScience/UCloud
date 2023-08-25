package dk.sdu.cloud.accounting.services.serviceJobs

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession

class AccountingChecks(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient,
) {

    suspend fun checkJobsVsTransactions() {
        val now = Time.now()
        val startOfYesterday = now - (24 * 60 * 60 * 1000L)
        val jobs = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("start", startOfYesterday)
                    setParameter("now", now)
                },
                """
                    select * 
                    from app_orchestrator.jobs j join
                        provider.resource r on j.resource = r.id
                    where 
                        (j.last_scan > :start::bigint and j.last_scan < :now::bigint)
                        and
                        (r.created_at < :now::bigint or r.created_at > :start:bigint )
                """.trimIndent()
            ).rows

            //Get jobs that have run today Last scan? vs created at
            //Get transaction from today (created at > now() minus 1
        }
    }
}
