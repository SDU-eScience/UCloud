package dk.sdu.cloud.app.orchestrator.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jasync.sql.db.ResultSet
import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Actor
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromString

@Suppress("SqlResolve")
class JobQueryService(
    private val db: AsyncDBSessionFactory,
    private val projectCache: ProjectCache,
    private val appStoreCache: AppStoreCache,
    private val productCache: ProductCache,
    private val providerSupport: ProviderSupportService,
) {
    suspend fun browse(
        securityPrincipal: Actor,
        project: String?,
        pagination: NormalizedPaginationRequestV2,
        flags: JobDataIncludeFlags,
        filters: JobFilters? = null,
        sortBy: JobsSortBy? = null,
    ): PageV2<VerifiedJobWithAccessToken> {
        val sortByOrDefault = sortBy ?: JobsSortBy.CREATED_AT

        return db.paginateV2(
            securityPrincipal,
            pagination,
            create = { session ->
                val projectRole =
                    if (project != null) projectCache.retrieveRole(securityPrincipal.username, project)
                    else null

                val isAdmin = projectRole?.isAdmin() == true
                val isMember = projectRole != null

                val isSystem = securityPrincipal == Actor.System

                session
                    .sendPreparedStatement(
                        {
                            setParameter("username", securityPrincipal.username)
                            setParameter("project", project)
                            setParameter("isAdmin", isAdmin)
                            setParameter("isMember", isMember)
                            setParameter("isSystem", isSystem)

                            setParameter("filterApplication", filters?.filterApplication)
                            setParameter("filterLaunchedBy", filters?.filterLaunchedBy)
                            setParameter("filterState", filters?.filterState?.name)
                            setParameter("filterTitle", filters?.filterTitle)
                            setParameter("filterAfter", filters?.filterAfter)
                            setParameter("filterBefore", filters?.filterBefore)

                            setParameter("sortBy", sortByOrDefault.name)
                        },

                        """
                            declare c cursor for
                                select * 
                                from app_orchestrator.jobs j
                                where 
                                    (
                                        :isSystem or
                                        (
                                            j.launched_by = :username and 
                                            (
                                                (project is null and :project::text is null) or 
                                                (j.project = :project::text and :isMember)
                                            )
                                        ) or
                                        (:isAdmin and :project::text is not null and j.project = :project::text)
                                    ) and
                                    (
                                        :filterApplication::text is null or
                                        j.application_name = :filterApplication
                                    ) and
                                    (
                                        :filterLaunchedBy::text is null or
                                        j.launched_by = :filterLaunchedBy
                                    ) and
                                    (
                                        :filterState::text is null or
                                        j.current_state = :filterState
                                    ) and
                                    (
                                        :filterTitle::text is null or
                                        j.name ilike '%' || :filterTitle || '%'
                                    ) and
                                    (
                                        :filterBefore::bigint is null or
                                        j.started_at < to_timestamp(:filterBefore::bigint / 1000)
                                    ) and
                                    (
                                        :filterAfter::bigint is null or
                                        j.started_at > to_timestamp(:filterAfter::bigint / 1000)
                                    )
                                order by
                                    case :sortBy::text
                                        when 'STATE' then j.current_state
                                        when 'APPLICATION' then j.application_name
                                    end
                                    desc,
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
                val newParams = job.specification.copy(
                    parameters = params[job.id]?.asSequence()?.map { it.name to it.value }?.toMap()
                        ?: emptyMap(),
                    resources = resources[job.id]?.map { it.resource } ?: emptyList()
                )

                job.copy(specification = newParams)
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
            val uniqueApplications = jobs.map { it.specification.application }.toSet().mapNotNull {
                appStoreCache.apps.get(it)
            }.associateBy { NameAndVersion(it.metadata.name, it.metadata.version) }

            jobs = jobs.map { job ->
                job.copy(
                    specification = job.specification
                        .copy(resolvedApplication = uniqueApplications[job.specification.application])
                )
            }
        }

        if (flags.includeProduct == true) {
            val uniqueMachines = jobs
                .map { it.specification.product }
                .toSet()
                .map {
                    it to productCache.find<Product.Compute>(it.provider, it.id, it.category)
                }
                .toMap()

            jobs = jobs.map { job ->
                job.copy(
                    specification = job.specification.copy(resolvedProduct = uniqueMachines[job.specification.product])
                )
            }
        }

        if (flags.includeSupport == true) {
            val providers = jobs.map { it.specification.product.provider }.toSet()
            val productsWithSupport = providerSupport.retrieveProducts(providers).productsByProvider
            jobs = jobs.map { job ->
                job.copy(specification = job.specification.copy(
                    resolvedSupport = productsWithSupport[job.specification.product.provider]
                        ?.find {
                            it.product.id == job.specification.product.id &&
                                it.product.category.id == job.specification.product.category
                        }
                        ?.support
                ))
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
            val projectRole =
                if (project != null) projectCache.retrieveRole(securityPrincipal.username, project)
                else null

            val isAdmin = projectRole?.isAdmin() == true
            val isMember = projectRole != null

            val isSystem = securityPrincipal == Actor.System

            val rows = session
                .sendPreparedStatement(
                    {
                        setParameter("isAdmin", isAdmin)
                        setParameter("isMember", isMember)
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
                                (
                                    j.launched_by = :username and 
                                    (
                                        (project is null and :project::text is null) or 
                                        (j.project = :project::text and :isMember)
                                    )
                                ) or
                                (:isAdmin and project is not null and j.project = :project::text)
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
            getField(JobsTable.pricePerUnit),
            0L
        ),
        JobSpecification(
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
        createdAt = getField(JobsTable.createdAt).toDateTime().millis
    )
}

data class SqlAppParameterValue(val jobId: String, val name: String, val value: AppParameterValue)

fun RowData.toAppParameterValue(): SqlAppParameterValue {
    return SqlAppParameterValue(
        getField(JobInputParametersTable.jobId),
        getField(JobInputParametersTable.name),
        defaultMapper.decodeFromString(getField(JobInputParametersTable.value))
    )
}

data class SqlAppResource(val jobId: String, val resource: AppParameterValue)

fun RowData.toAppResource(): SqlAppResource {
    return SqlAppResource(
        getField(JobResourcesTable.jobId),
        defaultMapper.decodeFromString(getField(JobResourcesTable.resource))
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
