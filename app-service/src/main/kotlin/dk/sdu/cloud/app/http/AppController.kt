package dk.sdu.cloud.app.http

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.ApplicationDescription
import dk.sdu.cloud.app.api.HPCApplicationDescriptions
import dk.sdu.cloud.app.services.ApplicationDAO
import dk.sdu.cloud.app.util.yamlMapper
import dk.sdu.cloud.auth.api.PRIVILEGED_ROLES
import dk.sdu.cloud.auth.api.currentUsername
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.reader.ReaderException

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

            implement(HPCApplicationDescriptions.create) { req ->
                logEntry(log, req)
                if (!protect(PRIVILEGED_ROLES)) return@implement

                val content = try {
                    call.receiveText()
                } catch (ex: Exception) {
                    error(CommonErrorMessage("Bad request"), HttpStatusCode.BadRequest)
                    return@implement
                }

                @Suppress("DEPRECATION")
                val yamlDocument = try {
                    yamlMapper.readValue<ApplicationDescription>(content)
                } catch (ex: JsonMappingException) {
                    error(
                        CommonErrorMessage(
                            "Bad value for parameter ${ex.pathReference.replace(
                                "dk.sdu.cloud.app.api.",
                                ""
                            )}. ${ex.message}"
                        ),
                        HttpStatusCode.BadRequest
                    )
                    return@implement
                } catch (ex: MarkedYAMLException) {
                    log.debug(ex.stackTraceToString())
                    error(CommonErrorMessage("Invalid YAML document"), HttpStatusCode.BadRequest)
                    return@implement
                } catch (ex: ReaderException) {
                    error(
                        CommonErrorMessage("Document contains illegal characters (unicode?)"),
                        HttpStatusCode.BadRequest
                    )
                    return@implement
                }

                db.withTransaction {
                    source.create(it, call.request.currentUsername, yamlDocument.normalize(), content)
                }

                ok(Unit)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AppController::class.java)
    }
}
