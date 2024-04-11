package dk.sdu.cloud.app.store.rpc

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.*
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.services.*
import dk.sdu.cloud.app.store.util.yamlMapper
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.Page
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.cio.toByteReadChannel
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.javaio.*
import org.yaml.snakeyaml.reader.ReaderException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.text.String

class AppStoreController(
    private val importExportService: ImportExport,
    private val service: AppService,
    private val devMode: Boolean,
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AppStore.findByNameAndVersion) {
            ok(
                service.retrieveApplication(actorAndProject, request.appName, request.appVersion)
                    ?: throw RPCException("Unknown application", HttpStatusCode.NotFound)
            )
        }

        implement(AppStore.retrieveAcl) {
            ok(
                AppStore.RetrieveAcl.Response(
                    service.retrieveDetailedAcl(actorAndProject, request.name).toList()
                )
            )
        }

        implement(AppStore.updateAcl) {
            service.updateAcl(actorAndProject, request.name, request.changes)
            ok(Unit)
        }

        implement(AppStore.browseOpenWithRecommendations) {
            ok(
                PageV2.of(
                    service.listByExtension(
                        actorAndProject,
                        request.files
                    )
                )
            )
        }

        implement(AppStore.findByName) {
            val items = service.listVersions(actorAndProject, request.appName).map { it.withoutInvocation() }
            ok(
                Page(
                    items.size,
                    items.size,
                    0,
                    items
                )
            )
        }

        implement(AppStore.assignApplicationToGroup) {
            service.assignApplicationToGroup(actorAndProject, request.name, request.group)
            ok(Unit)
        }

        implement(AppStore.createGroup) {
            val id = service.createGroup(actorAndProject, request.title)
            ok(FindByIntId(id))
        }

        implement(AppStore.deleteGroup) {
            service.deleteGroup(actorAndProject, request.id)
            ok(Unit)
        }

        implement(AppStore.updateGroup) {
            service.updateGroup(
                actorAndProject,
                request.id,
                newTitle = request.newTitle,
                newDescription = request.newDescription,
                newDefaultFlavor = request.newDefaultFlavor,
                newLogoHasText = request.newLogoHasText,
            )

            ok(Unit)
        }

        implement(AppStore.browseGroups) {
            ok(PageV2.of(service.listGroups(actorAndProject)))
        }

        implement(AppStore.retrieveGroup) {
            val group = service.retrieveGroup(actorAndProject, request.id, loadApplications = true)
                ?: throw RPCException("No such group exists!", HttpStatusCode.NotFound)

            ok(group)
        }

        implement(AppStore.create) {
            val length = (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                ?: throw RPCException("Content-Length required", HttpStatusCode.BadRequest)
            val channel = (ctx as HttpCall).call.request.receiveChannel()
            val content = ByteArray(length.toInt())
                .also { arr -> channel.readFully(arr) }
                .let { String(it) }

            @Suppress("DEPRECATION")
            val yamlDocument = try {
                yamlMapper.readValue<ApplicationYaml>(content)
            } catch (ex: JsonMappingException) {
                log.debug(ex.stackTraceToString())
                throw RPCException(
                    "Bad value for parameter ${
                        ex.pathReference.replace(
                            "dk.sdu.cloud.app.store.api.",
                            ""
                        )
                    }. ${ex.message}",
                    HttpStatusCode.BadRequest
                )
            } catch (ex: MarkedYAMLException) {
                log.debug(ex.stackTraceToString())
                throw RPCException("Invalid YAML document", HttpStatusCode.BadRequest)
            } catch (ex: ReaderException) {
                throw RPCException(
                    "Document contains illegal characters (unicode?)",
                    HttpStatusCode.BadRequest
                )
            }

            service.createApplication(actorAndProject, yamlDocument.normalize())
            ok(Unit)
        }

        implement(AppStore.listAllApplications) {
            ok(AppStore.ListAllApplications.Response(service.listAllApplications()))
        }

        implement(AppStore.updateApplicationFlavor) {
            service.updateAppFlavorName(actorAndProject, request.applicationName, request.flavorName)
            ok(Unit)
        }


        if (devMode) {
            implement(AppStore.devImport) {
                val client = HttpClient(CIO)
                val bytes = ByteArrayOutputStream().use { bos ->
                    client.get(request.endpoint).bodyAsChannel().copyTo(bos)
                    bos.toByteArray()
                }

                val digest = MessageDigest.getInstance("SHA-256")
                ByteArrayInputStream(bytes).use { ins ->
                    val buf = ByteArray(1024)
                    while (true) {
                        val bytesRead = ins.read(buf)
                        if (bytesRead == -1) break
                        digest.update(buf, 0, bytesRead)
                    }
                }

                // NOTE(Dan): This checksum assumes that the client can be trusted. This is only intended to protect against a
                // sudden compromise of the domain we use to host the assets or some other mitm attack. This should all be
                // fine given that this code is only ever supposed to run locally.
                val computedChecksum = hex(digest.digest())
                if (computedChecksum != request.checksum) {
                    log.info("Invalid checksum. Computed: $computedChecksum. Expected: ${request.checksum}")
                    throw RPCException("invalid checksum", HttpStatusCode.BadRequest)
                }

                importExportService.importFromZip(bytes)
                ok(Unit)
            }
        }

        implement(AppStore.importFromFile) {
            val http = ctx as HttpCall
            val bytes = http.call.request.receiveChannel().readRemaining(1024 * 1024 * 64).readBytes()
            importExportService.importFromZip(bytes)
            ok(Unit)
        }

        implement(AppStore.export) {
            val bytes = importExportService.exportToZip()
            (ctx as HttpCall).call.respond(
                object : OutgoingContent.ReadChannelContent() {
                    override val contentLength = bytes.size.toLong()
                    override val contentType = ContentType.Application.Zip
                    override fun readFrom(): ByteReadChannel = ByteArrayInputStream(bytes).toByteReadChannel()
                }
            )

            okContentAlreadyDelivered()
        }

        implement(AppStore.toggleStar) {
            ok(service.toggleStar(actorAndProject, request.name))
        }

        implement(AppStore.retrieveStars) {
            val items = service.listStarredApplications(actorAndProject).map { it.withoutInvocation() }
            ok(AppStore.RetrieveStars.Response(items))
        }

        implement(AppStore.addLogoToGroup) {
            val http = ctx as HttpCall
            val packet = http.call.request.receiveChannel().readRemaining(1024 * 1024 * 2)
            service.updateGroup(
                actorAndProject,
                request.groupId,
                newLogo = packet.readBytes()
            )

            ok(Unit)
        }

        implement(AppStore.removeLogoFromGroup) {
            service.updateGroup(
                actorAndProject,
                request.id,
                newLogo = ByteArray(0),
            )
            ok(Unit)
        }

        implement(AppStore.retrieveGroupLogo) {
            val bytes = service.retrieveGroupLogo(
                request.id,
                request.darkMode,
                request.includeText,
                request.placeTextUnderLogo,
                null
            ) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            (ctx as HttpCall).call.respond(
                object : OutgoingContent.ReadChannelContent() {
                    override val contentLength = bytes.size.toLong()
                    override val contentType = ContentType.Image.PNG
                    override fun readFrom(): ByteReadChannel = ByteArrayInputStream(bytes).toByteReadChannel()
                }
            )

            okContentAlreadyDelivered()
        }

        implement(AppStore.retrieveAppLogo) {
            // NOTE(Dan): The endpoint does not have any authentication token, as a result, we resolve the
            // application with system privileges simply to find the appropriate groupId.
            val app = service.retrieveApplication(ActorAndProject.System, request.name, null)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            val groupId = app.metadata.group?.metadata?.id

            val bytes = if (groupId == null) {
                val cacheKey = buildString {
                    append("NO_LOGO")
                    append(app.metadata.title)
                    append(request.darkMode)
                    append(request.includeText)
                    append(request.placeTextUnderLogo)
                }

                LogoGenerator.generateLogoWithText(
                    cacheKey,
                    app.metadata.title,
                    LogoGenerator.emptyImage,
                    request.placeTextUnderLogo,
                    if (request.darkMode) DarkBackground else LightBackground,
                    emptyMap()
                )
            } else {
                service.retrieveGroupLogo(
                    groupId, request.darkMode, request.includeText,
                    request.placeTextUnderLogo, app.metadata.flavorName
                ) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

            (ctx as HttpCall).call.respond(
                object : OutgoingContent.ReadChannelContent() {
                    override val contentLength = bytes.size.toLong()
                    override val contentType = ContentType.Image.PNG
                    override fun readFrom(): ByteReadChannel = ByteArrayInputStream(bytes).toByteReadChannel()
                }
            )

            okContentAlreadyDelivered()
        }

        implement(AppStore.updatePublicFlag) {
            service.updatePublicFlag(
                actorAndProject,
                NameAndVersion(request.name, request.version),
                request.public
            )
            ok(Unit)
        }

        implement(AppStore.search) {
            val items = service.search(actorAndProject, request.query).map { it.withoutInvocation() }
            ok(PageV2.of(items))
        }

        implement(AppStore.browseCategories) {
            ok(PageV2.of(service.listCategories()))
        }

        implement(AppStore.retrieveCategory) {
            ok(
                service.retrieveCategory(actorAndProject, request.id, loadGroups = true)
                    ?: throw RPCException("Unknown group", HttpStatusCode.NotFound)
            )
        }

        implement(ToolStore.findByName) {
            val items = service.listToolVersions(actorAndProject, request.appName)
            ok(Page(items.size, items.size, 0, items))
        }

        implement(ToolStore.findByNameAndVersion) {
            ok(
                service.retrieveTool(
                    actorAndProject,
                    request.name,
                    request.version
                ) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            )
        }

        implement(ToolStore.create) {
            val length = (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                ?: throw RPCException("Content-Length required", HttpStatusCode.BadRequest)
            val channel = (ctx as HttpCall).call.request.receiveChannel()
            val content = ByteArray(length.toInt())
                .also { arr -> channel.readFully(arr) }
                .let { String(it) }

            @Suppress("DEPRECATION")
            val yamlDocument = try {
                yamlMapper.readValue<ToolYaml>(content)
            } catch (ex: JsonMappingException) {
                throw RPCException(
                    "Bad value for parameter ${
                        ex.pathReference.replace(
                            "dk.sdu.cloud.app.api.",
                            ""
                        )
                    }. ${ex.message}",
                    HttpStatusCode.BadRequest
                )
            } catch (ex: MarkedYAMLException) {
                throw RPCException("Invalid YAML document", HttpStatusCode.BadRequest)
            } catch (ex: ReaderException) {
                throw RPCException(
                    "Document contains illegal characters (unicode?)",
                    HttpStatusCode.BadRequest
                )
            }

            service.createTool(
                actorAndProject,
                Tool(actorAndProject.actor.safeUsername(), Time.now(), Time.now(), yamlDocument.normalize())
            )

            ok(Unit)
        }

        implement(AppStore.retrieveLandingPage) {
            ok(service.retrieveLandingPage(actorAndProject))
        }

        implement(AppStore.retrieveCarrouselImage) {
            // NOTE(Dan): request.slideTitle is used mostly to circumvent the cache in case the carrousel is updated.

            val bytes = service.retrieveCarrouselImage(request.index)
            (ctx as HttpCall).call.respond(
                object : OutgoingContent.ReadChannelContent() {
                    override val contentLength = bytes.size.toLong()
                    override val contentType = ContentType.Image.Any
                    override fun readFrom(): ByteReadChannel = ByteArrayInputStream(bytes).toByteReadChannel()
                }
            )

            okContentAlreadyDelivered()
        }

        implement(AppStore.createCategory) {
            ok(FindByIntId(service.createCategory(actorAndProject, request)))
        }

        implement(AppStore.deleteCategory) {
            service.deleteCategory(actorAndProject, request.id)
            ok(Unit)
        }

        implement(AppStore.addGroupToCategory) {
            service.addGroupToCategory(actorAndProject, listOf(request.categoryId), request.groupId)
            ok(Unit)
        }

        implement(AppStore.removeGroupFromCategory) {
            service.removeGroupFromCategories(actorAndProject, listOf(request.categoryId), request.groupId)
            ok(Unit)
        }

        implement(AppStore.assignPriorityToCategory) {
            service.assignPriorityToCategory(actorAndProject, request.id, request.priority)
            ok(Unit)
        }

        implement(AppStore.createSpotlight) {
            val id = service.createOrUpdateSpotlight(
                actorAndProject,
                null,
                request.title,
                request.body,
                request.active,
                request.applications
            )
            ok(FindByIntId(id))
        }

        implement(AppStore.updateSpotlight) {
            service.createOrUpdateSpotlight(
                actorAndProject,
                request.id ?: throw RPCException("Missing ID", HttpStatusCode.BadRequest),
                request.title,
                request.body,
                request.active,
                request.applications
            )

            ok(Unit)
        }

        implement(AppStore.deleteSpotlight) {
            service.deleteSpotlight(actorAndProject, request.id)
            ok(Unit)
        }

        implement(AppStore.browseSpotlights) {
            ok(PageV2.of(service.listSpotlights(actorAndProject)))
        }

        implement(AppStore.retrieveSpotlight) {
            ok(
                service.retrieveSpotlights(actorAndProject, request.id)
                    ?: throw RPCException("Unknown spotlight", HttpStatusCode.NotFound)
            )
        }

        implement(AppStore.activateSpotlight) {
            service.activateSpotlight(actorAndProject, request.id)
            ok(Unit)
        }

        implement(AppStore.updateCarrousel) {
            service.updateCarrousel(actorAndProject, request.newSlides)
            ok(Unit)
        }

        implement(AppStore.updateCarrouselImage) {
            val http = ctx as HttpCall
            val packet = http.call.request.receiveChannel().readRemaining(1024 * 1024 * 2)
            val bytes = packet.readBytes()
            service.updateCarrouselImage(actorAndProject, request.slideIndex, bytes)
            ok(Unit)
        }

        implement(AppStore.updateTopPicks) {
            service.updateTopPicks(actorAndProject, request.newTopPicks)
            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
