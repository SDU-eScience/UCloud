package dk.sdu.cloud.app.http

import dk.sdu.cloud.app.api.HPCToolDescriptions
import dk.sdu.cloud.app.services.ToolDAO
import dk.sdu.cloud.auth.api.currentUsername
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class ToolController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val source: ToolDAO<DBSession>
) {
    fun configure(routing: Route) = with(routing) {
        route("tools") {
            implement(HPCToolDescriptions.findByName) { req ->
                logEntry(log, req)
                val result = db.withTransaction {
                    source.findAllByName(
                        it,
                        call.request.currentUsername,
                        req.name,
                        req.pagination
                    )
                }

                ok(result)
            }

            implement(HPCToolDescriptions.findByNameAndVersion) { req ->
                logEntry(log, req)
                val result = db.withTransaction {
                    source.findByNameAndVersion(
                        it,
                        call.request.currentUsername,
                        req.name,
                        req.version
                    )
                }

                ok(result)
            }

            implement(HPCToolDescriptions.listAll) { req ->
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
        private val log = LoggerFactory.getLogger(ToolController::class.java)
    }
}
