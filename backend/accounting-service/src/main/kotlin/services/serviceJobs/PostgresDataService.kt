package dk.sdu.cloud.accounting.services.serviceJobs

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

class PostgresDataService(val db: AsyncDBSessionFactory) {

    fun getUsernames(): List<UCloudUser> {
        return runBlocking {
            db.withSession { session ->
                session
                    .sendPreparedStatement(
                        """
                            SELECT id, created_at
                            FROM auth.principals
                        """
                    ).rows
                    .map {
                        UCloudUser(
                            it.getString(0)!!,
                            it.getDate(1)!!
                        )
                    }
                    .filter { !it.username.startsWith("_") }
            }
        }
    }

    fun getUniversity(username: String): UniversityID {
        return runBlocking {
            val orgId = db.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("username", username)
                        },
                        """
                            SELECT org_id
                            FROM auth.principals
                            WHERE id = :username
                        """
                    ).rows
                    .singleOrNull()
                    ?.getString(0) ?: "PASSWORD"
            }
            UniversityID.fromOrgId(orgId)
        }
    }

    fun calculateProductUsageForUserInProjectForDate(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        productType: ProductType,
        username: String,
        projectId: String,
        aau: Boolean
    ): Long {
        if (productType == ProductType.GPU && startDate.isBefore(LocalDateTime.parse("2021-03-01"))) {
            return 0
        }
        if (productType == ProductType.STORAGE) {
            val storageInGB = if (aau) 1000L else {
                //Uses the day with highest storage usage
                runBlocking {
                    db.withSession { session ->
                        session
                            .sendPreparedStatement(
                                {
                                    setParameter("startDate", startDate.toLocalDate().toString())
                                    setParameter("type", productType.catagoryId)
                                    setParameter("username", username)
                                    setParameter("id", projectId)
                                },
                                """
                                select sum(usage)
                                from (
                                    select (initial_balance - local_balance) as usage
                                    from accounting.wallets wa
                                        join accounting.wallet_allocations wall on wa.id = wall.associated_wallet
                                        join accounting.wallet_owner wo on wa.owned_by = wo.id
                                        join accounting.product_categories pc on wa.category = pc.id
                                    where pc.category like '%ceph%' and (username = :username and project_id = :id)
                                    ) as storage
                            """
                            ).rows
                            .firstOrNull()
                            ?.getLong(0) ?: 0L
                    }
                }
            }
            return storageInGB * 1000
        } else if (productType == ProductType.GPU) {
            if (aau) {
                return runBlocking {
                    val amount = db.withSession { session ->
                        session
                            .sendPreparedStatement(
                                {
                                    setParameter("startDate", startDate.toLocalDate().toString())
                                    setParameter("endDate", endDate.toLocalDate().toString())
                                    setParameter("username", username)
                                    setParameter("id", projectId)
                                },
                                """
                                SELECT -sum(usage)::bigint / 60
                                from (
                                    SELECT ((tr.change/p.price_per_unit)) usage, tr.periods, tr.units, tr.change, price_per_unit, p.name
                                    FROM  "accounting"."transactions" tr
                                        join accounting.products p on p.id = tr.product_id
                                        join accounting.product_categories pc on pc.id = p.category
                                        join accounting.wallets w on pc.id = w.category
                                        join accounting.wallet_owner wo on w.owned_by = wo.id
                                    WHERE
                                        initial_transaction_id = transaction_id
                                        AND created_at >= :startDate::timestamp
                                        AND created_at < :endDate::timestamp
                                        AND tr.action_performed_by = :username
                                        and wo.project_id = :id
                                        AND tr.type = 'charge'
                                        and pc.category like 'uc-t4%'
                                ) as used
                            """
                            ).rows
                            .firstOrNull()
                            ?.getLong(0) ?: 0L
                    }
                    amount
                }
            } else {
                return runBlocking {
                    val amount = db.withSession { session ->
                        session
                            .sendPreparedStatement(
                                {
                                    setParameter("startDate", startDate.toLocalDate().toString())
                                    setParameter("endDate", endDate.toLocalDate().toString())
                                    setParameter("username", username)
                                    setParameter("id", projectId)
                                },
                                """
                                SELECT -sum(usage)::bigint / 60
                                from (
                                    SELECT ((tr.change/p.price_per_unit)) usage, tr.periods, tr.units, tr.change, price_per_unit, p.name
                                    FROM  "accounting"."transactions" tr
                                        join accounting.products p on p.id = tr.product_id
                                        join accounting.product_categories pc on pc.id = p.category
                                        join accounting.wallets w on pc.id = w.category
                                        join accounting.wallet_owner wo on w.owned_by = wo.id
                                    WHERE
                                        initial_transaction_id = transaction_id
                                        AND created_at >= :startDate::timestamp
                                        AND created_at < :endDate::timestamp
                                        AND tr.action_performed_by = :username
                                        and wo.project_id = :id
                                        AND tr.type = 'charge'
                                        and pc.category like '%gpu%'
                                ) as used
                            """
                            ).rows
                            .firstOrNull()
                            ?.getLong(0) ?: 0L
                    }
                    amount
                }
            }
        }
        else {
            return runBlocking {
                if (aau) {
                    val amount = db.withSession { session ->
                        session
                            .sendPreparedStatement(
                                {
                                    setParameter("startDate", startDate.toLocalDate().toString())
                                    setParameter("endDate", endDate.toLocalDate().toString())
                                    setParameter("username", username)
                                    setParameter("id", projectId)
                                },
                                """
                                SELECT -sum(usage)::bigint / 60
                                from (
                                    SELECT ((tr.change/p.price_per_unit)) usage, tr.periods, tr.units, tr.change, price_per_unit, p.name
                                    FROM  "accounting"."transactions" tr
                                        join accounting.products p on p.id = tr.product_id
                                        join accounting.product_categories pc on pc.id = p.category
                                        join accounting.wallets w on pc.id = w.category
                                        join accounting.wallet_owner wo on w.owned_by = wo.id
                                    WHERE
                                        initial_transaction_id = transaction_id
                                        AND created_at >= :startDate::timestamp
                                        AND created_at < :endDate::timestamp
                                        AND tr.action_performed_by = :username
                                        and wo.project_id = :id
                                        AND tr.type = 'charge'
                                        and pc.category like 'uc-gen%'
                                ) as used
                            """
                            ).rows
                            .firstOrNull()
                            ?.getLong(0) ?: 0L
                    }
                    amount
                } else {
                    val amount = db.withSession { session ->
                        session
                            .sendPreparedStatement(
                                {
                                    setParameter("startDate", startDate.toLocalDate().toString())
                                    setParameter("endDate", endDate.toLocalDate().toString())
                                    setParameter("username", username)
                                    setParameter("id", projectId)
                                },
                                """
                                SELECT -sum(usage)::bigint / 60
                                from (
                                    SELECT ((tr.change/p.price_per_unit)) usage, tr.periods, tr.units, tr.change, price_per_unit, p.name
                                    FROM  "accounting"."transactions" tr
                                        join accounting.products p on p.id = tr.product_id
                                        join accounting.product_categories pc on pc.id = p.category
                                        join accounting.wallets w on pc.id = w.category
                                        join accounting.wallet_owner wo on w.owned_by = wo.id
                                    WHERE
                                        initial_transaction_id = transaction_id
                                        AND created_at >= :startDate::timestamp
                                        AND created_at < :endDate::timestamp
                                        AND tr.action_performed_by = :username
                                        and wo.project_id = :id
                                        and pc.product_type = 'COMPUTE'
                                        AND tr.type = 'charge'
                                        and p.name not like 'uc%' and p.name not like '%gpu%'
                                ) as used
                            """
                            ).rows
                            .firstOrNull()
                            ?.getLong(0) ?: 0L
                    }
                    amount
                }
            }
        }
    }

    fun calculateProductUsageForUserInProject(
        startDate: LocalDateTime,
        productType: ProductType,
        username: String,
        projectId: String
    ): Long {
        if (productType == ProductType.GPU && startDate.isBefore(LocalDateTime.parse("2021-03-01"))) {
            return 0
        }
        if (productType == ProductType.STORAGE) {
            val storageInGB = runBlocking {
                db.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("startDate", startDate.toLocalDate().toString())
                                setParameter("type", productType.catagoryId)
                                setParameter("id", projectId)
                            },
                            """
                                select sum(usage)
                                from (
                                    select (initial_balance - local_balance) as usage
                                    from accounting.wallets wa
                                        join accounting.wallet_allocations wall on wa.id = wall.associated_wallet
                                        join accounting.wallet_owner wo on wa.owned_by = wo.id
                                        join accounting.product_categories pc on wa.category = pc.id
                                    where pc.category like '%ceph%' and wo.project_id = :id
                                    ) as storage
                            """
                        ).rows
                        .firstOrNull()
                        ?.getLong(0) ?: 0L
                }
            }
            return storageInGB * 1000
        } else {
            return runBlocking {
                val amount = db.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("startDate", startDate.toString().substringBefore("T"))
                                setParameter("type", productType.catagoryId)
                                setParameter("username", username)
                                setParameter("projectid", projectId)
                            },
                            """
                            SELECT -sum(usage)::bigint / 60
                                from (
                                    SELECT ((tr.change/p.price_per_unit)) usage, tr.periods, tr.units, tr.change, price_per_unit, p.name
                                    FROM  "accounting"."transactions" tr
                                        join accounting.products p on p.id = tr.product_id
                                        join accounting.product_categories pc on pc.id = p.category
                                        join accounting.wallets w on pc.id = w.category
                                        join accounting.wallet_owner wo on w.owned_by = wo.id
                                    WHERE
                                        initial_transaction_id = transaction_id
                                        AND created_at >= :startDate::timestamp
                                        AND pc.product_type = :type
                                        AND tr.action_performed_by = :username
                                    AND tr.type = 'charge' and wo.project_id = :id
                                ) as used
                            """
                        ).rows
                        .firstOrNull()
                        ?.getLong(0) ?: 0L
                }
                amount
            }
        }
    }

    fun calculateProductUsageForCenter(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        productType: ProductType,
        aau: Boolean
    ): Long {
        if (productType == ProductType.STORAGE) {
            val storageInGB = runBlocking {
                db.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("startDate", startDate.toLocalDate().toString())
                                setParameter("endDate", endDate.toLocalDate().toString())
                            },
                            """
                                select sum(usage)::bigint
                                from (
                                    select (initial_balance - local_balance) as usage
                                    from accounting.wallets wa
                                        join accounting.wallet_allocations wall on wa.id = wall.associated_wallet
                                        join accounting.wallet_owner wo on wa.owned_by = wo.id
                                        join accounting.product_categories pc on wa.category = pc.id
                                    where pc.category like '%ceph%' 
                                    ) as storage
                            """
                        ).rows
                        .firstOrNull()
                        ?.getLong(0) ?: 0L
                }
            }
            return storageInGB * 1000
        } else if (productType == ProductType.GPU){
            val amount = if (aau) {
                runBlocking {
                    db.withSession { session ->
                        session
                            .sendPreparedStatement(
                                {
                                    setParameter("startDate", startDate.toLocalDate().toString())
                                    setParameter("endDate", endDate.toLocalDate().toString())
                                },
                                """
                            SELECT -sum(usage)::bigint / 60
                            from (
                                SELECT ((tr.change/p.price_per_unit)) usage, tr.periods, tr.units, tr.change, price_per_unit, p.name
                                FROM  "accounting"."transactions" tr
                                    join accounting.products p on p.id = tr.product_id
                                    join accounting.product_categories pc on pc.id = p.category
                                WHERE
                                    initial_transaction_id = transaction_id
                                    AND created_at >= :startDate::timestamp
                                    AND created_at < :endDate::timestamp
                                    AND tr.action_performed_by NOT LIKE '\_%'
                                AND tr.type = 'charge'
                                and p.name like 'uc-t4%'
                            ) as used;
                        """
                            ).rows
                            .firstOrNull()
                            ?.getLong(0) ?: 0L
                    }
                }
            } else { //SDU
                0L
            }
            //Get Corehours by dividing amount with pricing and then with 60 to get in hours
            return amount
        } else {
            //amount = amount payed for jobs in period SDU (Does not include personal workspaces)
            if (aau) {
                val amount = runBlocking {
                    db.withSession { session ->
                        session
                            .sendPreparedStatement(
                                {
                                    setParameter("startDate", startDate.toLocalDate().toString())
                                    setParameter("endDate", endDate.toLocalDate().toString())
                                },
                                """
                            SELECT -sum(usage)::bigint / 60
                            from (
                                SELECT ((tr.change/p.price_per_unit)) usage, tr.periods, tr.units, tr.change, price_per_unit, p.name
                                FROM  "accounting"."transactions" tr
                                    join accounting.products p on p.id = tr.product_id
                                    join accounting.product_categories pc on pc.id = p.category
                                WHERE
                                    initial_transaction_id = transaction_id
                                    AND created_at >= :startDate::timestamp
                                    AND created_at < :endDate::timestamp
                                    AND tr.action_performed_by NOT LIKE '\_%'
                                AND tr.type = 'charge'
                                and p.name like 'uc-gene%'
                            ) as used;
                        """
                            ).rows
                            .firstOrNull()
                            ?.getLong(0) ?: 0L
                    }
                }
                //Get Corehours by dividing amount with pricing and then with 60 to get in hours
                return amount
            } else {
                val amount = runBlocking {
                    db.withSession { session ->
                        session
                            .sendPreparedStatement(
                                {
                                    setParameter("startDate", startDate.toLocalDate().toString())
                                    setParameter("endDate", endDate.toLocalDate().toString())
                                },
                                """
                                    SELECT -sum(usage)::bigint / 60
                                    from (
                                        SELECT ((tr.change/p.price_per_unit)) usage, tr.periods, tr.units, tr.change, price_per_unit, p.name
                                        FROM  "accounting"."transactions" tr
                                            join accounting.products p on p.id = tr.product_id
                                            join accounting.product_categories pc on pc.id = p.category
                                        WHERE
                                            initial_transaction_id = transaction_id
                                            AND created_at >= :startDate::timestamp
                                            AND created_at < :endDate::timestamp
                                            AND tr.action_performed_by NOT LIKE '\_%'
                                            AND tr.type = 'charge'
                                        and p.name not like 'uc%' and p.name not like '%gpu%'
                                    ) as used;
                                """
                            ).rows
                            .firstOrNull()
                            ?.getLong(0) ?: 0L
                    }
                }
                //Get Corehours by dividing amount with pricing and then with 60 to get in hours
                return amount
            }
        }
    }

    data class Usage(
        val coreHours: Long,
        val performedBy: String,
        val username: String?,
        val projectID: String?,
        val allocationAffected: Long,
        val productCategory: String
    )

    fun retrieveUsageAAU(startDate: LocalDateTime, endDate: LocalDateTime, productType: ProductType): List<Usage> {
        return runBlocking {
            db.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("startDate", startDate.toLocalDate().toString())
                            setParameter("endDate", endDate.toLocalDate().toString())
                        },
                        when (productType) {
                            ProductType.GPU -> {
                                """
                                    select (((usage)/60)/10)::bigint hours, action_performed_by, username, project_id, affected_allocation_id, category
                                    from (
                                        select -sum(tr.change/p.price_per_unit) usage, tr.affected_allocation_id, tr.action_performed_by, wo.username, wo.project_id, pc.category
                                        from accounting.wallets w
                                            join accounting.wallet_owner wo on w.owned_by = wo.id
                                            join accounting.wallet_allocations wall on w.id = wall.associated_wallet
                                            join accounting.transactions tr on wall.id = tr.affected_allocation_id
                                            join accounting.products p on p.id = tr.product_id
                                            join accounting.product_categories pc on p.category = pc.id
                                        where
                                            tr.created_at >= :startDate
                                            and tr.created_at < :endDate
                                            and tr.initial_transaction_id = tr.transaction_id
                                            and pc.product_type not in ('STORAGE', 'INGRESS', 'LICENSE', 'NETWORK_IP', 'SYNCHRONIZATION')
                                            and pc.category like 'uc-t%'
                                            and tr.type = 'charge'
                                        group by tr.affected_allocation_id, wo,username, wo.project_id, pc.category, tr.action_performed_by
                                        order by tr.affected_allocation_id
                                    ) as used
                                    ;
                                """.trimIndent()
                            }
                            ProductType.CPU -> {
                                """
                                    select ((usage)/60)::bigint hours, action_performed_by, username, project_id, affected_allocation_id, category
                                    from (
                                        select -sum(tr.change/p.price_per_unit) usage, tr.affected_allocation_id, tr.action_performed_by, wo.username, wo.project_id, pc.category
                                        from accounting.wallets w
                                            join accounting.wallet_owner wo on w.owned_by = wo.id
                                            join accounting.wallet_allocations wall on w.id = wall.associated_wallet
                                            join accounting.transactions tr on wall.id = tr.affected_allocation_id
                                            join accounting.products p on p.id = tr.product_id
                                            join accounting.product_categories pc on p.category = pc.id
                                        where
                                            tr.created_at >= :startDate
                                            and tr.created_at < :endDate
                                            and tr.initial_transaction_id = tr.transaction_id
                                            and pc.product_type not in ('STORAGE', 'INGRESS', 'LICENSE', 'NETWORK_IP', 'SYNCHRONIZATION')
                                            and pc.category like 'uc-gene%'
                                            and tr.type = 'charge'
                                        group by tr.affected_allocation_id, wo,username, wo.project_id, pc.category, tr.action_performed_by
                                        order by tr.affected_allocation_id
                                    ) as used
                                    ;
                                """.trimIndent()
                            }
                            else -> {throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Wrong product types")}
                        }
                    ).rows
                    .map {
                        Usage(
                            it.getLong(0)!!,
                            it.getString(1)!!,
                            it.getString(2),
                            it.getString(3),
                            it.getLong(4)!!,
                            it.getString(5)!!
                        )
                    }
            }
        }
    }

    fun retrieveUsageSDU(startDate: LocalDateTime, endDate: LocalDateTime, productType: ProductType): List<Usage> {
        return runBlocking {
            db.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("startDate", startDate.toLocalDate().toString())
                            setParameter("endDate", endDate.toLocalDate().toString())
                        },
                        when (productType) {
                            ProductType.GPU -> {
                                """
                                    with gpusmall as (
                                        select (-sum(tr.change/p.price_per_unit)/20) usage, tr.affected_allocation_id, tr.action_performed_by, wo.username, wo.project_id, pc.category
                                        from accounting.wallets w
                                            join accounting.wallet_owner wo on w.owned_by = wo.id
                                            join accounting.wallet_allocations wall on w.id = wall.associated_wallet
                                            join accounting.transactions tr on wall.id = tr.affected_allocation_id
                                            join accounting.products p on p.id = tr.product_id
                                            join accounting.product_categories pc on p.category = pc.id
                                        where
                                            tr.created_at >= :startDate
                                            and tr.created_at < :endDate
                                            and tr.initial_transaction_id = tr.transaction_id
                                            and pc.product_type not in ('STORAGE', 'INGRESS', 'LICENSE', 'NETWORK_IP', 'SYNCHRONIZATION')
                                            and pc.category not like 'uc%'
                                            and pc.category like '%gpu%'
                                            and tr.type = 'charge'
                                        group by tr.affected_allocation_id, wo,username, wo.project_id, pc.category, tr.action_performed_by
                                        order by tr.affected_allocation_id
                                    ),
                                    gpubig as (
                                        select (-sum(tr.change/p.price_per_unit)/12) usage, tr.affected_allocation_id, tr.action_performed_by, wo.username, wo.project_id, pc.category
                                        from accounting.wallets w
                                            join accounting.wallet_owner wo on w.owned_by = wo.id
                                            join accounting.wallet_allocations wall on w.id = wall.associated_wallet
                                            join accounting.transactions tr on wall.id = tr.affected_allocation_id
                                            join accounting.products p on p.id = tr.product_id
                                            join accounting.product_categories pc on p.category = pc.id
                                        where
                                            tr.created_at >= :startDate
                                            and tr.created_at < :endDate
                                            and tr.initial_transaction_id = tr.transaction_id
                                            and pc.product_type not in ('STORAGE', 'INGRESS', 'LICENSE', 'NETWORK_IP', 'SYNCHRONIZATION')
                                            and pc.category not like 'uc%'
                                            and pc.category like '%gpu%'
                                            and tr.type = 'charge'
                                        group by tr.affected_allocation_id, wo,username, wo.project_id, pc.category, tr.action_performed_by
                                        order by tr.affected_allocation_id
                                    )
                                    select ((usage)/60)::bigint hours, action_performed_by, username, project_id, affected_allocation_id, category
                                    from gpubig
                                    union
                                    select ((usage)/60)::bigint hours, action_performed_by, username, project_id, affected_allocation_id, category
                                    from gpusmall;
                                """.trimIndent()
                            }
                            ProductType.CPU -> {
                                """
                                    select ((usage)/60)::bigint hours, action_performed_by, username, project_id, affected_allocation_id, category
                                    from (
                                        select -sum(tr.change/p.price_per_unit) usage, tr.affected_allocation_id, tr.action_performed_by, wo.username, wo.project_id, pc.category
                                        from accounting.wallets w
                                            join accounting.wallet_owner wo on w.owned_by = wo.id
                                            join accounting.wallet_allocations wall on w.id = wall.associated_wallet
                                            join accounting.transactions tr on wall.id = tr.affected_allocation_id
                                            join accounting.products p on p.id = tr.product_id
                                            join accounting.product_categories pc on p.category = pc.id
                                        where
                                            tr.created_at >= :startDate
                                            and tr.created_at < :endDate
                                            and tr.initial_transaction_id = tr.transaction_id
                                            and pc.product_type not in ('STORAGE', 'INGRESS', 'LICENSE', 'NETWORK_IP', 'SYNCHRONIZATION')
                                            and pc.category not like 'uc%'
                                            and pc.category not like '%gpu%'
                                            and tr.type = 'charge'
                                        group by tr.affected_allocation_id, wo,username, wo.project_id, pc.category, tr.action_performed_by
                                        order by tr.affected_allocation_id
                                    ) as used
                                    ;
                                """.trimIndent()
                            }
                            else -> {throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Wrong product types")}
                        }
                    ).rows
                    .map {
                        Usage(
                            it.getLong(0)!!,
                            it.getString(1)!!,
                            it.getString(2),
                            it.getString(3),
                            it.getLong(4)!!,
                            it.getString(5)!!
                        )
                    }
            }
        }
    }


    fun getSDUStorage(): Map<DeicReportService.UserInProject, Long> {
        val mapping = mutableMapOf<DeicReportService.UserInProject, Long>()
        runBlocking {
            db.withTransaction { session ->
                session.sendPreparedStatement(
                    {
                    },
                    """
                        select (initial_balance - local_balance) as usage, wo.username, wo.project_id, coalesce(pm.username, wo.username)
                        from accounting.wallets wa
                            join accounting.wallet_allocations wall on wa.id = wall.associated_wallet
                            join accounting.wallet_owner wo on wa.owned_by = wo.id
                            join accounting.product_categories pc on wa.category = pc.id
                            left join project.projects pr on wo.project_id = pr.id
                            left join project.project_members pm on pr.id = pm.project_id
                        where pc.category like '%ceph%'
                    """
                ).rows
                    .forEach {
                        val uip = DeicReportService.UserInProject(it.getString(3)!!, it.getString(2))
                        val found = mapping[uip]
                        if (found == null) {
                            mapping[uip] = it.getLong(0)!!
                        } else {
                            mapping[uip] = found + it.getLong(0)!!
                        }
                    }
            }
        }
        return mapping
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

    fun findProjects(): List<Project> {
        return runBlocking {
            db.withTransaction { session ->
                //Projects excluded root projects and SDU_* and TYPE1_* projects (rootish)
                session
                    .sendPreparedStatement(
                        {},
                        """
                            SELECT id, title, parent, archived
                            FROM project.projects
                            WHERE parent IS NOT NULL
                                AND parent != '3196deee-c3c2-464b-b328-4d3c5d02b953'
                                AND parent != 'e37a704e-34e3-4f11-931c-2ecf3f07ffcb'
                                AND parent != '7672413e-d43b-4425-aa96-4cdd846e1192'
                        """
                    ).rows
                    .map {
                        Project(
                            it.getString(0)!!,
                            it.getString(1)!!,
                            it.getString(2),
                            it.getBoolean(3)!!
                        )
                    }
            }
        }
    }

    fun findProject(
        id: String
    ): Project {
        return runBlocking {
            db.withSession { session ->
                val project = session
                    .sendPreparedStatement(
                        { setParameter("id", id) },
                        "select id, title, parent, archived from project.projects where id = :id"
                    )
                    .rows
                    .singleOrNull()
                if (project != null) {
                    Project(
                        project.getString(0)!!,
                        project.getString(1)!!,
                        project.getString(2),
                        project.getBoolean(3)!!
                    )
                } else {
                    throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                }
            }
        }
    }

    fun viewAncestors(
        projectId: String
    ): List<Project> {
        val resultList = ArrayList<Project>()
        runBlocking {
            db.withSession { session ->
                var currentProject: Result<Project> = runCatching { findProject(projectId) }
                currentProject.getOrThrow() // Throw immediately if the initial project is not found
                while (currentProject.isSuccess) {
                    val nextProject = currentProject.getOrThrow()
                    resultList.add(nextProject)

                    val parent = nextProject.parent ?: break
                    currentProject = runCatching { findProject(parent) }
                }

                val ex = currentProject.exceptionOrNull()
                if (ex != null) {
                    if (ex is RPCException &&
                        ex.httpStatusCode in setOf(HttpStatusCode.NotFound, HttpStatusCode.Forbidden)
                    ) {
                        // All good, we expected one of these
                    } else {
                        // Not good, rethrow to caller
                        throw ex
                    }
                }
            }
        }
        return resultList.asReversed()
    }

    fun findActiveProjectsForAauUsage(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<String> {
        return runBlocking {
            db.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("startDate", startDate.toLocalDate().toString())
                            setParameter("endDate", endDate.toLocalDate().toString())
                        },
                        """
                            select wo.project_id
                            from accounting.wallet_allocations wall
                                join accounting.wallets w on wall.associated_wallet = w.id
                                join accounting.wallet_owner wo on w.owned_by = wo.id
                                join accounting.product_categories pc on w.category = pc.id
                                join accounting.transactions tr on wall.id = tr.affected_allocation_id
                            where pc.category like 'uc-%'
                                and tr.created_at >= :startDate
                                and tr.created_at < :endDate
                        """.trimIndent()
                    ).rows
                    .mapNotNull {
                        it.getString(0)
                    }
            }
        }
    }

    fun findProjectMembers(projectId: String): List<ProjectMemberInfo> {
        return runBlocking {
            db.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("projectid", projectId)
                        },
                        """
                            SELECT created_at, username, project_id
                            FROM project.project_members
                            WHERE project_id = :projectid
                        """
                    ).rows
                    .map {
                        ProjectMemberInfo(
                            it.getDate(0)!!,
                            it.getString(1)!!,
                            it.getString(2)!!
                        )
                    }
            }
        }
    }
}
