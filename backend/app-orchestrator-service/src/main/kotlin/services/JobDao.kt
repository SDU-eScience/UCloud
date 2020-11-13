package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlin.IllegalArgumentException

object JobsTable : SQLTable("jobs") {
    val id = text("id")
    val launchedBy = text("launched_by")
    val project = text("project")
    val refreshToken = text("refreshToken")
    val applicationName = text("application_name")
    val applicationVersion = text("application_version")
    val pricePerUnit = long("price_per_unit")
    val timeAllocationMillis = long("time_allocation_millis")
    val creditsCharged = long("credits_charged")
    val productProvider = text("product_provider")
    val productCategory = text("product_category")
    val productId = text("product_id")
    val replicas = int("replicas")
    val name = text("name")
    val outputFolder = text("output_folder")
}

object JobUpdatesTable : SQLTable("job_updates") {
    val jobId = text("job_id")
    val ts = timestamp("ts")
    val state = text("state")
    val status = text("status")
}

object JobInputParametersTable : SQLTable("job_input_parameters") {
    val jobId = text("job_id")
    val name = text("name")
    val value = jsonb("value")
}

object JobResourcesTable : SQLTable("job_resources") {
    val jobId = text("job_id")
    val resource = jsonb("resource")
}

class JobDao {
    suspend fun create(
        ctx: DBContext,
        jobWithToken: VerifiedJobWithAccessToken
    ) {
        ctx.withSession { session ->
            val (job, refreshToken) = jobWithToken
            val parameters = job.parameters ?: error("no parameters")

            session.insert(JobsTable) {
                set(JobsTable.id, job.id)
                set(JobsTable.launchedBy, job.owner.launchedBy)
                set(JobsTable.project, job.owner.project)
                set(JobsTable.refreshToken, refreshToken)
                set(JobsTable.applicationName, parameters.application.name)
                set(JobsTable.applicationVersion, parameters.application.version)
                set(JobsTable.pricePerUnit, job.billing.pricePerUnit)
                set(JobsTable.timeAllocationMillis, parameters.timeAllocation?.toMillis())
                set(JobsTable.creditsCharged, job.billing.creditsCharged)
                set(JobsTable.productProvider, parameters.product.provider)
                set(JobsTable.productCategory, parameters.product.category)
                set(JobsTable.productId, parameters.product.id)
                set(JobsTable.replicas, parameters.replicas)
                set(JobsTable.name, parameters.name)
                set(JobsTable.outputFolder, job.output?.outputFolder)
            }

            for (update in job.updates) {
                session.insert(JobUpdatesTable) {
                    set(JobUpdatesTable.jobId, job.id)
                    set(JobUpdatesTable.state, update.state?.name)
                    set(JobUpdatesTable.status, update.status)
                }
            }

            for (resource in parameters.resources) {
                session.insert(JobResourcesTable) {
                    set(JobResourcesTable.jobId, job.id)
                    set(JobResourcesTable.resource, defaultMapper.writeValueAsString(resource))
                }
            }

            for ((param, value) in parameters.parameters) {
                session.insert(JobInputParametersTable) {
                    set(JobInputParametersTable.jobId, job.id)
                    set(JobInputParametersTable.name, param)
                    set(JobInputParametersTable.value, defaultMapper.writeValueAsString(value))
                }
            }
        }
    }

    suspend fun insertUpdate(
        ctx: DBContext,
        jobId: String,
        timestamp: Long,
        state: JobState?,
        status: String?
    ) {
        if (state == null && status == null) {
            throw IllegalArgumentException("Must supply at least one change")
        }

        ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("jobId", jobId)
                        setParameter("state", state?.name)
                        setParameter("status", status)
                        setParameter("ts", timestamp)
                    },

                    """
                        insert into job_updates values (
                            :jobId, 
                            to_timestamp(:timestamp / 1000) at time zone 'UTC', 
                            :state::text, 
                            :status::text
                        )
                    """
                )
        }
    }

    suspend fun updateCreditsCharged(
        ctx: DBContext,
        jobId: String,
        creditsCharged: Long
    ) {
        ctx.withSession { session ->
            val found = session
                .sendPreparedStatement(
                    {
                        setParameter("jobId", jobId)
                        setParameter("creditsCharged", creditsCharged)
                    },

                    """
                        update jobs
                        set
                            credits_charged = :creditsCharged::bigint + credits_charged
                        where
                            id = :jobId
                    """
                )
                .rowsAffected != 1L

            if (!found) throw JobException.NotFound(jobId)
        }
    }

    suspend fun deleteJobInformation(
        ctx: DBContext,
        appName: String,
        appVersion: String
    ) {
        TODO("we probably still want a record of the job")
        ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("appName", appName)
                        setParameter("appVersion", appVersion)
                    },

                    """
                        delete from jobs
                        where
                            application_name = :appName and
                            application_version = :appVersion
                    """
                )
        }
    }

    suspend fun updateMaxTime(ctx: DBContext, jobId: String, maxTime: SimpleDuration) {
        ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("jobId", jobId)
                        setParameter("millis", maxTime.toMillis())
                    },
                    """
                        update jobs
                        set 
                            time_allocation_millis = :millis
                        where
                            id = :jobId
                    """
                )
        }
    }
}
