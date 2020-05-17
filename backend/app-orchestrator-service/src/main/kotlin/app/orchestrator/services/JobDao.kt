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

class JobDao {
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


}
