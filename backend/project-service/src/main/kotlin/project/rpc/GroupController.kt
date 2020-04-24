package dk.sdu.cloud.project.rpc

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.project.api.GroupExistsResponse
import dk.sdu.cloud.project.api.IsMemberResponse
import dk.sdu.cloud.project.api.ProjectGroups
import dk.sdu.cloud.project.services.GroupService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode

class GroupController(private val groupService: GroupService) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(ProjectGroups.create) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            groupService.createGroup(ctx.securityPrincipal, project, request.group)
            ok(Unit)
        }

        implement(ProjectGroups.delete) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            groupService.deleteGroups(ctx.securityPrincipal, project, request.groups)
            ok(Unit)
        }

        implement(ProjectGroups.list) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(groupService.listGroups(ctx.securityPrincipal, project))
        }

        implement(ProjectGroups.listGroupsWithSummary) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(groupService.listGroupsWithSummary(ctx.securityPrincipal, project, request.normalize()))
        }

        implement(ProjectGroups.addGroupMember) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)

            ok(groupService.addMember(ctx.securityPrincipal, project, request.group, request.memberUsername))
        }

        implement(ProjectGroups.removeGroupMember) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(groupService.removeMember(ctx.securityPrincipal, project, request.group, request.memberUsername))
        }

        implement(ProjectGroups.updateGroupName) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(groupService.updateGroupName(
                ctx.securityPrincipal,
                project,
                request.oldGroupName,
                request.newGroupName
            ))
        }

        implement(ProjectGroups.listGroupMembers) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(
                groupService.listGroupMembers(
                    ctx.securityPrincipal,
                    project,
                    request.group,
                    request.normalize()
                ).mapItems { it.username }
            )
        }

        implement(ProjectGroups.isMember) {
            ok(IsMemberResponse(groupService.isMemberQuery(request.queries)))
        }

        implement(ProjectGroups.groupExists) {
            ok(GroupExistsResponse(groupService.exists(request.project, request.group)))
        }
    }
}
