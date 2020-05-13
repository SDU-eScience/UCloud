package dk.sdu.cloud.app.orchestrator.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.*
import dk.sdu.cloud.app.store.api.AppParametersWithValues
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.ToolReference
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.lang.IllegalArgumentException

object JobInformationTable : SQLTable("job_information") {
    val systemId = text("system_id", notNull = true)
    val owner = text("owner", notNull = true)
    val accessToken = text("access_token")
    val applicationName = text("application_name", notNull = true)
    val applicationVersion = text("application_version", notNull = true)
    val backendName = text("backend_name", notNull = false)
    val files = jsonb("files")
    val parameters = jsonb("parameters")
    val nodes = int("nodes")
    val tasksPerNode = int("tasks_per_node")
    val maxTimeHours = int("max_time_hours")
    val maxTimeMinutes = int("max_time_minutes")
    val maxTimeSeconds = int("max_time_seconds")
    val state = text("state")
    val status = text("status")
    val createdAt = timestamp("created_at")
    val modifiedAt = timestamp("modified_at")
    val archiveInCollection = text("archive_in_collection")
    val mounts = jsonb("mounts")
    val startedAt = timestamp("started_at")
    val username = text("username")
    val peers = jsonb("peers")
    val name = text("name")
    val failedState = text("failed_state")
    val refreshToken = text("refresh_token")
    val reservedCpus = int("reserved_cpus")
    val reservedMemoryInGigs = int("reserved_memory_in_gigs")
    val reservedGpus = int("reserved_gpus")
    val folderId = text("folder_id")
    val reservationType = text("reservation_type")
    val outputFolder = text("output_folder")
    val url = text("url")
    val project = text("project")
}

