package dk.sdu.cloud.accounting.services.serviceJobs

import dk.sdu.cloud.accounting.api.AccountingFrequency
import dk.sdu.cloud.accounting.api.ProductV2
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.slack.api.Alert
import dk.sdu.cloud.slack.api.SlackDescriptions
import kotlin.math.abs

class AccountingChecks(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient,
) {

    suspend fun checkJobsVsTransactions() {
        val maxdiff = 100L
        data class JobInfo(
            val resourceId: Long,
            val millisecondsSpend: Long,
            val product: ProductV2,
            val projectId: String?,
            val createdBy: String
        )

        data class TransactionData(
            val usage: Long,
            val walletId: Long,
            val categoryName: String,
            val categoryProvider: String,
            val projectId: String?,
            val username: String?
        )



        val now = Time.now()
        val startOfYesterday = now - (24 * 60 * 60 * 1000L)
        println("$startOfYesterday vs now: $now")
        val jobs = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("start", startOfYesterday)
                    setParameter("now", now)
                },
                """
                    with all_updates as(
                        select
                            r.id,
                            u.created_at,
                            u.extra->>'state' as state,
                            accounting.product_to_json(p, pc, au, null) as prod,
                            proj.id as project_id,
                            r.created_by
                        from provider.resource r join
                            app_orchestrator.jobs j on r.id = j.resource join
                            provider.resource_update u on r.id = u.resource join
                            accounting.products p on r.product = p.id join
                            accounting.product_categories pc on p.category = pc.id join
                            accounting.accounting_units au on pc.accounting_unit = au.id left join
                            project.projects proj on r.project = proj.id
                        where (
                                (
                                    floor(extract(epoch from u.created_at) * 1000) < :now::bigint and
                                    floor(extract(epoch from u.created_at) * 1000) > :start::bigint
                                    ) or
                                j.current_state = 'RUNNING'
                            ) and pc.free_to_use = false
                        order by r.created_at desc , u.created_at desc
                    ),
                    started as (
                        select *
                        from all_updates
                        where state in ('RUNNING')
                    ),
                    stopped as (
                        select *
                        from all_updates
                        where state in ('SUCCESS', 'EXPIRED', 'FAILURE')
                    )
                    select
                        coalesce(started.id, stopped.id) as resource_id,
                        (
                            floor(coalesce(extract(epoch from (stopped.created_at )) * 1000, :now::bigint))
                            - floor(coalesce(extract(epoch from (started.created_at )) * 1000, :start::bigint))
                        )::bigint as milliseconds_spend,
                        coalesce(started.prod, stopped.prod),
                        coalesce(started.project_id, stopped.project_id) as project_id,
                        coalesce(started.created_by, stopped.created_by) as username
                    from started full join stopped on started.id = stopped.id
                """.trimIndent()
            ).rows.map {
                JobInfo(
                    it.getLong(0)!!,
                    it.getLong(1)!!,
                    defaultMapper.decodeFromString(it.getString(2)!!),
                    it.getString(3),
                    it.getString(4)!!
                )
            }
        }

        println("GODT JOBB")

        val computeTransactions = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("start", startOfYesterday)
                    setParameter("now", now)
                },
                """
                    with usage_per_alloc as (
                        select
                            max(new_local_usage)::bigint - min(new_local_usage)::bigint as usage,
                            wall.id wallet_id,
                            pc.category category,
                            pc.provider provider,
                            wo.project_id project_id,
                            wo.username username
                        from accounting.transaction_history th join
                            accounting.wallet_allocations wa on wa.id = th.affected_allocation join
                            accounting.wallets wall on wa.associated_wallet = wall.id join
                            accounting.product_categories pc on pc.id = wall.category join
                            accounting.wallet_owner wo on wall.owned_by = wo.id
                        where floor(extract(epoch from created_at) * 1000) < :now::bigint and
                            floor(extract(epoch from created_at) * 1000) > :start::bigint and
                            pc.product_type = 'COMPUTE'
                        group by wall.id, pc.category, pc.provider, wo.project_id, wo.username, th.affected_allocation
                    )
                    select
                        sum(usage)::bigint,
                        wallet_id,
                        category,
                        provider,
                        project_id,
                        username
                    from usage_per_alloc
                    group by project_id, username, category, provider, wallet_id
                """.trimIndent()
            ).rows.map {
                TransactionData(
                    it.getLong(0)!!,
                    it.getLong(1)!!,
                    it.getString(2)!!,
                    it.getString(3)!!,
                    it.getString(4),
                    it.getString(5)
                )
            }
        }

        println("GOT TRNAS")

        val highDiffs = mutableSetOf<String>()

        val projectJobs = jobs.filter { it.projectId != null}.groupBy { it.projectId }
        val userjobs = jobs.filter { it.projectId == null }.groupBy { it.createdBy }

        fun calculate(info: JobInfo): Long {
            println(info.millisecondsSpend)
            println(AccountingFrequency.toMinutes(info.product.category.accountingFrequency.name))
            println(info.product.price)
            println("Calcuting $info, Got : ${(((info.millisecondsSpend / 1000.0) / AccountingFrequency.toMinutes(info.product.category.accountingFrequency.name)) * info.product.price).toLong()}")
            return (((info.millisecondsSpend / 1000.0) / AccountingFrequency.toMinutes(info.product.category.accountingFrequency.name)) * info.product.price).toLong()
        }


        println(computeTransactions)
        println()
        println(projectJobs)
        println()
        println(userjobs)

        computeTransactions.forEach {transaction ->
            val totalUsage = if (transaction.projectId != null) {
                projectJobs[transaction.projectId]?.sumOf { info ->
                    calculate(info)
                } ?: 0
            } else {
                userjobs[transaction.username]?.sumOf { info ->
                    calculate(info)
                } ?: 0
            }
            val todaysUsage = transaction.usage
            println("todaysUsage: $todaysUsage, total: $totalUsage, maxDiff: $maxdiff, abs: ${abs(todaysUsage - totalUsage)}")
            if (abs(todaysUsage - totalUsage) > maxdiff ) {
                highDiffs.add(transaction.projectId ?: transaction.username ?: "UNKNOWN USER")
            }

        }

        /*SlackDescriptions.sendAlert.call(
            Alert(
                "Noticeable difference in charges and balances for COMPUTE jobs for $highDiffs"
            ),
            serviceClient
        )*/

        println("ALERTS: $highDiffs")

    }
}
