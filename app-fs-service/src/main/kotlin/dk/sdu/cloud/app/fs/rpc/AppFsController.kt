package dk.sdu.cloud.app.fs.rpc

import dk.sdu.cloud.app.fs.api.AppFileSystems
import dk.sdu.cloud.app.fs.api.SharedFileSystemFileWrapper
import dk.sdu.cloud.app.fs.services.SharedFileSystemService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityToken
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Page
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class AppFsController(
    private val sharedFileSystemService: SharedFileSystemService<*>
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AppFileSystems.create) {
            ok(
                AppFileSystems.Create.Response(
                    sharedFileSystemService.create(ctx.securityToken, request.backend, request.title)
                )
            )
        }

        implement(AppFileSystems.delete) {
            sharedFileSystemService.delete(ctx.securityToken, request.id)
            ok(AppFileSystems.Delete.Response())
        }

        implement(AppFileSystems.list) {
            ok(sharedFileSystemService.list(ctx.securityToken, request.normalize()))
        }

        implement(AppFileSystems.listAsFile) {
            val fileSystems = sharedFileSystemService.list(ctx.securityToken, request.normalize())
            val wrappedFiles = coroutineScope {
                fileSystems.items.map { fileSystem ->
                    async {
                        val size = sharedFileSystemService.calculateSize(fileSystem)
                        SharedFileSystemFileWrapper(fileSystem, size)
                    }
                }.awaitAll()
            }

            ok(
                Page(
                    fileSystems.itemsInTotal,
                    fileSystems.itemsPerPage,
                    fileSystems.pageNumber,
                    wrappedFiles
                )
            )
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
