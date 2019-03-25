package dk.sdu.cloud.app.http

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.ToolDescription
import dk.sdu.cloud.app.api.ToolDescriptions
import dk.sdu.cloud.app.services.ToolDAO
import dk.sdu.cloud.app.util.yamlMapper
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.ContentTransformationException
import io.ktor.request.receiveText
import org.yaml.snakeyaml.reader.ReaderException

class ToolController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val source: ToolDAO<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ToolDescriptions.findByName) {
            val result = db.withTransaction {
                source.findAllByName(
                    it,
                    ctx.securityPrincipal.username,
                    request.name,
                    request.normalize()
                )
            }

            ok(result)
        }

        implement(ToolDescriptions.findByNameAndVersion) {
            val result = db.withTransaction {
                source.findByNameAndVersion(
                    it,
                    ctx.securityPrincipal.username,
                    request.name,
                    request.version
                )
            }

            ok(result)
        }

        implement(ToolDescriptions.listAll) {
            ok(
                db.withTransaction {
                    source.listLatestVersion(it, ctx.securityPrincipal.username, request.normalize())
                }
            )
        }

        implement(ToolDescriptions.create) {
            with(ctx as HttpCall) {
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
                    source.create(it, ctx.securityPrincipal.username, yamlDocument.normalize(), content)
                }

                ok(Unit)
            }
        }
    }
}
