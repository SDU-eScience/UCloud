package dk.sdu.cloud.app.http

import dk.sdu.cloud.app.api.HPCApplicationDescriptions
import dk.sdu.cloud.app.services.ApplicationDAO
import dk.sdu.cloud.auth.api.currentUsername
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class AppController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val source: ApplicationDAO<DBSession>
) {
    fun configure(routing: Route) = with(routing) {
        route("apps") {
            implement(HPCApplicationDescriptions.findByNameAndVersion) { req ->
                logEntry(log, req)

                val app = db.withTransaction {
                    source.findByNameAndVersion(
                        it,
                        call.request.currentUsername,
                        req.name,
                        req.version
                    )
                }

                ok(app)
            }

            implement(HPCApplicationDescriptions.findByName) { req ->
                logEntry(log, req)

                val result = db.withTransaction {
                    source.findAllByName(it, call.request.currentUsername, req.name, req.pagination)
                }

                ok(result)
            }

            implement(HPCApplicationDescriptions.listAll) { req ->
                logEntry(log, req)

                ok(
                    db.withTransaction {
                        source.listLatestVersion(it, call.request.currentUsername, req.normalize())
                    }
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AppController::class.java)
    }
}
