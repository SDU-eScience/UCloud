package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.sql.*
import kotlinx.serialization.Serializable

@Serializable
data class SlurmBrowseFlags(
    val filterSlurmId: String? = null,
    val filterUCloudId: String? = null,
    val filterIsActive: Boolean? = null,
)

object SlurmJobMapper {
    fun registerJob(
        job: SlurmJob,
        ctx: DBContext = dbConnection
    ) {
        ctx.withSession { session ->
            session.prepareStatement(
                """
                    insert into job_mapping (local_id, ucloud_id, partition, status, lastknown) 
                    values (:local_id, :ucloud_id, :partition, :status, :last_known)
                """
            ).useAndInvokeAndDiscard {
                bindString("ucloud_id", job.ucloudId)
                bindString("local_id", job.slurmId)
                bindString("partition", job.partition)
                bindInt("status", job.status)
                bindString("last_known", job.lastKnown)
            }
        }
    }

    fun browse(
        flags: SlurmBrowseFlags,
        ctx: DBContext = dbConnection,
    ): List<SlurmJob> = with(flags) {
        val result = ArrayList<SlurmJob>()
        ctx.withSession { session ->
            session.prepareStatement(
                """
                    select ucloud_id, local_id, partition, lastknown, status
                    from job_mapping
                    where
                        (:filter_slurm_id is null or local_id = :filter_slurm_id) and
                        (:filter_is_active is null or status = :filter_is_active) and
                        (:filter_ucloud_id is null or ucloud_id = :filter_ucloud_id)
                """
            ).useAndInvoke(
                prepare = {
                    bindStringNullable("filter_slurm_id", filterSlurmId)
                    bindBooleanNullable("filter_is_active", filterIsActive)
                    bindStringNullable("filter_ucloud_id", filterUCloudId)
                },
                readRow = { row ->
                    result.add(
                        SlurmJob(
                            row.getString(0)!!, row.getString(1)!!, row.getString(2)!!, row.getString(3)!!,
                            row.getInt(4)!!
                        )
                    )
                }
            )
        }
        return result
    }

    fun retrieveBySlurmId(
        slurmId: String,
        ctx: DBContext = dbConnection,
    ): SlurmJob? {
        return browse(SlurmBrowseFlags(filterSlurmId = slurmId), ctx = ctx).firstOrNull()
    }

    fun retrieveByUCloudId(
        ucloudId: String,
        ctx: DBContext = dbConnection,
    ): SlurmJob? {
        return browse(SlurmBrowseFlags(filterUCloudId = ucloudId), ctx = ctx).firstOrNull()
    }

    fun updateState(
        slurmIds: List<String>,
        newState: List<JobState>,
        ctx: DBContext = dbConnection,
    ) {
        val table = slurmIds.zip(newState).map { (slurmId, newState) ->
            mapOf<String, Any?>(
                "slurm_id" to slurmId,
                "new_state" to newState.name
            )
        }

        ctx.withSession { session ->
            session.prepareStatement(
                """
                    with update_table as (
                        ${safeSqlTableUpload("update", table)}
                    )
                    update job_mapping as mapping
                    set lastknown = u.new_state
                    from update_table as u
                    where
                        u.slurm_id = mapping.local_id;
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindTableUpload("update", table)
                }
            )
        }
    }

    fun markAsInactive(
        slurmIds: List<String>,
        ctx: DBContext = dbConnection,
    ) {
        ctx.withSession { session ->
            session.prepareStatement(
                """
                    update job_mapping
                    set status = false 
                    where
                        local_id in (${safeSqlParameterList("job_id", slurmIds)})
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindParameterList("job_id", slurmIds)
                }
            )
        }
    }
}
