package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import java.util.*

class ShellSessionDao {
    suspend fun createSession(
        ctx: DBContext,
        job: Job,
    ): String {
        // NOTE(Dan): Must be cryptographically secure since it will be used as an access token
        val sessionId = UUID.randomUUID().toString()
        ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("jobId", job.id)
                        setParameter("sessionId", sessionId)
                    },
                    """
                        insert into shell_sessions (job_id, session_id, expires_at) values (
                            :jobId,
                            :sessionId,
                            now() + '5 minutes'
                        );
                    """
                )
        }
        return sessionId
    }

    suspend fun findSessionOrNull(
        ctx: DBContext,
        sessionId: String,
    ): String? {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("sessionId", sessionId)
                    },
                    """
                        select job_id 
                        from shell_sessions
                        where
                            now() <= expires_at and
                            session_id = :sessionId
                    """
                )
                .rows
                .map { it.getString(0)!! }
                .singleOrNull()
        }
    }
}
