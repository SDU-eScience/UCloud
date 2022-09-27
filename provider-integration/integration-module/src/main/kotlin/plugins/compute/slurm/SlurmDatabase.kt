package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.sql.*
import kotlinx.serialization.Serializable
import dk.sdu.cloud.FindByStringId

@Serializable
data class SlurmBrowseFlags(
    val filterSlurmId: String? = null,
    val filterUCloudId: String? = null,
    val filterIsActive: Boolean? = null,
)

@Deprecated("Renamed to SlurmDatabase")
val SlurmJobMapper = SlurmDatabase

object SlurmDatabase {
    suspend fun registerJob(
        job: SlurmJob,
        ctx: DBContext = dbConnection
    ) {
        ctx.withSession { session ->
            session.prepareStatement(
                """
                    insert into job_mapping (local_id, ucloud_id, partition, status, lastknown, elapsed)
                    values (:local_id, :ucloud_id, :partition, :status, :last_known, :elapsed)
                """
            ).useAndInvokeAndDiscard {
                bindString("ucloud_id", job.ucloudId)
                bindString("local_id", job.slurmId)
                bindString("partition", job.partition)
                bindInt("status", job.status)
                bindString("last_known", job.lastKnown)
                bindLong("elapsed", job.elapsed)
            }
        }
    }

    suspend fun browse(
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

    suspend fun retrieveBySlurmId(
        slurmId: String,
        ctx: DBContext = dbConnection,
    ): SlurmJob? {
        return browse(SlurmBrowseFlags(filterSlurmId = slurmId), ctx = ctx).firstOrNull()
    }

    suspend fun retrieveByUCloudId(
        ucloudId: String,
        ctx: DBContext = dbConnection,
    ): SlurmJob? {
        return browse(SlurmBrowseFlags(filterUCloudId = ucloudId), ctx = ctx).firstOrNull()
    }

    suspend fun updateElapsedByUCloudId(
        ucloudId: List<String>,
        newElapsed: List<Long>,
        ctx: DBContext = dbConnection,
    ) {
        val table = ucloudId.zip(newElapsed).map { (id, elapsed) ->
            mapOf<String, Any?>(
                "ucloud_id" to id,
                "new_elapsed" to elapsed
            )
        }

        ctx.withSession { session ->
            session.prepareStatement(
                """
                    with update_table as (
                        ${safeSqlTableUpload("update", table)}
                    )
                    update job_mapping as mapping
                    set elapsed = u.new_elapsed
                    from update_table as u
                    where
                        u.ucloud_id = mapping.ucloud_id;
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindTableUpload("update", table)
                }
            )
        }
    }

    suspend fun updateState(
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

    suspend fun markAsInactive(
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

    suspend fun retrieveSession(
        token: FindByStringId,
        ctx: DBContext = dbConnection,
    ): InteractiveSession? {
        val result = ArrayList<InteractiveSession>()

        ctx.withSession { session ->
            session.prepareStatement(
                """
                    select token, rank, ucloud_id
                    from session_mapping
                    where
                        (token = :token)
                """
            ).useAndInvoke(
                prepare = {
                    bindString("token", token.id)
                },
                readRow = { row ->
                    result.add(
                        InteractiveSession(
                            row.getString(0)!!, row.getInt(1)!!, row.getString(2)!!
                        )
                    )
                }
            )
        }

        return result.firstOrNull()
    }

    suspend fun registerSession(
        iSession: InteractiveSession,
        ctx: DBContext = dbConnection
    ) {
        ctx.withSession { session ->
            session.prepareStatement(
                """
                    insert into session_mapping (token, rank, ucloud_id) 
                    values (:token, :rank, :ucloud_id)
                    on conflict do nothing
                """
            ).useAndInvokeAndDiscard {
                bindString("token", iSession.token)
                bindInt("rank", iSession.rank)
                bindString("ucloud_id", iSession.ucloudId)
            }
        }
    }
}