class JobDao(
    private val appStoreService: AppStoreService,
    private val toolStoreService: ToolStoreService
) {
    suspend fun create(
        ctx: DBContext,
        jobWithToken: VerifiedJobWithAccessToken
    ) {
        ctx.withSession { session ->
            val (job, token, refreshToken) = jobWithToken

            session.insert(JobInformationTable) {
                set(JobInformationTable.systemId, job.id)
                set(JobInformationTable.owner, job.owner)
                set(JobInformationTable.name, job.name)
                set(JobInformationTable.applicationName, job.application.metadata.name)
                set(JobInformationTable.applicationVersion, job.application.metadata.version)
                set(JobInformationTable.status, "Verified")
                set(JobInformationTable.state, job.currentState.name)
                set(JobInformationTable.failedState, job.failedState?.name)
                set(JobInformationTable.nodes, job.nodes)
                set(JobInformationTable.tasksPerNode, job.tasksPerNode)
                set(JobInformationTable.parameters, defaultMapper.writeValueAsString(job.jobInput.asMap()))
                set(JobInformationTable.files, defaultMapper.writeValueAsString(job.files.toList()))
                set(JobInformationTable.mounts, defaultMapper.writeValueAsString(job.mounts.toList()))
                set(JobInformationTable.maxTimeHours, job.maxTime.hours)
                set(JobInformationTable.maxTimeMinutes, job.maxTime.minutes)
                set(JobInformationTable.maxTimeSeconds, job.maxTime.seconds)
                set(JobInformationTable.accessToken, token)
                set(JobInformationTable.archiveInCollection, job.archiveInCollection)
                set(JobInformationTable.backendName, job.backend)
                set(JobInformationTable.startedAt, null)
                set(JobInformationTable.modifiedAt, LocalDateTime.now(DateTimeZone.UTC))
                set(JobInformationTable.createdAt, LocalDateTime.now(DateTimeZone.UTC))
                set(JobInformationTable.peers, defaultMapper.writeValueAsString(job.peers.toList()))
                set(JobInformationTable.refreshToken, refreshToken)
                set(JobInformationTable.reservationType, job.reservation.name)
                set(JobInformationTable.reservedCpus, job.reservation.cpu)
                set(JobInformationTable.reservedMemoryInGigs, job.reservation.memoryInGigs)
                set(JobInformationTable.reservedGpus, job.reservation.gpu)
                set(JobInformationTable.outputFolder, job.outputFolder)
                set(JobInformationTable.url, job.url)
                set(JobInformationTable.project, job.project)
            }
        }
    }

    suspend fun updateStatus(
        ctx: DBContext,
        systemId: String,
        status: String? = null,
        state: JobState? = null,
        failedState: JobState? = null
    ) {
        if (status == null && state == null && failedState == null) {
            throw IllegalArgumentException("No changes are going to be made!")
        }

        ctx.withSession { session ->
            val rowsAffected = session
                .sendPreparedStatement(
                    {
                        setParameter("systemId", systemId)
                        setParameter("status", status)
                        setParameter("failedState", failedState?.name)
                        setParameter("state", state?.name)
                    },
                    """
                        update job_information
                        set
                            modified_at = now(),
                            status = coalesce(?status::text, status),
                            state = coalesce(?state::text, state),
                            failed_state = coalesce(?failedState::text, failed_state),
                            started_at = (case
                                when ?state::text = 'RUNNING' then timezone('utc', now())
                                else started_at
                            end)
                        where
                            system_id = ?systemId
                    """
                )
                .rowsAffected

            if (rowsAffected != 1L) throw JobException.NotFound(systemId)
        }
    }

    suspend fun find(
        ctx: DBContext,
        systemIds: List<String>,
        owner: String?
    ): List<VerifiedJobWithAccessToken> {
        ctx.withSession { session ->
            return session
                .sendPreparedStatement(
                    {
                        setParameter("systemIds", systemIds)
                        setParameter("owner", owner)
                    },
                    """
                        select *
                        from job_information
                        where
                            (?owner::text is null or owner = ?owner) and
                            system_id in (select unnest(?systemIds::text[]))
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
                            created_at < to_timestamp(?createdAt)
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
        ctx.withSession { session ->
            return session
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
                            (?appName::text is null or application_name = ?appName) and
                            (?appVersion::text is null or application_version = ?appVersion) and

                            -- Min timestamp filter
                            (
                                ?minTimestamp::bigint is null or
                                created_at >= to_timestamp(?minTimestamp)
                            ) and

                            -- Max timestamp filter
                            (
                                ?maxTimestamp::bigint is null or
                                created_at <= to_timestamp(?maxTimestamp)
                            ) and

                            -- State filter
                            (
                                ?state::text is null or
                                state = ?state
                            ) and

                            -- Permissions
                            (
                                owner = ?owner or
                                (?isProjectAdmin and ?project::text is not null and project = ?project)
                            )
                    """,
                    """
                        order by
                            (case when ?sortBy = 'CREATED_AT' and ?sortDirection = 'DESCENDING' then created_at end) desc,
                            (case when ?sortBy = 'CREATED_AT' and ?sortDirection = 'ASCENDING' then created_at end) asc,
                            (case when ?sortBy = 'STARTED_AT' and ?sortDirection = 'DESCENDING' then started_at end) desc,
                            (case when ?sortBy = 'STARTED_AT' and ?sortDirection = 'ASCENDING' then started_at end) asc,
                            (case when ?sortBy = 'NAME' and ?sortDirection = 'DESCENDING' then name end) desc,
                            (case when ?sortBy = 'NAME' and ?sortDirection = 'ASCENDING' then name end) asc,
                            (case when ?sortBy = 'LAST_UPDATE' and ?sortDirection = 'DESCENDING' then modified_at end) desc,
                            (case when ?sortBy = 'LAST_UPDATE' and ?sortDirection = 'ASCENDING' then modified_at end) asc,
                            (case when ?sortBy = 'APPLICATION' and ?sortDirection = 'DESCENDING' then application_name end) desc,
                            (case when ?sortBy = 'APPLICATION' and ?sortDirection = 'ASCENDING' then application_name end) asc,
                            (case when ?sortBy = 'STATE' and ?sortDirection = 'DESCENDING' then state end) desc,
                            (case when ?sortBy = 'STATE' and ?sortDirection = 'ASCENDING' then state end) asc
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
        ctx.withSession { session ->
            return session
                .sendPreparedStatement(
                    {
                        setParameter("urlId", urlId)
                        setParameter("username", owner)
                    },

                    """
                        select *
                        from job_information
                        where
                            (?username::text is null or owner = ?username) and
                            (system_id = ?urlId or url = ?urlId) and
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
        ctx.withSession { session ->
            return session
                .sendPreparedStatement(
                    {
                        setParameter("urlId", urlId)
                    },

                    """
                        select count(system_id)
                        from job_information
                        where
                            (system_id = ?urlId or url = ?urlId) and
                            state != 'SUCCESS' and
                            state != 'FAILURE'
                    """
                )
                .rows
                .map { it.getLong(1)!! }
                .singleOrNull() ?: 0L > 0L
        }
    }

    suspend fun deleteJobInformation(
        ctx: DBContext,
        appName: String,
        appVersion: String
    ) {
        ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("appName", appName)
                        setParameter("appVersion", appVersion)
                    },

                    """
                        delete from job_information  
                        where
                            application_name = ?appName and
                            application_version = ?appVersion
                    """
                )
        }
    }

    private suspend fun RowData.toModel(resolveTool: Boolean): VerifiedJobWithAccessToken? {
        with(JobInformationTable) {
            val job = VerifiedJob(
                id = getField(systemId),
                name = getFieldNullable(name),
                owner = getField(owner),
                application = appStoreService.findByNameAndVersion(
                    getField(applicationName),
                    getField(applicationVersion)
                ) ?: return null,
                backend = getField(backendName),
                nodes = getField(nodes),
                maxTime = SimpleDuration(getField(maxTimeHours), getField(maxTimeMinutes), getField(maxTimeSeconds)),
                tasksPerNode = getField(tasksPerNode),
                reservation = MachineReservation(
                    getField(reservationType),
                    getField(reservedCpus),
                    getField(reservedMemoryInGigs),
                    getField(reservedGpus)
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
            val tool =
                toolStoreService.findByNameAndVersion(toolReference.name, toolReference.version)

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

