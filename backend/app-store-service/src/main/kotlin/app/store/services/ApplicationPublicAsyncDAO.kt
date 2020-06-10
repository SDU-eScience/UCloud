package app.store.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.services.ApplicationException
import dk.sdu.cloud.app.store.services.ApplicationTable
import dk.sdu.cloud.app.store.services.PublicDAO
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession

class ApplicationPublicAsyncDAO() : PublicDAO {

    override suspend fun isPublic(
        ctx: DBContext,
        user: SecurityPrincipal,
        appName: String,
        appVersion: String
    ): Boolean {
        val result = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("name", appName)
                    setParameter("version", appVersion)
                },
                """
                    SELECT *
                    FROM applications
                    WHERE (name = ?name) AND (version = ?version)
                """.trimIndent()
            ).rows.singleOrNull() ?: throw ApplicationException.NotFound()

        }
        return result.getField(ApplicationTable.isPublic)
    }

    override suspend fun setPublic(
        ctx: DBContext,
        user: SecurityPrincipal,
        appName: String,
        appVersion: String,
        public: Boolean
    ) {
        if (user.role !in Roles.PRIVILEDGED) throw ApplicationException.NotAllowed()
        val existing = ctx.withSession { session ->
            internalByNameAndVersion(session, appName, appVersion) ?: throw ApplicationException.NotFound()
        }
        if (!canUserPerformWriteOperation(existing.getField(ApplicationTable.owner), user)) throw ApplicationException.NotAllowed()

        ctx.withSession { session ->
            session.sendPreparedStatement(
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
