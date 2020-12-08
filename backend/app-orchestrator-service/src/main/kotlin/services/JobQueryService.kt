package dk.sdu.cloud.app.orchestrator.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jasync.sql.db.ResultSet
import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*

@Suppress("SqlResolve")
class JobQueryService(
    private val db: AsyncDBSessionFactory,
    private val projectCache: ProjectCache,
    private val appStoreCache: AppStoreCache,
    private val machineTypeCache: MachineTypeCache,
) {
    suspend fun browse(
        securityPrincipal: Actor,
        project: String?,
        pagination: NormalizedPaginationRequestV2,
        flags: JobDataIncludeFlags,
    ): PageV2<VerifiedJobWithAccessToken> {
        return db.paginateV2(
            securityPrincipal,
            pagination,
            create = { session ->
                val isAdmin =
                    if (project == null) false
                    else projectCache.retrieveRole(securityPrincipal.username, project)?.isAdmin() == true

                val isSystem = securityPrincipal == Actor.System

                session
                    .sendPreparedStatement(
                        {
                            setParameter("username", securityPrincipal.username)
                            setParameter("project", project)
                            setParameter("isAdmin", isAdmin)
                            setParameter("isSystem", isSystem)
                        },

                        """
                            declare c cursor for
                                select * 
                                from app_orchestrator.jobs j
                                where 
                                    (
                                        :isSystem or
                                        (j.launched_by = :username and project is null or j.project = :project::text) or
                                        (:isAdmin and :project::text is not null and j.project = :project::text)
                                    )
                                order by
                                    last_update desc
                        """
                    )
            },
            mapper = { session, rows -> mapJobs(rows, flags, session) }
        )
    }

    private suspend fun mapJobs(
        rows: ResultSet,
        flags: JobDataIncludeFlags,
        session: AsyncDBConnection,
    ): List<VerifiedJobWithAccessToken> {
        val tokens = rows
            .map { it.getField(JobsTable.id) to it.getField(JobsTable.refreshToken) }
            .associateBy { it.first }
        var jobs = rows.map { it.toJob() }
        val jobIds = jobs.map { it.id }

        if (flags.includeParameters == true) {
            val resources = session
                .sendPreparedStatement(
                    { setParameter("ids", jobIds) },
                    """
                        select *
                        from job_resources
                        where job_id in (select unnest(:ids::text[]))
                    """
                )
                .rows
                .map { it.toAppResource() }
                .groupBy { it.jobId }

            val params = session
                .sendPreparedStatement(
                    { setParameter("ids", jobIds) },
                    """
                        select *
                        from job_input_parameters
                        where job_id in (select unnest(:ids::text[]))
                    """
                )
                .rows
                .map { it.toAppParameterValue() }
                .groupBy { it.jobId }

            jobs = jobs.map { job ->
                val newParams = job.parameters.copy(
                    parameters = params[job.id]?.asSequence()?.map { it.name to it.value }?.toMap()
                        ?: emptyMap(),
                    resources = resources[job.id]?.map { it.resource } ?: emptyList()
                )

                job.copy(parameters = newParams)
            }
        }

        if (flags.includeUpdates == true) {
            val updates = session
                .sendPreparedStatement(
                    { setParameter("ids", jobIds) },
                    """
                        select *
                        from job_updates
                        where job_id in (select unnest(:ids::text[]))
                    """
                )
                .rows
                .map { it.toJobUpdate() }
                .groupBy { it.jobId }

            jobs = jobs.map { job ->
                job.copy(updates = updates[job.id]?.map { it.update } ?: job.updates)
            }
        }

        if (flags.includeApplication == true) {
            val uniqueApplications = jobs.map { it.parameters.application }.toSet().mapNotNull {
                appStoreCache.apps.get(it)
            }.associateBy { NameAndVersion(it.metadata.name, it.metadata.version) }

            jobs = jobs.map { job ->
                job.copy(
                    parameters = job.parameters
                        .copy(resolvedApplication = uniqueApplications[job.parameters.application])
                )
            }
        }

        if (flags.includeProduct == true) {
            val uniqueMachines = jobs
                .map { it.parameters.product }
                .toSet()
                .map {
                    it to machineTypeCache.find(it.provider, it.id, it.category)
                }
                .toMap()

            jobs = jobs.map { job ->
                job.copy(
                    parameters = job.parameters.copy(resolvedProduct = uniqueMachines[job.parameters.product])
                )
            }
        }

        return jobs.map { VerifiedJobWithAccessToken(it, tokens.getValue(it.id).second) }
    }

    suspend fun retrieve(
        securityPrincipal: Actor,
        project: String?,
        jobId: String,
        flags: JobDataIncludeFlags,
    ): VerifiedJobWithAccessToken {
        return db.withSession { session ->
            val isAdmin =
                if (project == null) false
                else projectCache.retrieveRole(securityPrincipal.username, project)?.isAdmin() == true

            val isSystem = securityPrincipal == Actor.System

            val rows = session
                .sendPreparedStatement(
                    {
                        setParameter("isAdmin", isAdmin)
                        setParameter("project", project)
                        setParameter("jobId", jobId)
                        setParameter("username", securityPrincipal.username)
                        setParameter("isSystem", isSystem)
                    },
                    """
                        select * 
                        from app_orchestrator.jobs j
                        where 
                            j.id = :jobId and
                            (
                                :isSystem or
                                (j.launched_by = :username and project is null or j.project = :project::text) or
                                (:isAdmin and :project::text is not null and j.project = :project::text)
                            )
                    """
                )
                .rows

            mapJobs(rows, flags, session).singleOrNull()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    suspend fun retrievePrivileged(
        ctx: DBContext,
        jobIds: Collection<String>,
        flags: JobDataIncludeFlags,
    ): Map<String, VerifiedJobWithAccessToken> {
        return ctx.withSession { session ->
            mapJobs(
                session
                    .sendPreparedStatement(
                        { setParameter("jobIds", jobIds.toList()) },
                        """
                        select * from app_orchestrator.jobs j
                        where j.id in (select unnest(:jobIds::text[]))
                    """
                    ).rows,
                flags,
                session
            ).associateBy { it.job.id }
        }
    }

    suspend fun findFromUrlId(
        ctx: DBContext,
        urlId: String,
        owner: String?,
    ): VerifiedJobWithAccessToken? {
        TODO()
        /*
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("urlId", urlId)
                        setParameter("username", owner)
                    },

                    """
                        select *
                        from job_information
                        where
                            (:username::text is null or owner = :username) and
                            (system_id = :urlId or url = :urlId) and
                            (state != 'SUCCESS' and state != 'FAILURE')
                    """
                )
                .rows
                .map { it.toVerifiedJob(false, appService) }
                .singleOrNull()
        }
         */
    }

    suspend fun isUrlOccupied(
        ctx: DBContext,
        urlId: String,
    ): Boolean {
        TODO()
        /*
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("urlId", urlId)
                    },

                    """
                        select count(system_id)
                        from job_information
                        where
                            (system_id = :urlId or url = :urlId) and
                            state != 'SUCCESS' and
                            state != 'FAILURE'
                    """
                )
                .rows
                .map { it.getLong(0)!! }
                .singleOrNull() ?: 0L > 0L
        }
        */
    }

    suspend fun canUseUrl(
        ctx: DBContext,
        requestedBy: String,
        urlId: String,
    ): Boolean {
        TODO()
        /*
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("requestedBy", requestedBy)
                        setParameter("urlId", urlId)
                    },

                    """
                        select count(url)
                        from public_links
                        where
                            url = :urlId and
                            username = :requestedBy
                    """
                )
                .rows
                .singleOrNull()
                ?.let { it.getLong(0)!! }  ?: 0L > 0L
        }
         */
    }

    /*
    suspend fun listPublicUrls(
        ctx: DBContext,
        requestedBy: String,
        pagination: NormalizedPaginationRequest
    ): Page<PublicLink> {
        return ctx.withSession { session ->
            val parameters: EnhancedPreparedStatement.() -> Unit = {
                setParameter("requestedBy", requestedBy)
            }

            val itemsInTotal = session
                .sendPreparedStatement(
                    parameters,
                    """
                        select count(pl.url)
                        from 
                            public_links pl
                        where
                            pl.username = :requestedBy
                    """
                )
                .rows
                .singleOrNull()
                ?.let { it.getLong(0)!! } ?: 0L

            val items = session
                .sendPreparedStatement(
                    {
                        parameters()
                        setParameter("limit", pagination.itemsPerPage)
                        setParameter("offset", pagination.offset)
                    },

                    """
                        select url, system_id, name, in_use
                        from
                        (
                            select
                               pl.url,
                               j.system_id,
                               j.name,
                               (j.state is not null and j.state != 'FAILURE' and j.state != 'SUCCESS') as in_use,
                               row_number() over(partition by pl.url order by (j.state is not null and j.state != 'FAILURE' and j.state != 'SUCCESS')) as rn
                           from
                               public_links pl left join job_information j on pl.url = j.url
                           where
                               pl.username = :requestedBy
                        ) t
                        where t.rn = 1
                        order by t.in_use, t.url
                        limit :limit
                        offset :offset;
                    """
                )
                .rows
                .map {
                    val url = it.getString(0)!!
                    val inUseBy = it.getString(1)
                    val inUseByName = it.getString(2)
                    val activelyInUse = it.getBoolean(3)!!

                    PublicLink(
                        url,
                        if (activelyInUse) inUseBy else null,
                        if (activelyInUse) inUseByName ?: inUseBy else null
                    )
                }

            Page(itemsInTotal.toInt(), pagination.itemsPerPage, pagination.page, items)
        }
    }
     */
}

fun RowData.toJob(): Job {
    return Job(
        getField(JobsTable.id),
        JobOwner(
            getField(JobsTable.launchedBy),
            getFieldNullable(JobsTable.project)
        ),
        listOf(
            JobUpdate(
                getField(JobsTable.lastUpdate).toDateTime().millis,
                JobState.valueOf(getField(JobsTable.currentState))
            )
        ),
        JobBilling(
            getField(JobsTable.creditsCharged),
            getField(JobsTable.pricePerUnit)
        ),
        JobParameters(
            NameAndVersion(getField(JobsTable.applicationName), getField(JobsTable.applicationVersion)),
            ComputeProductReference(
                getField(JobsTable.productId),
                getField(JobsTable.productCategory),
                getField(JobsTable.productProvider),
            ),
            getFieldNullable(JobsTable.name),
            getField(JobsTable.replicas),
            true,
            timeAllocation = getFieldNullable(JobsTable.timeAllocationMillis)?.let { SimpleDuration.fromMillis(it) }
        ),
        output = getFieldNullable(JobsTable.outputFolder)?.let { JobOutput(it) },
        status = JobStatus(
            getField(JobsTable.currentState).let { JobState.valueOf(it) },
            getFieldNullable(JobsTable.startedAt)?.toDateTime()?.millis,
            run {
                val startedAt = getFieldNullable(JobsTable.startedAt)?.toDateTime()?.millis
                val timeAllocation = getFieldNullable(JobsTable.timeAllocationMillis)
                if (startedAt != null && timeAllocation != null) {
                    startedAt + timeAllocation
                } else {
                    null
                }
            }
        ),
    )
}

data class SqlAppParameterValue(val jobId: String, val name: String, val value: AppParameterValue)

fun RowData.toAppParameterValue(): SqlAppParameterValue {
    return SqlAppParameterValue(
        getField(JobInputParametersTable.jobId),
        getField(JobInputParametersTable.name),
        defaultMapper.readValue(getField(JobInputParametersTable.value))
    )
}

data class SqlAppResource(val jobId: String, val resource: AppParameterValue)

fun RowData.toAppResource(): SqlAppResource {
    return SqlAppResource(
        getField(JobResourcesTable.jobId),
        defaultMapper.readValue(getField(JobResourcesTable.resource))
    )
}

data class SqlJobUpdate(val jobId: String, val update: JobUpdate)

fun RowData.toJobUpdate(): SqlJobUpdate {
    return SqlJobUpdate(
        getField(JobUpdatesTable.jobId),
        JobUpdate(
            getField(JobUpdatesTable.ts).toDateTime().millis,
            getFieldNullable(JobUpdatesTable.state)?.let { JobState.valueOf(it) },
            getFieldNullable(JobUpdatesTable.status)
        )
    )
}
