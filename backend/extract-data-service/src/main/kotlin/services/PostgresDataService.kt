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
                                select (initial_balance - local_balance) as usage, wo.username, wo.project_id
                                from accounting.wallets wa
                                    join accounting.wallet_allocations wall on wa.id = wall.associated_wallet
                                    join accounting.wallet_owner wo on wa.owned_by = wo.id
                                    join accounting.product_categories pc on wa.category = pc.id
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
        aau: Boolean
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
