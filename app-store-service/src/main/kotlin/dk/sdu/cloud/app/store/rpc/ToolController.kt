package dk.sdu.cloud.app.store.rpc

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.store.api.ToolDescription
import dk.sdu.cloud.app.store.api.ToolStore
import dk.sdu.cloud.app.store.services.LogoService
import dk.sdu.cloud.app.store.services.LogoType
import dk.sdu.cloud.app.store.services.ToolDAO
import dk.sdu.cloud.app.store.util.yamlMapper
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.ContentTransformationException
import io.ktor.request.receiveText
import kotlinx.coroutines.io.jvm.javaio.toByteReadChannel
import org.yaml.snakeyaml.reader.ReaderException
import java.io.ByteArrayInputStream

class ToolController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val source: ToolDAO<DBSession>,
    private val logoService: LogoService<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ToolStore.findByName) {
            val result = db.withTransaction {
                source.findAllByName(
                    it,
                    ctx.securityPrincipal,
                    request.name,
                    request.normalize()
                )
            }

            ok(result)
        }

        implement(ToolStore.findByNameAndVersion) {
            val result = db.withTransaction {
                source.findByNameAndVersion(
                    it,
                    ctx.securityPrincipal,
                    request.name,
                    request.version
                )
            }
            ok(result)
        }

        implement(ToolStore.listAll) {
            ok(
                db.withTransaction {
                    source.listLatestVersion(it, ctx.securityPrincipal, request.normalize())
                }
            )
        }

        implement(ToolStore.create) {
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
                    source.create(it, ctx.securityPrincipal, yamlDocument.normalize(), content)
                }

                ok(Unit)
            }
        }

        implement(ToolStore.uploadLogo) {
            logoService.acceptUpload(
                ctx.securityPrincipal,
                LogoType.TOOL,
                request.name,
                request.data.asIngoing()
            )

            ok(Unit)
        }

        implement(ToolStore.fetchLogo) {
            val logo = logoService.fetchLogo(LogoType.TOOL, request.name)
            ok(
                BinaryStream.outgoingFromChannel(
                    ByteArrayInputStream(logo).toByteReadChannel(),
                    logo.size.toLong(),
                    ContentType.Image.Any
                )
            )
        }
    }
}
