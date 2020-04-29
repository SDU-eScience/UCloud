package dk.sdu.cloud.project.rpc

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.services.MembershipService
import dk.sdu.cloud.service.Controller
import io.ktor.http.HttpStatusCode

class MembershipController(
    private val members: MembershipService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(ProjectMembers.userStatus) {
            val inputUsername = if (ctx.securityPrincipal.role in Roles.PRIVILEDGED) {
                request.username
            } else {
                null
            }

            val username = inputUsername ?: ctx.securityPrincipal.username
            ok(members.summarizeMembershipForUser(username))
        }

        implement(ProjectMembers.search) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(members.search(ctx.securityPrincipal, project, request.query, request.normalize()))
        }
    }
}
