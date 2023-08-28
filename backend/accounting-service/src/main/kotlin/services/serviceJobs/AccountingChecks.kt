package dk.sdu.cloud.accounting.services.serviceJobs

import dk.sdu.cloud.accounting.api.ProductV2
import dk.sdu.cloud.accounting.api.UsageReport
import dk.sdu.cloud.accounting.services.wallets.AccountingProcessor
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession

class AccountingChecks(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient,
    private val accountingProcessor: AccountingProcessor
) {

    suspend fun checkJobsVsTransactions() {
        data class JobInfo(
            val startedAt: Long,
            val lastScan: Long,
            val product: ProductV2
        )

        val now = Time.now()
        val startOfYesterday = now - (24 * 60 * 60 * 1000L)
        val jobs = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("start", startOfYesterday)
                    setParameter("now", now)
                },
                """
                    select j.started_at, j.last_scan, accounting.product_to_json(p, pc, au, null)
                    from app_orchestrator.jobs j join
                        provider.resource r on j.resource = r.id join
                        accounting.products p on r.product = p.id join
                        accounting.product_categories pc on p.category = pc.id join
                        accounting.accounting_units au on pc.accounting_unit = au.id
                    where 
                        (j.last_scan > :start::bigint and j.last_scan < :now::bigint)
                        and
                        (r.created_at < :now::bigint or r.created_at > :start:bigint )
                """.trimIndent()
            ).rows.map {
                JobInfo(
                    it.getLong(0)!!,
                    it.getLong(1)!!,
                    defaultMapper.decodeFromString(it.getString(2)!!)
                )
            }
        }

        val transactions = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("start", startOfYesterday)
                    setParameter("now", now)
                },
                """
                    select affected_allocation, created_at, new_tree_usage, new_local_usage, new_quota, action, transaction_id
                    from accounting.transaction_history
                    where created_at < :now and created_at > :start
                """.trimIndent()
            ).rows.map {
                UsageReport.AllocationHistoryEntry(
                    it.getString(0)!!,
                    it.getLong(1)!!,
                    UsageReport.Balance(it.getLong(2)!!, it.getLong(3)!!, it.getLong(4)!!),
                    UsageReport.HistoryAction.valueOf(it.getString(5)!!),
                    it.getString(6)!!
                )
            }
        }
    }
}
