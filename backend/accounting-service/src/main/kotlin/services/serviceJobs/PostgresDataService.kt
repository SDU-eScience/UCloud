package dk.sdu.cloud.accounting.services.serviceJobs

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

class PostgresDataService(val db: AsyncDBSessionFactory) {
    fun calculateProductUsageForCenter(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        productType: ProductType,
        aau: Boolean
    ): Long {
        if (productType == ProductType.STORAGE) {
            val usageByProduct = runBlocking {
                db.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("start", startDate.toLocalDate().toString())
                                setParameter("end", endDate.toLocalDate().toString())
                            },
                            """
                                select sum(th.local_change), accounting.product_category_to_json(pc,au)
                                from accounting.transaction_history th join
                                    accounting.wallet_allocations alloc on alloc.id = th.affected_allocation join
                                    accounting.wallets wa on alloc.associated_wallet = wa.id join
                                    accounting.product_categories pc on pc.id = wa.category join
                                    accounting.accounting_units au on au.id = pc.accounting_unit
                                where th.local_change = th.tree_change
                                    and pc.product_type = 'STORAGE'
                                    and th.action = 'CHARGE'
                                    and th.local_change != 0
                                    and created_at > :start::timestamp and created_at < :end::timestamp
                                group by pc, au
                            """
                        ).rows
                        .map {
                            UsageByProduct(
                                it.getLong(0)!!,
                                defaultMapper.decodeFromString(it.getString(1)!!)
                            )
                        }
                }
            }
            //NOTE(Henrik) This only works now since we only hae one storage type which runs with GB
            val storageInGB = usageByProduct.sumOf { it.usage }
            return storageInGB * 1000
        } else if (productType == ProductType.GPU){
            val amount = if (aau) {
                //TODO() MAKE QUERY FOR AAU
                0L
            } else { //SDU
                0L
            }
            //Get Corehours by dividing amount with pricing and then with 60 to get in hours
            return amount
        } else {
            //amount = amount payed for jobs in period SDU (Does not include personal workspaces)
            if (aau) {
                val amount = runBlocking {
                    //TODO() MAKE QUERY FOR AAU
                    0L
                }
                return amount
            } else {
                val amount = runBlocking {
                    db.withSession { session ->
                        session
                            .sendPreparedStatement(
                                {
                                    setParameter("start", startDate.toLocalDate().toString())
                                    setParameter("end", endDate.toLocalDate().toString())
                                },
                                """
                                    select sum(th.local_change), accounting.product_category_to_json(pc,au)
                                    from accounting.transaction_history th join
                                        accounting.wallet_allocations alloc on alloc.id = th.affected_allocation join
                                        accounting.wallets wa on alloc.associated_wallet = wa.id join
                                        accounting.product_categories pc on pc.id = wa.category join
                                        accounting.accounting_units au on au.id = pc.accounting_unit
                                    where th.local_change = th.tree_change
                                        and pc.product_type = 'COMPUTE'
                                        and th.action = 'CHARGE'
                                        and th.local_change != 0 
                                        and (pc.provider = 'K8' or pc.provider = 'ucloud')
                                        and created_at > :start::timestamp and created_at < :end::timestamp
                                    group by pc, au
                                """
                            ).rows
                            .map {
                                UsageByProduct(
                                    it.getLong(0)!!,
                                    defaultMapper.decodeFromString(it.getString(1)!!)
                                )
                            }.sumOf { it.usage }
                    }
                }
                return amount
            }
        }
    }

    suspend fun getProjectUsage(): List<ProjectAndUsage> {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {},
                """
                    select sum(th.local_change), wo.project_id, accounting.product_category_to_json(pc,au)
                    from accounting.transaction_history th join
                        accounting.wallet_allocations alloc on th.affected_allocation = alloc.id join
                        accounting.wallets w on alloc.associated_wallet = w.id join
                        accounting.wallet_owner wo on w.owned_by = wo.id join
                        accounting.product_categories pc on w.category = pc.id join
                        accounting.accounting_units au on au.id = pc.accounting_unit
                    where pc.product_type = 'COMPUTE'
                    group by wo.project_id, pc, au
                """.trimIndent()
            ).rows.map {
                ProjectAndUsage(
                    it.getString(1)!!,
                    defaultMapper.decodeFromString(it.getString(2)!!),
                    it.getLong(0)!!
                )
            }
        }
    }

    suspend fun getProjectMembers(): List<UserInProject> {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {

                },
                """
                    select username, project_id from project.project_members
                    order by project_id
                """.trimIndent()
            ).rows.map {
                UserInProject(
                    it.getString(0)!!,
                    it.getString(1)!!
                )
            }
        }
    }

    fun getWallets(projectId: String): List<WalletInfo> {
        return runBlocking {
            db.withTransaction { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("projectid", projectId)
                        },
                        """
                            SELECT wo.project_id, sum(walloc.initial_balance::bigint)::bigint,
                                (sum(walloc.initial_balance)::bigint - sum(walloc.local_balance)::bigint)::bigint,  p.price_per_unit,  pc.category
                            FROM accounting.wallets wa join
                                accounting.product_categories pc on pc.id = wa.category join
                                accounting.wallet_owner wo on wo.id = wa.owned_by join
                                accounting.wallet_allocations walloc on walloc.associated_wallet = wa.id join 
                                accounting.products p on pc.id = p.category
                            WHERE wo.project_id = :projectid
                            group by wo.project_id, pc.category, p.price_per_unit
                        """
                    ).rows
                    .map {
                        WalletInfo(
                            it.getString(0)!!,
                            it.getLong(1)!!,
                            it.getLong(2)!!,
                            it.getLong(3)!!,
                            ProductType.createFromCatagory(it.getString(4)!!)
                        )
                    }
            }
        }
    }

    fun isUnderUCloud(
        projectId: String
    ): Boolean {
        val resultList = runBlocking {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("project_id", projectId)
                    },
                    """
                        with recursive parents as (
                            select p.id, p.parent, title
                            from project.projects p
                            where p.id = :project_id

                            union

                            select pro.id, pro.parent, pro.title
                            from project.projects pro
                            inner join parents pa on pro.id = pa.parent
                        ) select * from parents;
                    """.trimIndent()
                ).rows.map {
                    it.getString(2)!!
                }
            }
        }
        return resultList.any { it == "UCloud" }
    }

}
