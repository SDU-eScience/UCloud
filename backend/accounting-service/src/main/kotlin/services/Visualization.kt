package dk.sdu.cloud.accounting.services

import dk.sdu.cloud.accounting.api.ProductArea
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
import org.joda.time.DateTime
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

            val productAreas = HashMap<ProductCategoryId, ProductArea>()
            val relevantProductCategoriesByProvider = HashMap<String, HashSet<ProductCategoryId>>()
            val relevantAccountsByProvider = HashMap<String, HashSet<String>>()
            val allCreditsUsed = HashMap<RowKey, Long>()
            val allTimestamps = TreeSet<Long>()

            session
                .sendPreparedStatement(
                    {
                        setParameter("periodStart", query.periodStart / 1000L)
                        setParameter("periodEnd", query.periodEnd / 1000L)
                        setParameter("bucketSize", "${query.bucketSize / 1000L } s")
                    },
                    """
                        select
                            extract(epoch from timestamps.ts)::bigint
                        from (
                             Select generate_series(
                                     to_timestamp(?periodStart) :: timestamp,
                                     to_timestamp(?periodEnd) :: timestamp,
                                     ?bucketSize :: interval
                              ) as ts
                        ) as timestamps;
                    """.trimIndent()
                )
                .rows
                .forEach { row ->
                    val timestamp = row.getLong(0)!! * 1000L
                    allTimestamps.add(timestamp)

                }
            session
                .sendPreparedStatement(
                    {
                        setParameter("periodStart", query.periodStart / 1000L)
                        setParameter("periodEnd", query.periodEnd / 1000L)
                        setParameter("bucketSize", "${query.bucketSize / 1000} s")
                        setParameter("accountId", accountId)
                        setParameter("accountType", accountType.name)
                    },

                    """
                        select
                            t.original_account_id,
                            t.product_provider,
                            t.product_category,
                            sum(t.amount)::bigint,
                            extract(epoch from timestamps.ts)::bigint,
                            pc.area

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
                            ),
                            product_categories pc
                            
                        where
                            pc.category = t.product_category and
                            pc.provider = t.product_provider

                        group by
                            timestamps.ts,
                            t.original_account_id,
                            t.product_provider,
                            t.product_category,
                            pc.area
                    """
                )
                .rows
                .forEach { row ->
                    val timestamp = row.getLong(4)!! * 1000L
                    val childAccountId = row.getString(0) ?: return@forEach
                    val productProvider = row.getString(1)!!
                    val productCategory = row.getString(2)!!
                    val creditsUsed = row.getLong(3)!!
                    val area = ProductArea.valueOf(row.getString(5)!!)

                    val relevantAccounts = relevantAccountsByProvider[productProvider] ?: HashSet()
                    relevantAccountsByProvider[productProvider] = relevantAccounts
                    relevantAccounts.add(childAccountId)

                    val relevantCategories = relevantProductCategoriesByProvider[productProvider] ?: HashSet()
                    relevantProductCategoriesByProvider[productProvider] = relevantCategories

                    val category = ProductCategoryId(productCategory, productProvider)
                    relevantCategories.add(category)
                    productAreas[category] = area

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

            fun findProjectPath(projectId: String): List<Project> {
                if (accountType == WalletOwnerType.USER) return emptyList()
                return buildList {
                    var currentProject = knownProjects[projectId]
                    while (currentProject != null) {
                        add(currentProject!!)
                        currentProject = knownProjects[currentProject.parent]
                    }
                }.asReversed()
            }

            val pathToActiveProject = findProjectPath(accountId)

            val allProviders = relevantAccountsByProvider.keys
            if (allProviders.isEmpty()) {
                val points = allTimestamps.map { ts ->
                    UsagePoint(ts, 0L)
                }

                val lines = UsageLine(ProductArea.COMPUTE, "", null, null, points)

                UsageResponse(
                    listOf(
                        UsageChart(
                            "none",
                            listOf(lines)
                        )
                    )
                )
            } else {
                UsageResponse(
                    allProviders.map { provider ->
                        val removedAccounts = HashSet<String>()
                        val newAccounts = HashSet<String>()

                        if (accountType == WalletOwnerType.PROJECT) {
                            // We only wish to display usage of direct children.
                            // We start by moving our usage to the direct child level.

                            val categories = relevantProductCategoriesByProvider.getValue(provider)
                            val accounts = relevantAccountsByProvider.getValue(provider)
                            for (category in categories) {
                                for (account in accounts) {
                                    val path = findProjectPath(account)
                                    if (path.size > pathToActiveProject.size + 1) {
                                        tsLoop@ for (ts in allTimestamps) {
                                            // We need to move this to the direct child of the active project
                                            val oldKey = RowKey(account, provider, category.id, ts)
                                            val usage = allCreditsUsed[oldKey] ?: continue@tsLoop

                                            val newKey = RowKey(
                                                path[pathToActiveProject.size].id,
                                                provider,
                                                category.id,
                                                ts
                                            )

                                            val newKeyExistingUsage = allCreditsUsed[newKey] ?: 0L
                                            allCreditsUsed[newKey] = newKeyExistingUsage + usage
                                            allCreditsUsed.remove(oldKey)
                                            newAccounts.add(newKey.accountId)
                                        }

                                        removedAccounts.add(account)
                                    }
                                }
                            }

                            relevantAccountsByProvider.getValue(provider).apply {
                                removeAll(removedAccounts)
                                addAll(newAccounts)
                            }
                        }

                        val categories = relevantProductCategoriesByProvider.getValue(provider)
                        val accounts = relevantAccountsByProvider.getValue(provider)
                        val lines = categories.flatMap { category ->
                            accounts.map { account ->
                                val points = allTimestamps.map { ts ->
                                    UsagePoint(ts, allCreditsUsed[RowKey(account, provider, category.id, ts)] ?: 0L)
                                }

                                val projectPath: String? = if (accountType == WalletOwnerType.PROJECT) {
                                    findProjectPath(account).joinToString("/") { it.title }
                                } else {
                                    null
                                }

                                UsageLine(productAreas.getValue(category), category.id, projectPath, account, points)
                            }
                        }

                        UsageChart(provider, lines)
                    }
                )
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
