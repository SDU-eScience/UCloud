package dk.sdu.cloud.extract.data.services

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.extract.data.api.*
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
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

    suspend fun getProjectUsage(projectId: String, startDate: LocalDateTime, endDate: LocalDateTime) {

        println(projectId)

        val children = viewChildrenProjectIds(projectId)
        val sduCPUUsage = getCPUUsage(startDate, endDate, false)
        val aauCPUUsage = getCPUUsage(startDate, endDate, true)
        val sduGPUUsage = getGPUUsage(startDate, endDate, false, true)
        val aauGPUUsage = getGPUUsage(startDate, endDate, true)

        var totalCPUUsage = 0L
        var totalGPUUsage = 0L
        children.forEach { project ->
            var cpuUsage = 0L
            cpuUsage += sduCPUUsage[project] ?: 0L
            cpuUsage += aauCPUUsage[project] ?: 0L

            var gpuUsage = 0L
            gpuUsage += sduGPUUsage[project] ?: 0L
            gpuUsage += aauGPUUsage[project] ?: 0L

            totalCPUUsage += cpuUsage
            totalGPUUsage += gpuUsage

            println("$project , CPU: $cpuUsage GPU: $gpuUsage")
        }

        println("CPU: $totalCPUUsage GPU: $totalGPUUsage")
    }

    suspend fun getCPUUsage(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        aau: Boolean
    ):Map<String, Long> {
        val usageForWallet = mutableMapOf<String, Long>()
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("start", startDate.toLocalDate().toString())
                    setParameter("end", endDate.toLocalDate().toString())
                    setParameter("product", if (aau) "uc_gene%" else "u1_standard%")
                },
                """
                    with jobs as (
                        select
                            r.id job_id,
                            coalesce(job.started_at, r.created_at) job_start,
                            case when current_state = 'RUNNING' then now() else job.last_update end job_ended,
                            job.current_state,
                            p.cpu,
                            job.replicas,
                            r.created_by,
                            r.project
                        from app_orchestrator.jobs job join
                            provider.resource r on job.resource = r.id join
                            accounting.products p on r.product = p.id
                        where
                            p.name like :product and
                            (job.started_at < :end) and
                            (job.last_update > :start)
                    ),
                    clamedJobs as (select job_id,
                                          case when job_start < :start then :start else job_start end jobstart,
                                          case when job_ended > :end then :end else job_ended end jobend,
                                          cpu,
                                          replicas,
                                          created_by,
                                          project
                                   from jobs),
                    minutes as (
                        select *, ((extract (epoch from jobend) - extract (epoch from jobstart))/ 60)minSpend
                        from clamedJobs
                        )
                    select 
                        ((minSpend * cpu * replicas)/ 60)::bigint resourcesUsed, 
                        created_by, 
                        project
                    from minutes
                """.trimIndent()
            ).rows.forEach {
                val usage = it.getLong(0)!!
                val username = it.getString(1)
                val project = it.getString(2)
                if (project != null ) {
                    usageForWallet[project] = (usageForWallet[project] ?: 0) + usage
                } else {
                    usageForWallet[username!!] = (usageForWallet[username] ?: 0) + usage
                }
            }
        }
    return usageForWallet
    }

    suspend fun getSDUCeph(): Map<String, Long> {
        val usageForWallet = mutableMapOf<String, Long>()
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    """
                                select local_usage as usage, wo.username, wo.project_id
                                from accounting.wallets_v2 wa
                                    join accounting.wallet_owner wo on wa.wallet_owner = wo.id
                                    join accounting.product_categories pc on wa.product_category = pc.id
                                    left join project.projects pr on wo.project_id = pr.id
                                where pc.category like '%ceph%'
                            """
                ).rows
                .forEach {
                    val username = it.getString(1)
                    val projectId = it.getString(2)
                    val usage = it.getLong(0) ?: 0
                    if (username == null) {
                        usageForWallet[projectId!!] = usage
                    } else {
                        usageForWallet[username] = usage
                    }
                }
        }
        return usageForWallet
    }

    suspend fun getGPUUsage(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        aau: Boolean,
        includeNonDeicGPUS: Boolean = false
    ): Map<String, Long> {
        val usageForWallet = mutableMapOf<String, Long>()
        if (aau) {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("start", startDate.toLocalDate().toString())
                        setParameter("end", endDate.toLocalDate().toString())
                        setParameter("product", "uc_t%" )
                    },
                    """
                    with jobs as (
                        select
                            r.id job_id,
                            coalesce(job.started_at, r.created_at) job_start,
                            case when current_state = 'RUNNING' then now() else job.last_update end job_ended,
                            job.current_state,
                            p.gpu,
                            job.replicas,
                            r.created_by,
                            r.project
                        from app_orchestrator.jobs job join
                            provider.resource r on job.resource = r.id join
                            accounting.products p on r.product = p.id
                        where
                            p.name like :product and
                            (job.started_at < :end) and
                            (job.last_update > :start)
                    ),
                    clamedJobs as (
                        select job_id,
                          case when job_start < :start then :start else job_start end jobstart,
                          case when job_ended > :end then :end else job_ended end jobend,
                          gpu,
                          replicas,
                          created_by,
                          project
                       from jobs
                    ),
                    minutes as (
                        select *, ((extract (epoch from jobend) - extract (epoch from jobstart))/ 60) minSpend
                        from clamedJobs
                        ),
                    used as (
                        select ((minSpend * gpu * replicas)/ 60)::bigint  resourcesUsed, created_by, project
                        from minutes )
                    select * from used;
                """.trimIndent()
                ).rows.forEach {
                    val usage = it.getLong(0)!!
                    val username = it.getString(1)
                    val project = it.getString(2)
                    if (project != null ) {
                        usageForWallet[project] = (usageForWallet[project]?:0) + usage
                    } else {
                        usageForWallet[username!!] = (usageForWallet[username] ?: 0) + usage
                    }
                }
            }
        } else if (includeNonDeicGPUS) {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("start", startDate.toLocalDate().toString())
                        setParameter("end", endDate.toLocalDate().toString())
                        setParameter("product", "%-gpu-%" )
                    },
                    """
                    with jobs as (
                        select
                            r.id job_id,
                            coalesce(job.started_at, r.created_at) job_start,
                            case when current_state = 'RUNNING' then now() else job.last_update end job_ended,
                            job.current_state,
                            p.gpu,
                            job.replicas,
                            r.created_by,
                            r.project
                        from app_orchestrator.jobs job join
                            provider.resource r on job.resource = r.id join
                            accounting.products p on r.product = p.id
                        where
                            p.name like :product and
                            (job.started_at < :end) and
                            (job.last_update > :start)
                    ),
                    clamedJobs as (
                        select job_id,
                          case when job_start < :start then :start else job_start end jobstart,
                          case when job_ended > :end then :end else job_ended end jobend,
                          gpu,
                          replicas,
                          created_by,
                          project
                       from jobs
                    ),
                    minutes as (
                        select *, ((extract (epoch from jobend) - extract (epoch from jobstart))/ 60) minSpend
                        from clamedJobs
                        ),
                    used as (
                        select ((minSpend * gpu * replicas)/ 60)::bigint  resourcesUsed, created_by, project
                        from minutes )
                    select * from used;
                """.trimIndent()
                ).rows.forEach {
                    val usage = it.getLong(0)!!
                    val username = it.getString(1)
                    val project = it.getString(2)
                    if (project != null ) {
                        usageForWallet[project] = (usageForWallet[project]?:0) + usage
                    } else {
                        usageForWallet[username!!] = (usageForWallet[username] ?: 0) + usage
                    }
                }
            }
        }
        return usageForWallet
    }
    suspend fun calculateProductUsage(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        productType: ProductType,
        aau: Boolean
    ): Long {
        return when (productType) {
            ProductType.STORAGE -> {
                getSDUCeph().values.sum()
            }
            ProductType.GPU -> {
                getGPUUsage(startDate, endDate, aau).values.sum()
            }
            else -> {
                getCPUUsage(startDate, endDate, aau).values.sum()
            }
        }
    }

    suspend fun retrieveUsageAAU(startDate: LocalDateTime, endDate: LocalDateTime, productType: ProductType): Map<String, Long> {
        return when (productType) {
            ProductType.CPU -> {
                getCPUUsage(startDate, endDate, true)
            }
            ProductType.GPU -> {
                getGPUUsage(startDate, endDate, true)
            }
            else -> {throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Wrong product types")}
        }
    }

    suspend fun retrieveUsageSDU(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        productType: ProductType
    ): Map<String, Long> {
        return when (productType) {
            ProductType.CPU -> {
                return getCPUUsage(startDate, endDate, false)
            }
            ProductType.GPU -> {
                emptyMap()
            }
            else -> {throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Wrong product types")}
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
                            with lookup as (
                                SELECT distinct (pc.id), pc.category, (p.gpu is not null and p.gpu != 0) as is_gpu
                                from accounting.products p
                                    join accounting.product_categories pc on p.category = pc.id
                            )
                            SELECT wo.project_id, sum(walloc.quota)::bigint,
                                wa.local_usage, pc.product_type, is_gpu, wa.id
                            FROM accounting.wallets_v2 wa join
                                accounting.allocation_groups ag on wa.id = ag.associated_wallet join
                                accounting.wallet_allocations_v2 walloc on ag.id = walloc.associated_allocation_group join
                                accounting.wallet_owner wo on wo.id = wa.wallet_owner join
                                accounting.product_categories pc on pc.id = wa.product_category join
                                lookup l on l.id = pc.id
                            WHERE wo.project_id = :projectid
                            group by wo.project_id, pc.product_type, wa.local_usage, is_gpu, wa.id;
                        """
                    ).rows
                    .map {
                        WalletInfo(
                            it.getString(0)!!,
                            it.getLong(1)!!,
                            it.getLong(2)!!,
                            ProductType.createFromType(it.getString(3)!!, it.getBoolean(4)!!),
                            it.getLong(5)!!
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

    fun listExcludedProjects(): List<Project> {
        val excludedProjects = ArrayList<Project>()

        runBlocking {
            db.withSession { session ->
                val id = session.sendPreparedStatement(
                    """
                    select id
                    from project.projects
                    where title = 'UCloud' and parent is null
                """
                ).rows.singleOrNull()?.getString(0) ?: throw RPCException.fromStatusCode(
                    HttpStatusCode.NotFound,
                    "UCloud project not found"
                )
                session.sendPreparedStatement(
                    {
                        setParameter("id", id)
                    },
                    """
                    with recursive ptree as (
                        select
                            id,
                            title,
                            parent
                        from project.projects
                        where id = :id
                        union
                        select
                            p.id,
                            p.title,
                            p.parent
                        from project.projects p
                        inner join ptree pt on pt.id = p.parent
                    )
                    select * from ptree;
                """
                ).rows.forEach {
                    excludedProjects.add(
                        Project(
                            it.getString(0)!!,
                            it.getString(1)!!,
                            it.getString(2),
                            false,
                            null
                        )
                    )
                }
            }

            db.withSession { session ->
                val id = session.sendPreparedStatement(
                    """
                    select id, title, parent
                    from project.projects
                    where can_consume_resources = false
                """
                ).rows.forEach {
                    excludedProjects.add(
                        Project(
                            it.getString(0)!!,
                            it.getString(1)!!,
                            it.getString(2),
                            false,
                            null
                        )
                    )
                }
            }
        }
        return excludedProjects.toSet().toList()
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

    fun viewChildrenProjectIds(projectId: String): List<String> {
        val resultList = ArrayList<String>()
        val wallets = getWallets(projectId).map { it.walletId }
        runBlocking {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("wallets_given", wallets)
                    },
                    """
                        with recursive children as (
                            select wall1.id walletid
                            from accounting.wallets_v2 wall1 join
                                accounting.allocation_groups ag1 on wall1.id = ag1.associated_wallet
                            where
                                wall1.id in (
                                select
                                    unnest(:wallets_given::bigint[])
                            )
                        
                            union
                        
                            select wall.id
                            from accounting.wallets_v2 wall join
                                accounting.allocation_groups ag on wall.id = ag.associated_wallet join
                                children c on c.walletid = ag.parent_wallet
                        )
                        select distinct coalesce(username, project_id)
                        from children c join
                        accounting.wallets_v2 wall on c.walletid = wall.id join
                            accounting.wallet_owner wo on wall.wallet_owner = wo.id;
                    """.trimIndent()
                ).rows.forEach { resultList.add(it.getString(0)!!) }
            }
        }
        return resultList
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
