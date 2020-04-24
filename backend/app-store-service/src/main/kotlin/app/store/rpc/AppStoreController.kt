package dk.sdu.cloud.app.store.rpc

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.ApplicationDescription
import dk.sdu.cloud.app.store.api.IsPublicResponse
import dk.sdu.cloud.app.store.api.tags
import dk.sdu.cloud.app.store.services.AppStoreService
import dk.sdu.cloud.app.store.services.LogoService
import dk.sdu.cloud.app.store.services.LogoType
import dk.sdu.cloud.app.store.util.yamlMapper
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.ContentTransformationException
import io.ktor.request.receiveText
import kotlinx.coroutines.io.jvm.javaio.toByteReadChannel
import org.yaml.snakeyaml.reader.ReaderException
import java.io.ByteArrayInputStream

class AppStoreController<DBSession>(
    private val appStore: AppStoreService<DBSession>,
    private val logoService: LogoService<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {

        implement(AppStore.toggleFavorite) {
            ok(appStore.toggleFavorite(ctx.securityPrincipal, request.appName, request.appVersion))
        }

        implement(AppStore.retrieveFavorites) {
            ok(appStore.retrieveFavorites(ctx.securityPrincipal, request))
        }

        implement(AppStore.searchTags) {
            ok(appStore.searchTags(ctx.securityPrincipal, request.tags, request.normalize()))
        }

        implement(AppStore.searchApps) {
            ok(appStore.searchApps(ctx.securityPrincipal, request.query, request.normalize()))
        }

        implement(AppStore.findByNameAndVersion) {
            ok(appStore.findByNameAndVersion(ctx.securityPrincipal, request.appName, request.appVersion))
        }

        implement(AppStore.hasPermission) {
            ok(appStore.hasPermission(ctx.securityPrincipal, request.appName, request.appVersion, request.permission))
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
                    request.files
                )
            )
        }

        implement(AppStore.findByName) {
            ok(appStore.findByName(ctx.securityPrincipal, request.appName, request.normalize()))
        }

        implement(AppStore.isPublic) {
            ok(IsPublicResponse(appStore.isPublic(ctx.securityPrincipal, request.applications)))
        }

        implement(AppStore.setPublic) {
            ok(appStore.setPublic(ctx.securityPrincipal, request.appName, request.appVersion, request.public))
        }

        implement(AppStore.listAll) {
            ok(appStore.listAll(ctx.securityPrincipal, request.normalize()))
        }

        implement(AppStore.advancedSearch) {
            ok(appStore.advancedSearch(
                ctx.securityPrincipal,
                request.query,
                request.tags,
                request.showAllVersions,
                request.normalize()
            ))
        }

        implement(AppStore.create) {
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

                appStore.create(ctx.securityPrincipal, yamlDocument.normalize(), content)

                ok(Unit)
            }
        }

        implement(AppStore.delete) {
            appStore.delete(ctx.securityPrincipal, request.appName, request.appVersion)
            ok(Unit)
        }

        implement(AppStore.createTag) {
            appStore.createTags(
                request.tags,
                request.applicationName,
                ctx.securityPrincipal
            )
            ok(Unit)
        }

        implement(AppStore.removeTag) {
            appStore.deleteTags(
                request.tags,
                request.applicationName,
                ctx.securityPrincipal
            )
            ok(Unit)
        }

        implement(AppStore.uploadLogo) {
            logoService.acceptUpload(
                ctx.securityPrincipal,
                LogoType.APPLICATION,
                request.name,
                request.data.asIngoing()
            )

            ok(Unit)
        }

        implement(AppStore.clearLogo) {
            logoService.clearLogo(ctx.securityPrincipal, LogoType.APPLICATION, request.name)
            ok(Unit)
        }

        implement(AppStore.fetchLogo) {
            val logo = logoService.fetchLogo(LogoType.APPLICATION, request.name)
            ok(
                BinaryStream.outgoingFromChannel(
                    ByteArrayInputStream(logo).toByteReadChannel(),
                    logo.size.toLong(),
                    ContentType.Image.Any
                )
            )
        }

        implement(AppStore.findLatestByTool) {
            ok(appStore.findLatestByTool(ctx.securityPrincipal, request.tool, request.normalize()))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
