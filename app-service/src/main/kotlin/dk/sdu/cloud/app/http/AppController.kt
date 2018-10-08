package dk.sdu.cloud.app.http

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.ApplicationDescription
import dk.sdu.cloud.app.api.HPCApplicationDescriptions
import dk.sdu.cloud.app.services.ApplicationDAO
import dk.sdu.cloud.app.util.yamlMapper
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.routing.Route
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.reader.ReaderException

class AppController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val source: ApplicationDAO<DBSession>
) : Controller {
    override val baseContext = HPCApplicationDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {

        implement(HPCApplicationDescriptions.toggleFavorite) {req ->
            logEntry(log, req)

            db.withTransaction {
                source.toggleFavorite(
                    it,
                    call.securityPrincipal.username,
                    req.name,
                    req.version
                )
            }

            ok(HttpStatusCode.OK)

        }

        implement(HPCApplicationDescriptions.retrieveFavorites) {req ->
            logEntry(log, req)

            val favorites = db.withTransaction {
                source.retrieveFavorites(
                    it,
                    call.securityPrincipal.username,
                    req.normalize()
                )
            }

            ok(favorites)
        }

        implement(HPCApplicationDescriptions.searchTags) { req ->
            logEntry(log, req)

            val app = db.withTransaction {
                source.searchTags(
                    it,
                    call.securityPrincipal.username,
                    req.query,
                    req.normalize()
                )
            }

            ok(app)
        }


        implement(HPCApplicationDescriptions.searchApps) { req ->
            logEntry(log, req)

            val app = db.withTransaction {
                source.search(
                    it,
                    call.securityPrincipal.username,
                    req.query,
                    req.normalize()
                )
            }

            ok(app)
        }

        implement(HPCApplicationDescriptions.findByNameAndVersion) { req ->
            logEntry(log, req)

            val app = db.withTransaction {
                source.findByNameAndVersion(
                    it,
                    call.securityPrincipal.username,
                    req.name,
                    req.version
                )
            }

            ok(app)
        }

        implement(HPCApplicationDescriptions.findByName) { req ->
            logEntry(log, req)

            val result = db.withTransaction {
                source.findAllByName(it, call.securityPrincipal.username, req.name, req.normalize())
            }

            ok(result)
        }

        implement(HPCApplicationDescriptions.listAll) { req ->
            logEntry(log, req)

            ok(
                db.withTransaction {
                    source.listLatestVersion(it, call.securityPrincipal.username, req.normalize())
                }
            )
        }

        implement(HPCApplicationDescriptions.create) { req ->
            logEntry(log, req)

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
                source.create(it, call.securityPrincipal.username, yamlDocument.normalize(), content)
            }

            ok(Unit)
        }

    }

    companion object {
        private val log = LoggerFactory.getLogger(AppController::class.java)
    }
}
