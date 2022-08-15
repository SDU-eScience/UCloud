package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.InteractiveSessionType
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import java.util.*
import kotlin.collections.ArrayList

class SessionDao {
    suspend fun createSession(
        ctx: DBContext,
        jobAndRank: JobAndRank,
        type: InteractiveSessionType,
    ): String {
        // NOTE(Dan): Must be cryptographically secure since it will be used as an access token
        val sessionId = UUID.randomUUID().toString()
        val (job, rank) = jobAndRank
        ctx.withSession { session ->
            session
                .prepareStatement(
                    """
                        insert into ucloud_compute_sessions (job_id, rank, session_id, type, expires_at) values (
                            :jobId,
                            :rank,
                            :sessionId,
                            :type,
                            now() + :lifeTime
                        );
                    """
                )
                .useAndInvokeAndDiscard(
                    prepare = {
                        bindString("jobId", job.id)
                        bindInt("rank", rank)
                        bindString("sessionId", sessionId)
                        bindString("type", type.name)
                        bindString("lifeTime", lifeTime(type))
                    }
                )
        }
        return sessionId
    }

    private fun lifeTime(type: InteractiveSessionType) = when (type) {
        InteractiveSessionType.WEB -> "10 hours"
        InteractiveSessionType.VNC -> "10 hours"
        InteractiveSessionType.SHELL -> "5 minutes"
    }

    suspend fun findSessionOrNull(
        ctx: DBContext,
        sessionId: String,
        type: InteractiveSessionType,
    ): JobIdAndRank? {
        return ctx.withSession { session ->
            val rows = ArrayList<JobIdAndRank>()
            session
                .prepareStatement(
                    """
                        update ucloud_compute_sessions
                        set expires_at = now() + :lifeTime
                        where
                            now() <= expires_at and
                            session_id = :sessionId and
                            type = :type
                        returning job_id, rank
                    """
                )
                .useAndInvoke(
                    prepare = {
                        bindString("sessionId", sessionId)
                        bindString("type", type.name)
                        bindString("lifeTime", lifeTime(type))
                    },
                    readRow = { row -> rows.add(JobIdAndRank(row.getString(0)!!, row.getInt(1)!!))}
                )

            rows.singleOrNull()
        }
    }
}
