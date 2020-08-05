package dk.sdu.cloud.app.store.rpc

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.ApplicationDescription
import dk.sdu.cloud.app.store.services.AppStoreService
import dk.sdu.cloud.app.store.util.yamlMapper
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.ContentTransformationException
import io.ktor.request.receiveText
import kotlinx.coroutines.io.readFully
import org.yaml.snakeyaml.reader.ReaderException

class AppStoreController(
    private val appStore: AppStoreService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {

        implement(AppStore.findByNameAndVersion) {
            ok(appStore.findByNameAndVersion(ctx.securityPrincipal, ctx.project, request.appName, request.appVersion))
        }

        implement(AppStore.hasPermission) {
            ok(appStore.hasPermission(ctx.securityPrincipal, ctx.project, request.appName, request.appVersion, request.permission))
        }

        implement(AppStore.listAcl) {
            ok(appStore.listAcl(ctx.securityPrincipal, request.appName))
        }

        implement(AppStore.updateAcl) {
            ok(appStore.updatePermissions(ctx.securityPrincipal, request.applicationName, request.changes))
        }

        implement(AppStore.findBySupportedFileExtension) {
            ok(
                appStore.findBySupportedFileExtension(
                    ctx.securityPrincipal,
                    ctx.project,
                    request.files
                )
            )
        }

        implement(AppStore.findByName) {
            ok(appStore.findByName(ctx.securityPrincipal, ctx.project, request.appName, request.normalize()))
        }

        implement(AppStore.listAll) {
            ok(appStore.listAll(ctx.securityPrincipal, ctx.project, request.normalize()))
        }

        implement(AppStore.create) {
            val length = request.asIngoing().length?.toInt()
                ?: throw RPCException("Content-Length required", HttpStatusCode.BadRequest)
            val content = ByteArray(length)
                .also { arr ->
                    request.asIngoing().channel.readFully(arr)
                }
                .let { String(it) }

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

            appStore.create(ctx.securityPrincipal, yamlDocument.normalize(), content)

            ok(Unit)
        }

        implement(AppStore.delete) {
            appStore.delete(ctx.securityPrincipal, ctx.project, request.appName, request.appVersion)
            ok(Unit)
        }

        implement(AppStore.findLatestByTool) {
            ok(appStore.findLatestByTool(ctx.securityPrincipal, ctx.project, request.tool, request.normalize()))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
