package dk.sdu.cloud.accounting.services

import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.TimeRangeQuery
import dk.sdu.cloud.accounting.api.UsageChart
import dk.sdu.cloud.accounting.api.UsageLine
import dk.sdu.cloud.accounting.api.UsagePoint
import dk.sdu.cloud.accounting.api.UsageResponse
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode
import org.joda.time.DateTimeZone
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class VisualizationService(
    private val balance: BalanceService,
    private val projectCache: ProjectCache
) {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun usage(
        ctx: DBContext,
        actor: Actor,
        accountId: String,
        accountType: WalletOwnerType,
        query: TimeRangeQuery
    ): UsageResponse {
        return ctx.withSession { session ->
            balance.requirePermissionToReadBalance(session, actor, accountId, accountType)

            data class RowKey(
                val accountId: String,
                val provider: String,
                val category: String,
                val timestamp: Long
            )

            val relevantProductCategoriesByProvider = HashMap<String, HashSet<ProductCategoryId>>()
            val relevantAccountsByProvider = HashMap<String, HashSet<String>>()
            val allCreditsUsed = HashMap<RowKey, Long>()
            val allTimestamps = TreeSet<Long>()

            session
                .sendPreparedStatement(
                    {
                        setParameter("periodStart", query.periodStart / 1000L)
                        setParameter("periodEnd", query.periodEnd / 1000L)
                        setParameter("bucketSize", "${query.bucketSize} ms")
                        setParameter("accountId", accountId)
                        setParameter("accountType", accountType.name)
                    },

                    """
                        select
                            t.original_account_id,
                            t.product_provider,
                            t.product_category,
                            sum(t.amount)::bigint,
                            timestamps.ts

                        from
                            (
                                select generate_series(
                                    to_timestamp(?periodStart)::timestamp,
                                    to_timestamp(?periodEnd)::timestamp,
                                    ?bucketSize::interval
                                ) as ts
                            ) as timestamps left outer join transactions t on (
                                t.completed_at >= timestamps.ts and
                                t.completed_at <= timestamps.ts + ?bucketSize and
                                t.is_reserved = false and
                                t.account_id = ?accountId and
                                t.account_type = ?accountType
                            )

                        group by
                            timestamps.ts,
                            t.original_account_id,
                            t.product_provider,
                            t.product_category
                    """
                )
                .rows
                .forEach { row ->
                    val timestamp = row.getDate(4)!!.toDateTime(DateTimeZone.UTC).millis
                    allTimestamps.add(timestamp)

                    val childAccountId = row.getString(0) ?: return@forEach
                    val productProvider = row.getString(1)!!
                    val productCategory = row.getString(2)!!
                    val creditsUsed = row.getLong(3)!!

                    val relevantAccounts = relevantAccountsByProvider[productProvider] ?: HashSet()
                    relevantAccountsByProvider[productProvider] = relevantAccounts
                    relevantAccounts.add(childAccountId)

                    val relevantCategories = relevantProductCategoriesByProvider[productProvider] ?: HashSet()
                    relevantProductCategoriesByProvider[productProvider] = relevantCategories
                    relevantCategories.add(ProductCategoryId(productCategory, productProvider))

                    allCreditsUsed[RowKey(childAccountId, productProvider, productCategory, timestamp)] = creditsUsed
                }

            val knownProjects = HashMap<String, Project>()
            if (accountType == WalletOwnerType.PROJECT) {
                val allProjects = relevantAccountsByProvider.values.flatten().toSet()
                for (project in allProjects) {
                    if (project in knownProjects) continue
                    val ancestors = projectCache.ancestors.get(project)
                        ?: throw RPCException("Could not find ancestors", HttpStatusCode.BadGateway)

                    ancestors.forEach { newProject ->
                        knownProjects[newProject.id] = newProject
                    }
                }
            }

            val allProviders = relevantAccountsByProvider.keys
            UsageResponse(
                allProviders.map { provider ->
                    val categories = relevantProductCategoriesByProvider.getValue(provider)
                    val accounts = relevantAccountsByProvider.getValue(provider)

                    val lines = categories.flatMap { category ->
                        accounts.map { account ->
                            val points = allTimestamps.map { ts ->
                                UsagePoint(ts, allCreditsUsed[RowKey(account, provider, category.id, ts)] ?: 0L)
                            }

                            val projectPath: String? = if (accountType == WalletOwnerType.PROJECT) {
                                buildList {
                                    var currentProject = knownProjects[account]
                                    while (currentProject != null) {
                                        add(currentProject.title)
                                        currentProject = knownProjects[currentProject.parent]
                                    }
                                }.asReversed().joinToString("/")
                            } else {
                                null
                            }

                            UsageLine(category.id, projectPath, account, points)
                        }
                    }

                    UsageChart(provider, lines)
                }
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
