package dk.cloud.sdu.app.store.rpc

import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.services.LogoService
import dk.sdu.cloud.app.store.services.LogoType
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.service.Controller
import io.ktor.http.ContentType
import kotlinx.coroutines.io.jvm.javaio.toByteReadChannel
import java.io.ByteArrayInputStream

class AppLogoController (
    private val logoService: LogoService
): Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
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
    }
}
