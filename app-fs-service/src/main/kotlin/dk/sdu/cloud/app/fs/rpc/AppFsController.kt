package dk.sdu.cloud.app.fs.rpc

import dk.sdu.cloud.app.fs.api.AppFileSystems
import dk.sdu.cloud.app.fs.services.SharedFileSystemService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityToken
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

class AppFsController(
    private val sharedFileSystemService: SharedFileSystemService<*>
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AppFileSystems.create) {
            ok(AppFileSystems.Create.Response(sharedFileSystemService.create(ctx.securityToken, request.backend)))
        }

        implement(AppFileSystems.delete) {
            sharedFileSystemService.delete(ctx.securityToken, request.id)
            ok(AppFileSystems.Delete.Response())
        }

        implement(AppFileSystems.list) {
            ok(sharedFileSystemService.list(ctx.securityToken, request.normalize()))
        }

        implement(AppFileSystems.view) {
            ok(sharedFileSystemService.view(ctx.securityToken, request.id, request.calculateSize ?: true))
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
