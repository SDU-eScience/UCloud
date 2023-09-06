package dk.sdu.cloud.app.store.rpc

import app.store.services.Importer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.AppStorePageType
import dk.sdu.cloud.app.store.api.ApplicationDescription
import dk.sdu.cloud.app.store.api.PageSection
import dk.sdu.cloud.app.store.services.AppStoreService
import dk.sdu.cloud.app.store.util.yamlMapper
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.mapItems
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.actorAndProject
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import org.yaml.snakeyaml.reader.ReaderException

class AppStoreController(
    private val appStore: AppStoreService,
    private val importer: Importer? = null
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {

        implement(AppStore.findByNameAndVersion) {
            ok(appStore.findByNameAndVersion(actorAndProject, request.appName, request.appVersion))
        }

        implement(AppStore.hasPermission) {
            ok(appStore.hasPermission(actorAndProject, request.appName, request.appVersion, request.permission))
        }

        implement(AppStore.listAcl) {
            ok(appStore.listAcl(actorAndProject, request.appName))
        }

        implement(AppStore.updateAcl) {
            ok(appStore.updatePermissions(actorAndProject, request.applicationName, request.changes))
        }

        implement(AppStore.findBySupportedFileExtension) {
            ok(
                appStore.findBySupportedFileExtension(
                    actorAndProject,
                    request.normalize(),
                    ctx.project,
                    request.files
                )
            )
        }

        implement(AppStore.findByName) {
            ok(appStore.findByName(actorAndProject, request.appName, request.normalize()).mapItems { it.withoutInvocation() })
        }

        implement(AppStore.listAll) {
            ok(appStore.listAll(actorAndProject, request.normalize()))
        }

        implement(AppStore.store) {
            ok(appStore.browseSections(actorAndProject, request.page))
        }

        implement(AppStore.setGroup) {
            ok(appStore.setGroup(actorAndProject, request.applicationName, request.groupId))
        }

        implement(AppStore.createGroup) {
            ok(appStore.createGroup(actorAndProject, request.title))
        }

        implement(AppStore.deleteGroup) {
            ok(appStore.deleteGroup(actorAndProject, request.id))
        }

        implement(AppStore.updateGroup) {
            ok(appStore.updateGroup(
                actorAndProject,
                request.id,
                request.title,
                request.logo,
                request.description,
                request.defaultApplication
            ))
        }

        implement(AppStore.listGroups) {
            ok(appStore.listGroups(actorAndProject))
        }

        implement(AppStore.retrieveGroup) {
            ok(appStore.retrieveGroup(actorAndProject, request.id, request.name))
        }

        implement(AppStore.create) {
            val length = (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                ?: throw RPCException("Content-Length required", dk.sdu.cloud.calls.HttpStatusCode.BadRequest)
            val channel = (ctx as HttpCall).call.request.receiveChannel()
            val content = ByteArray(length.toInt())
                .also { arr -> channel.readFully(arr) }
                .let { String(it) }

            @Suppress("DEPRECATION")
            val yamlDocument = try {
                yamlMapper.readValue<ApplicationDescription>(content)
            } catch (ex: JsonMappingException) {
                log.debug(ex.stackTraceToString())
                error(
                    CommonErrorMessage(
                        "Bad value for parameter ${ex.pathReference.replace(
                            "dk.sdu.cloud.app.store.api.",
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

            appStore.create(actorAndProject, yamlDocument.normalize(), content)

            ok(Unit)
        }

        implement(AppStore.updateLanding) {
            val length = (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                ?: throw RPCException("Content-Length required", dk.sdu.cloud.calls.HttpStatusCode.BadRequest)
            val channel = (ctx as HttpCall).call.request.receiveChannel()
            val content = ByteArray(length.toInt())
                .also { arr -> channel.readFully(arr) }
                .let { String(it) }

            @Suppress("DEPRECATION")
            val yamlDocument = try {
                yamlMapper.readValue<List<PageSection>>(content)
            } catch (ex: JsonMappingException) {
                log.debug(ex.stackTraceToString())
                error(
                    CommonErrorMessage(
                        "Bad value for parameter ${ex.pathReference.replace(
                            "dk.sdu.cloud.app.store.api.",
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

            ok(appStore.updatePage(AppStorePageType.LANDING, yamlDocument))
        }

        implement(AppStore.updateOverview) {
            val length = (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                ?: throw RPCException("Content-Length required", dk.sdu.cloud.calls.HttpStatusCode.BadRequest)
            val channel = (ctx as HttpCall).call.request.receiveChannel()
            val content = ByteArray(length.toInt())
                .also { arr -> channel.readFully(arr) }
                .let { String(it) }

            @Suppress("DEPRECATION")
            val yamlDocument = try {
                yamlMapper.readValue<List<PageSection>>(content)
            } catch (ex: JsonMappingException) {
                log.debug(ex.stackTraceToString())
                error(
                    CommonErrorMessage(
                        "Bad value for parameter ${ex.pathReference.replace(
                            "dk.sdu.cloud.app.store.api.",
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

            ok(appStore.updatePage(AppStorePageType.FULL, yamlDocument))
        }

        implement(AppStore.delete) {
            appStore.delete(actorAndProject, request.appName, request.appVersion)
            ok(Unit)
        }

        implement(AppStore.findLatestByTool) {
            ok(appStore.findLatestByTool(actorAndProject, request.tool, request.normalize()))
        }

        importer?.let { im ->
            implement(AppStore.devImport) {
                ok(im.importApplications(request.endpoint, request.checksum))
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
