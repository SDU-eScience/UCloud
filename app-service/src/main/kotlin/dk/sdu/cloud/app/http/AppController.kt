package dk.sdu.cloud.app.http

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.ApplicationDescription
import dk.sdu.cloud.app.api.ApplicationDescriptions
import dk.sdu.cloud.app.api.ToolReference
import dk.sdu.cloud.app.api.tags
import dk.sdu.cloud.app.services.ApplicationDAO
import dk.sdu.cloud.app.services.ToolDAO
import dk.sdu.cloud.app.util.yamlMapper
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.ContentTransformationException
import io.ktor.request.receiveText
import org.yaml.snakeyaml.reader.ReaderException

class AppController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val source: ApplicationDAO<DBSession>,
    private val toolDao: ToolDAO<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ApplicationDescriptions.toggleFavorite) {
            db.withTransaction {
                source.toggleFavorite(
                    it,
                    ctx.securityPrincipal.username,
                    request.name,
                    request.version
                )
            }

            ok(Unit)
        }

        implement(ApplicationDescriptions.retrieveFavorites) {
            val favorites = db.withTransaction {
                source.retrieveFavorites(
                    it,
                    ctx.securityPrincipal.username,
                    request.normalize()
                )
            }

            ok(favorites)
        }

        implement(ApplicationDescriptions.searchTags) {
            val app = db.withTransaction {
                source.searchTags(
                    it,
                    ctx.securityPrincipal.username,
                    request.tags,
                    request.normalize()
                )
            }

            ok(app)
        }


        implement(ApplicationDescriptions.searchApps) {
            val app = db.withTransaction {
                source.search(
                    it,
                    ctx.securityPrincipal.username,
                    request.query,
                    request.normalize()
                )
            }

            ok(app)
        }

        implement(ApplicationDescriptions.findByNameAndVersion) {
            val app = db.withTransaction {
                val user = ctx.securityPrincipal.username
                val result = source.findByNameAndVersionForUser(
                    it,
                    user,
                    request.name,
                    request.version
                )

                val toolRef = result.invocation.tool
                val tool =
                    toolDao.findByNameAndVersion(it, user, toolRef.name, toolRef.version)

                result.copy(
                    invocation = result.invocation.copy(
                        tool = ToolReference(
                            toolRef.name,
                            toolRef.version,
                            tool
                        )
                    )
                )
            }

            ok(app)
        }

        implement(ApplicationDescriptions.findByName) {
            val result = db.withTransaction {
                source.findAllByName(it, ctx.securityPrincipal.username, request.name, request.normalize())
            }

            ok(result)
        }

        implement(ApplicationDescriptions.listAll) {
            ok(
                db.withTransaction {
                    source.listLatestVersion(it, ctx.securityPrincipal.username, request.normalize())
                }
            )
        }

        implement(ApplicationDescriptions.create) {
            with(ctx as HttpCall) {
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
                    source.create(it, ctx.securityPrincipal.username, yamlDocument.normalize(), content)
                }

                ok(Unit)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
