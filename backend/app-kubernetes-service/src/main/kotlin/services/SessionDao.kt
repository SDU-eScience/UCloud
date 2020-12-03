package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.orchestrator.api.InteractiveSessionType
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import java.util.*

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
                .sendPreparedStatement(
                    {
                        setParameter("jobId", job.id)
                        setParameter("rank", rank)
                        setParameter("sessionId", sessionId)
                        setParameter("type", type.name)
                        setParameter("lifeTime", lifeTime(type))
                    },
                    """
                        insert into sessions (job_id, rank, session_id, type, expires_at) values (
                            :jobId,
                            :rank,
                            :sessionId,
                            :type,
                            now() + :lifeTime
                        );
                    """
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
            session
                .sendPreparedStatement(
                    {
                        setParameter("sessionId", sessionId)
                        setParameter("type", type.name)
                        setParameter("lifeTime", lifeTime(type))
                    },
                    """
                        update sessions
                        set expires_at = now() + :lifeTime
                        where
                            now() <= expires_at and
                            session_id = :sessionId and
                            type = :type
                        returning job_id, rank
                    """
                )
                .rows
                .map { JobIdAndRank(it.getString(0)!!, it.getInt(1)!!) }
                .singleOrNull()
        }
    }
}
