package dk.sdu.cloud.app.http

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.ToolDescriptions
import dk.sdu.cloud.app.api.ToolDescription
import dk.sdu.cloud.app.services.ToolDAO
import dk.sdu.cloud.app.util.yamlMapper
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.request.ContentTransformationException
import io.ktor.request.receiveText
import io.ktor.routing.Route
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.reader.ReaderException

class ToolController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val source: ToolDAO<DBSession>
) : Controller {
    override val baseContext = ToolDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ToolDescriptions.findByName) { req ->
            logEntry(log, req)
            val result = db.withTransaction {
                source.findAllByName(
                    it,
                    call.securityPrincipal.username,
                    req.name,
                    req.normalize()
                )
            }

            ok(result)
        }

        implement(ToolDescriptions.findByNameAndVersion) { req ->
            logEntry(log, req)
            val result = db.withTransaction {
                source.findByNameAndVersion(
                    it,
                    call.securityPrincipal.username,
                    req.name,
                    req.version
                )
            }

            ok(result)
        }

        implement(ToolDescriptions.listAll) { req ->
            logEntry(log, req)
            ok(
                db.withTransaction {
                    source.listLatestVersion(it, call.securityPrincipal.username, req.normalize())
                }
            )
        }

        implement(ToolDescriptions.create) { req ->
            logEntry(log, req)

            val content = try {
                call.receiveText()
            } catch (ex: ContentTransformationException) {
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
                source.create(it, call.securityPrincipal.username, yamlDocument.normalize(), content)
            }

            ok(Unit)
        }

    }

    companion object {
        private val log = LoggerFactory.getLogger(ToolController::class.java)
    }
}
