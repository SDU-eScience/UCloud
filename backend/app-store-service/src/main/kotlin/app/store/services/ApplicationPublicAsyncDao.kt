package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.*
import dk.sdu.cloud.service.db.async.*

class ApplicationPublicAsyncDao() {
    suspend fun isPublic(
        ctx: DBContext,
        user: SecurityPrincipal,
        appName: String,
        appVersion: String
    ): Boolean {
        val result = ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("name", appName)
                        setParameter("version", appVersion)
                    },
                    """
                        SELECT *
                        FROM applications
                        WHERE (name = ?name) AND (version = ?version)
                    """.trimIndent()
                )
                .rows
                .singleOrNull() ?: throw ApplicationException.NotFound()

        }
        return result.getField(ApplicationTable.isPublic)
    }

    suspend fun setPublic(
        ctx: DBContext,
        actorAndProject: ActorAndProject,
        appName: String,
        appVersion: String,
        public: Boolean
    ) {
        ctx.withSession { session ->
            internalByNameAndVersion(session, appName, appVersion) ?: throw ApplicationException.NotFound()
        }

        ctx.withSession { session ->
            verifyAppUpdatePermission(actorAndProject, session, appName, appVersion)

            session
                .sendPreparedStatement(
                    {
                        setParameter("public", public)
                        setParameter("name", appName)
                        setParameter("version", appVersion)
                    },
                    """
                        UPDATE applications
                        SET is_public = ?public
                        WHERE (name = ?name) AND (version = ?version)
                    """.trimIndent()
              )
        }
    }
}
