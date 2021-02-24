package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession

class ApplicationPublicService(
    private val ctx: DBContext,
    private val applicationPublicAsyncDao: ApplicationPublicAsyncDao
) {
    suspend fun isPublic(
        securityPrincipal: SecurityPrincipal,
        applications: List<NameAndVersion>
    ): Map<NameAndVersion, Boolean> {
        return ctx.withSession { session ->
            applications.map { app ->
                Pair<NameAndVersion, Boolean>(
                    NameAndVersion(app.name, app.version),
                    applicationPublicAsyncDao.isPublic(
                        session,
                        securityPrincipal,
                        app.name,
                        app.version
                    )
                )
            }.toMap()
        }
    }

    suspend fun setPublic(
        securityPrincipal: SecurityPrincipal,
        appName: String,
        appVersion: String,
        public: Boolean
    ) {
        ctx.withSession {
            applicationPublicAsyncDao.setPublic(
                it,
                securityPrincipal,
                appName,
                appVersion,
                public
            )
        }
    }

}
