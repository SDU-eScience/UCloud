package dk.cloud.sdu.app.store.rpc

import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.services.LogoService
import dk.sdu.cloud.app.store.services.LogoType
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.ByteArrayInputStream

class AppLogoController (
    private val logoService: LogoService
): Controller {
    @OptIn(KtorExperimentalAPI::class)
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AppStore.uploadLogo) {
            logoService.acceptUpload(
                actorAndProject,
                LogoType.APPLICATION,
                request.name,
                (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull(),
                (ctx as HttpCall).call.request.receiveChannel()
            )

            ok(Unit)
        }

        implement(AppStore.clearLogo) {
            logoService.clearLogo(actorAndProject, LogoType.APPLICATION, request.name)
            ok(Unit)
        }

        implement(AppStore.fetchLogo) {
            val logo = logoService.fetchLogo(actorAndProject, LogoType.APPLICATION, request.name)

            (ctx as HttpCall).call.respond(
                object : OutgoingContent.ReadChannelContent() {
                    override val contentLength = logo.size.toLong()
                    override val contentType = ContentType.Image.Any
                    override fun readFrom(): ByteReadChannel = ByteArrayInputStream(logo).toByteReadChannel()
                }
            )

            okContentAlreadyDelivered()
        }

        implement(AppStore.uploadGroupLogo) {
            logoService.acceptUpload(
                actorAndProject,
                LogoType.GROUP,
                request.name,
                (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull(),
                (ctx as HttpCall).call.request.receiveChannel()
            )

            ok(Unit)
        }

        implement(AppStore.clearGroupLogo) {
            logoService.clearLogo(actorAndProject, LogoType.GROUP, request.name)
            ok(Unit)
        }

        implement(AppStore.fetchGroupLogo) {
            val logo = logoService.fetchLogo(actorAndProject, LogoType.GROUP, request.name)

            (ctx as HttpCall).call.respond(
                object : OutgoingContent.ReadChannelContent() {
                    override val contentLength = logo.size.toLong()
                    override val contentType = ContentType.Image.Any
                    override fun readFrom(): ByteReadChannel = ByteArrayInputStream(logo).toByteReadChannel()
                }
            )

            okContentAlreadyDelivered()
        }
    }
}
