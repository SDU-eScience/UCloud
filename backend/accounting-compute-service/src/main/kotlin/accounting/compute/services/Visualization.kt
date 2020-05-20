package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.accounting.compute.MachineType
import dk.sdu.cloud.accounting.compute.api.AccountType
import dk.sdu.cloud.accounting.compute.api.BreakdownPoint
import dk.sdu.cloud.accounting.compute.api.ComputeChartPoint
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime

class VisualizationService(
    private val balance: BalanceService,
    private val projectCache: ProjectCache
) {
    suspend fun dailyUsage(
        ctx: DBContext,
        requestedBy: String,
        accountId: String,
        accountType: AccountType,
        groupFilter: String?,
        start: Long,
        end: Long
    ): Map<MachineType, List<ComputeChartPoint>> {
        require(groupFilter == null || accountType == AccountType.PROJECT)

        return ctx.withSession { session ->
            balance.requirePermissionToReadBalance(
                session,
                requestedBy,
                accountId,
                accountType
            )

            session
                .sendPreparedStatement(
                    {
                        setParameter("accId", accountId)
                        setParameter("accType", accountType.name)
                        setParameter("start", LocalDateTime(start, DateTimeZone.UTC))
                        setParameter("end", LocalDateTime(end, DateTimeZone.UTC))
                        setParameter("groupMembers", getGroupMembers(groupFilter, accountId))
                    },

                    """
                        select completed_at::date, sum(amount) as usage, account_machine_type
                        from transactions
                        where
                            account_id = ?accId and
                            account_type = ?accType and
                            is_reserved = false and
                            completed_at >= ?start and
                            completed_at <= ?end and
                            (?groupMembers::text[] is null or initiated_by in (select unnest(?groupMembers::text[])))
                        group by completed_at::date, account_machine_type;
                    """
                )
                .rows
                .groupBy { row ->
                    row.getString(2)!!.let { MachineType.valueOf(it) }
                }
                .mapValues { group ->
                    group.value.map {
                        val date = it.getDate(0)!!.toDate().time
                        val amount = it.getLong(1)!!

                        ComputeChartPoint(date, amount)
                    }
                }
        }
    }

    suspend fun cumulativeUsage(
        ctx: DBContext,
        requestedBy: String,
        accountId: String,
        accountType: AccountType,
        groupFilter: String?,
        start: Long,
        end: Long
    ): Map<MachineType, List<ComputeChartPoint>> {
        require(groupFilter == null || accountType == AccountType.PROJECT)

        return ctx.withSession { session ->
            balance.requirePermissionToReadBalance(
                session,
                requestedBy,
                accountId,
                accountType
            )

            session
                .sendPreparedStatement(
                    {
                        setParameter("accId", accountId)
                        setParameter("accType", accountType.name)
                        setParameter("start", LocalDateTime(start, DateTimeZone.UTC))
                        setParameter("end", LocalDateTime(end, DateTimeZone.UTC))
                        setParameter("groupMembers", getGroupMembers(groupFilter, accountId))
                    },

                    """
                        select completed_at, amount as usage, account_machine_type
                        from transactions
                        where
                            account_id = ?accId and
                            account_type = ?accType and
                            is_reserved = false and
                            completed_at >= ?start and
                            completed_at <= ?end and
                            (?groupMembers::text[] is null or initiated_by in (select unnest(?groupMembers::text[])))
                        order by completed_at
                    """
                )
                .rows
                .groupBy { row ->
                    row.getString(2)!!.let { MachineType.valueOf(it) }
                }
                .mapValues { group ->
                    val result = ArrayList<ComputeChartPoint>()
                    for ((index, it) in group.value.withIndex()) {
                        val previousAmount = if (index == 0) 0L else group.value[index].getLong(1)!!
                        val date = it.getDate(0)!!.toDate().time
                        val amount = it.getLong(1)!! + previousAmount

                        result.add(ComputeChartPoint(date, amount))
                    }

                    result
                }
        }
    }

    suspend fun usageBreakdown(
        ctx: DBContext,
        requestedBy: String,
        accountId: String,
        accountType: AccountType,
        groupFilter: String?,
        start: Long,
        end: Long
    ): Map<MachineType, List<BreakdownPoint>> {
        require(groupFilter == null || accountType == AccountType.PROJECT)

        return ctx.withSession { session ->
            balance.requirePermissionToReadBalance(
                session,
                requestedBy,
                accountId,
                accountType
            )

            session
                .sendPreparedStatement(
                    {
                        setParameter("accId", accountId)
                        setParameter("accType", accountType.name)
                        setParameter("start", LocalDateTime(start, DateTimeZone.UTC))
                        setParameter("end", LocalDateTime(end, DateTimeZone.UTC))
                        setParameter("groupMembers", getGroupMembers(groupFilter, accountId))
                    },

                    """
                        select initiated_by, sum(amount), account_machine_type
                        from transactions
                        where
                            account_id = ?accId and
                            account_type = ?accType and
                            is_reserved = false and 
                            completed_at >= ?start and
                            completed_at <= ?end and
                            (?groupMembers::text[] is null or initiated_by in (select unnest(?groupMembers::text[])))
                        group by initiated_by, account_machine_type;
                    """
                )
                .rows
                .groupBy { row ->
                    row.getString(2)!!.let { MachineType.valueOf(it) }
                }
                .mapValues { group ->
                    group.value.map {
                        val initiatedBy = it.getString(0)!!
                        val amount = it.getLong(1)!!

                        BreakdownPoint(initiatedBy, amount)
                    }
                }
        }
    }

    private suspend fun getGroupMembers(groupFilter: String?, accountId: String): List<String>? {
        return if (groupFilter != null) {
            projectCache.groupMembers.get(ProjectAndGroup(accountId, groupFilter)) ?: emptyList()
        } else {
            null
        }
    }
}
