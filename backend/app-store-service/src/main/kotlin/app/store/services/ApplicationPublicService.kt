package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession

class ApplicationPublicService(
    private val db: DBContext,
) {
    suspend fun isPublic(
        actorAndProject: ActorAndProject,
        applications: List<NameAndVersion>
    ): Map<NameAndVersion, Boolean> {
        return db.withSession { session ->
            applications.associate { app ->
                Pair(
                    NameAndVersion(app.name, app.version),
                    isPublic(session, actorAndProject, app.name, app.version)
                )
            }
        }
    }

    suspend fun isPublic(session: AsyncDBConnection, actorAndProject: ActorAndProject, appName: String, appVersion: String): Boolean {
        return session.sendPreparedStatement(
            {
                setParameter("name", appName)
                setParameter("version", appVersion)
            },
            """
                SELECT *
                FROM applications
                WHERE (name = :name) AND (version = :version)
            """.trimIndent()
        ).rows.singleOrNull()?.getBoolean("is_public") ?: false
    }

    suspend fun setPublic(
        actorAndProject: ActorAndProject,
        appName: String,
        appVersion: String,
        public: Boolean
    ) {
        db.withSession { session ->
            internalByNameAndVersion(session, appName, appVersion) ?: throw ApplicationException.NotFound()
            verifyAppUpdatePermission(actorAndProject, session, appName, appVersion)

            session.sendPreparedStatement(
                {
                    setParameter("public", public)
                    setParameter("name", appName)
                    setParameter("version", appVersion)
                },
                """
                    UPDATE applications
                    SET is_public = :public
                    WHERE (name = :name) AND (version = :version)
                """.trimIndent()
            )
        }
    }
}
