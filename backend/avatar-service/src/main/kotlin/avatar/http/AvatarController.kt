package dk.sdu.cloud.avatar.http

import dk.sdu.cloud.avatar.api.*
import dk.sdu.cloud.avatar.services.AvatarStore
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.requestAllocator
import dk.sdu.cloud.calls.server.responseAllocator
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.actorAndProject

class AvatarController(
    private val avatarService: AvatarStore,
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AvatarDescriptions.update) {
            println(request.encodeToJson())
            println(request.hatColor)
            val username = actorAndProject.actor.safeUsername()

            avatarService.upsert(username, request)
            ok(Unit)
        }

        implement(AvatarDescriptions.findAvatar) {
            ok(avatarService.findByUser(ctx.responseAllocator, actorAndProject.actor.username))
        }

        implement(AvatarDescriptions.findBulk) {
            ok(avatarService.bulkFind(ctx.responseAllocator, request.usernames.map { it.decode() }))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
