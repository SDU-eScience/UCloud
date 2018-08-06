package dk.sdu.cloud.app.http

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.HPCToolDescriptions
import dk.sdu.cloud.app.api.ToolDescription
import dk.sdu.cloud.app.services.ToolDAO
import dk.sdu.cloud.app.util.yamlMapper
import dk.sdu.cloud.auth.api.PRIVILEGED_ROLES
import dk.sdu.cloud.auth.api.currentUsername
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.reader.ReaderException

class ToolController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val source: ToolDAO<DBSession>
): Controller {
    override val baseContext = HPCToolDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        protect()

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

        implement(HPCToolDescriptions.create) { req ->
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
                yamlMapper.readValue<ToolDescription>(content)
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

    companion object {
        private val log = LoggerFactory.getLogger(ToolController::class.java)
    }
}
