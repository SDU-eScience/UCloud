package dk.sdu.cloud.app.orchestrator.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.ToolReference
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.service.offset
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime

class JobQueryService(
    private val db: DBContext,
    private val jobFileService: JobFileService,
    private val projectCache: ProjectCache,
    private val appService: ApplicationService,
    private val publicLinks: PublicLinkService
) {
    suspend fun findById(
        user: SecurityPrincipalToken,
        jobId: String
    ): VerifiedJobWithAccessToken {
        // TODO This function is the real function to use if you want ACLs. Clarify this design!
        val verifiedJobWithToken = find(db, listOf(jobId), null).singleOrNull()
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        // We just validate that we can view the entries from this (since we know which job to look for concretely)
        if (verifiedJobWithToken.job.owner != user.principal.username) {
            val project = verifiedJobWithToken.job.project ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            val role = projectCache.retrieveRole(user.principal.username, project)
            if (role?.isAdmin() != true) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }
        }

        return verifiedJobWithToken
    }

    suspend fun listRecent(
        user: SecurityPrincipalToken,
        pagination: NormalizedPaginationRequest,
        query: JobQuery,
        projectContext: ProjectContext? = null
    ): Page<JobWithStatus> {
        // We pass the project context eagerly to the list
        return list(
            db,
            user.principal.username,
            pagination,
            query,
            projectContext
        ).mapItems { asJobWithStatus(it) }
    }

    suspend fun asJobWithStatus(verifiedJobWithAccessToken: VerifiedJobWithAccessToken): JobWithStatus {
        val (job) = verifiedJobWithAccessToken
        val startedAt = job.startedAt
        val expiresAt = startedAt?.let {
            startedAt + job.maxTime.toMillis()
        }

        return JobWithStatus(
            job.id,
            job.name,
            job.owner,
            job.currentState,
            job.status,
            job.failedState,
            job.createdAt,
            job.modifiedAt,
            expiresAt,
            job.maxTime.toMillis(),
            jobFileService.jobFolder(verifiedJobWithAccessToken),
            job.application.metadata
        )
    }

    suspend fun find(
        ctx: DBContext,
        systemIds: List<String>,
        owner: String?
    ): List<VerifiedJobWithAccessToken> {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("systemIds", systemIds)
                        setParameter("owner", owner)
                    },
                    """
                        select *
                        from job_information
                        where
                            (:owner::text is null or owner = :owner) and
                            system_id in (select unnest(:systemIds::text[]))
                    """
                )
                .rows
                .mapNotNull { it.toModel(resolveTool = true) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun findJobsCreatedBefore(
        ctx: DBContext,
        timestamp: Long
    ): Flow<VerifiedJobWithAccessToken> {
        return channelFlow {
            ctx.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("createdAt", timestamp / 1000)
                    },

                    """
                        declare c no scroll cursor for 
                        select *
                        from job_information
                        where
                            state != 'SUCCESS' and
                            state != 'FAILURE' and
                            created_at < to_timestamp(:createdAt)
                    """
                )

                while (true) {
                    val rows = session.sendQuery("fetch forward 100 from c").rows
                    rows.mapNotNull { it.toModel(resolveTool = true) }.forEach { send(it) }
                    if (rows.isEmpty()) break
                }
            }
        }
    }

    suspend fun list(
        ctx: DBContext,
        owner: String,
        pagination: NormalizedPaginationRequest,
        query: JobQuery,
        projectContext: ProjectContext? = null
    ): Page<VerifiedJobWithAccessToken> {
        return ctx.withSession { session ->
            session
                .paginatedQuery(
                    pagination,
                    {
                        setParameter("owner", owner)
                        setParameter("isProjectAdmin", projectContext?.role?.isAdmin() == true)
                        setParameter("project", projectContext?.project)
                        setParameter("sortBy", query.sortBy.name)
                        setParameter("sortDirection", query.order.name)
                        setParameter("appName", query.application)
                        setParameter("appVersion", query.version)
                        setParameter("minTimestamp", query.minTimestamp?.let { it / 1000 })
                        setParameter("maxTimestamp", query.maxTimestamp?.let { it / 1000 })
                        setParameter("state", query.filter?.name)
                    },

                    """
                        from job_information
                        where
                            -- Application filter
                            (:appName::text is null or application_name = :appName) and
                            (:appVersion::text is null or application_version = :appVersion) and

                            -- Min timestamp filter
                            (
                                :minTimestamp::bigint is null or
                                created_at >= to_timestamp(:minTimestamp)
                            ) and

                            -- Max timestamp filter
                            (
                                :maxTimestamp::bigint is null or
                                created_at <= to_timestamp(:maxTimestamp)
                            ) and

                            -- State filter
                            (
                                :state::text is null or
                                state = :state
                            ) and

                            -- Permissions
                            (
                                (owner = :owner and :project::text is null) or
                                (:isProjectAdmin and :project::text is not null and project = :project)
                            )
                    """,
                    """
                        order by
                            (case when :sortBy = 'CREATED_AT' and :sortDirection = 'DESCENDING' then created_at end) desc,
                            (case when :sortBy = 'CREATED_AT' and :sortDirection = 'ASCENDING' then created_at end) asc,
                            (case when :sortBy = 'STARTED_AT' and :sortDirection = 'DESCENDING' then started_at end) desc,
                            (case when :sortBy = 'STARTED_AT' and :sortDirection = 'ASCENDING' then started_at end) asc,
                            (case when :sortBy = 'NAME' and :sortDirection = 'DESCENDING' then name end) desc,
                            (case when :sortBy = 'NAME' and :sortDirection = 'ASCENDING' then name end) asc,
                            (case when :sortBy = 'LAST_UPDATE' and :sortDirection = 'DESCENDING' then modified_at end) desc,
                            (case when :sortBy = 'LAST_UPDATE' and :sortDirection = 'ASCENDING' then modified_at end) asc,
                            (case when :sortBy = 'APPLICATION' and :sortDirection = 'DESCENDING' then application_name end) desc,
                            (case when :sortBy = 'APPLICATION' and :sortDirection = 'ASCENDING' then application_name end) asc,
                            (case when :sortBy = 'STATE' and :sortDirection = 'DESCENDING' then state end) desc,
                            (case when :sortBy = 'STATE' and :sortDirection = 'ASCENDING' then state end) asc
                    """
                )
                .mapItemsNotNull { it.toModel(false) }
        }
    }

    suspend fun findFromUrlId(
        ctx: DBContext,
        urlId: String,
        owner: String?
    ): VerifiedJobWithAccessToken? {
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
                .map { it.toModel(false) }
                .singleOrNull()
        }
    }

    suspend fun isUrlOccupied(
        ctx: DBContext,
        urlId: String
    ): Boolean {
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
    }

    suspend fun canUseUrl(
        ctx: DBContext,
        requestedBy: String,
        urlId: String
    ): Boolean {
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
    }

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

    private suspend fun RowData.toModel(resolveTool: Boolean): VerifiedJobWithAccessToken? {
        with(JobInformationTable) {
            val job = VerifiedJob(
                id = getField(systemId),
                name = getFieldNullable(name),
                owner = getField(owner),
                application = appService.apps.get(
                    NameAndVersion(
                        getField(applicationName),
                        getField(applicationVersion)
                    )
                ) ?: return null,
                backend = getField(backendName),
                nodes = getField(nodes),
                maxTime = SimpleDuration(getField(maxTimeHours), getField(maxTimeMinutes), getField(maxTimeSeconds)),
                tasksPerNode = getField(tasksPerNode),
                reservation = Product.Compute(
                    getField(reservationType),
                    getField(reservedPricePerUnit),
                    ProductCategoryId(getField(reservedCategory), getField(reservedProvider)),
                    cpu = getField(reservedCpus),
                    memoryInGigs = getField(reservedMemoryInGigs),
                    gpu = getField(reservedGpus)
                ),
                jobInput = VerifiedJobInput(
                    defaultMapper.readValue(getField(parameters))
                ),
                files = defaultMapper.readValue(getField(files)),
                _mounts = getFieldNullable(mounts)?.let { defaultMapper.readValue<Set<ValidatedFileForUpload>>(it) },
                _peers = getFieldNullable(peers)?.let { defaultMapper.readValue<Set<ApplicationPeer>>(it) },
                currentState = getField(state).let { JobState.valueOf(it) },
                failedState = getFieldNullable(failedState)?.let { JobState.valueOf(it) },
                status = getField(status),
                archiveInCollection = getField(archiveInCollection),
                outputFolder = getFieldNullable(outputFolder),
                createdAt = getField(createdAt).toTimestamp(),
                modifiedAt = getField(modifiedAt).toTimestamp(),
                startedAt = getFieldNullable(startedAt)?.toTimestamp(),
                url = getFieldNullable(url),
                project = getFieldNullable(project)
            )

            val withoutTool = VerifiedJobWithAccessToken(
                job,
                getFieldNullable(accessToken),
                getFieldNullable(refreshToken)
            )

            if (!resolveTool) return withoutTool

            val toolReference = withoutTool.job.application.invocation.tool
            val tool = appService.tools.get(NameAndVersion(toolReference.name, toolReference.version))

            return withoutTool.copy(
                job = withoutTool.job.copy(
                    application = withoutTool.job.application.copy(
                        invocation = withoutTool.job.application.invocation.copy(
                            tool = ToolReference(toolReference.name, toolReference.version, tool)
                        )
                    )
                )
            )
        }
    }
}

fun LocalDateTime.toTimestamp(): Long = toDateTime(DateTimeZone.UTC).millis

private inline fun <T, R : Any> Page<T>.mapItemsNotNull(mapper: (T) -> R?): Page<R> {
    val newItems = items.mapNotNull(mapper)
    return Page(
        itemsInTotal,
        itemsPerPage,
        pageNumber,
        newItems
    )
}

