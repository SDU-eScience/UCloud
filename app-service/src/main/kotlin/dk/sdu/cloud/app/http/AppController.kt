package dk.sdu.cloud.app.http

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.ApplicationDescription
import dk.sdu.cloud.app.api.ApplicationDescriptions
import dk.sdu.cloud.app.api.tags
import dk.sdu.cloud.app.services.ApplicationDAO
import dk.sdu.cloud.app.util.yamlMapper
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.ok
import dk.sdu.cloud.service.securityPrincipal
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import io.ktor.request.ContentTransformationException
import io.ktor.request.receiveText
import io.ktor.routing.Route
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.reader.ReaderException

class AppController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val source: ApplicationDAO<DBSession>
) : Controller {
    override val baseContext = ApplicationDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ApplicationDescriptions.toggleFavorite) { req ->
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

        implement(ApplicationDescriptions.retrieveFavorites) { req ->
            val favorites = db.withTransaction {
                source.retrieveFavorites(
                    it,
                    call.securityPrincipal.username,
                    req.normalize()
                )
            }

            ok(favorites)
        }

        implement(ApplicationDescriptions.searchTags) { req ->
            val app = db.withTransaction {
                source.searchTags(
                    it,
                    call.securityPrincipal.username,
                    req.tags,
                    req.normalize()
                )
            }

            ok(app)
        }


        implement(ApplicationDescriptions.searchApps) { req ->
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

        implement(ApplicationDescriptions.findByNameAndVersion) { req ->
            val app = db.withTransaction {
                source.findByNameAndVersionForUser(
                    it,
                    call.securityPrincipal.username,
                    req.name,
                    req.version
                )
            }

            ok(app)
        }

        implement(ApplicationDescriptions.findByName) { req ->
            val result = db.withTransaction {
                source.findAllByName(it, call.securityPrincipal.username, req.name, req.normalize())
            }

            ok(result)
        }

        implement(ApplicationDescriptions.listAll) { req ->
            ok(
                db.withTransaction {
                    source.listLatestVersion(it, call.securityPrincipal.username, req.normalize())
                }
            )
        }

        implement(ApplicationDescriptions.create) { req ->
            val content = try {
                call.receiveText()
            } catch (ex: ContentTransformationException) {
                error(CommonErrorMessage("Bad request"), HttpStatusCode.BadRequest)
                return@implement
            }

            @Suppress("DEPRECATION")
            val yamlDocument = try {
                yamlMapper.readValue<ApplicationDescription>(content)
            } catch (ex: JsonMappingException) {
                log.debug(ex.stackTraceToString())
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
