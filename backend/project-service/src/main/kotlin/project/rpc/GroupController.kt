package dk.sdu.cloud.project.rpc

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.project.services.GroupService
import dk.sdu.cloud.project.services.ProjectException
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.HttpStatusCode
import dk.sdu.cloud.project.services.QueryService
import dk.sdu.cloud.service.*

class GroupController(
    private val db: DBContext,
    private val groups: GroupService,
    private val queries: QueryService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(ProjectGroups.create) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)

            ok(
                FindByStringId(
                    groups.createGroup(db, ctx.securityPrincipal.username, project, request.group)
                )
            )
        }

        implement(ProjectGroups.delete) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            groups.deleteGroups(db, ctx.securityPrincipal.username, project, request.groups)
            ok(Unit)
        }

        implement(ProjectGroups.listGroupsWithSummary) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(queries.listGroups(db, ctx.securityPrincipal.username, project, request.normalize()))
        }

        implement(ProjectGroups.addGroupMember) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(groups.addMember(db, ctx.securityPrincipal.username, project, request.group, request.memberUsername))
        }

        implement(ProjectGroups.removeGroupMember) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(groups.removeMember(db, ctx.securityPrincipal.username, project, request.group, request.memberUsername))
        }

        implement(ProjectGroups.updateGroupName) {
            ok(
                groups.rename(
                    db,
                    ctx.securityPrincipal.toActor(),
                    request.groupId,
                    request.newGroupName
                )
            )
        }

        implement(ProjectGroups.listAllGroupMembers) {
            ok(
                queries.listGroupMembers(
                    db,
                    null,
                    request.project,
                    request.group,
                    null
                ).items.map { it.username }
            )
        }

        implement(ProjectGroups.listGroupMembers) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(
                queries.listGroupMembers(
                    db,
                    ctx.securityPrincipal.username,
                    project,
                    request.group,
                    request.normalize()
                ).mapItems { it.username }
            )
        }

        implement(ProjectGroups.isMember) {
            ok(IsMemberResponse(queries.isMemberOfGroup(db, request.queries)))
        }

        implement(ProjectGroups.groupExists) {
            ok(GroupExistsResponse(queries.groupExists(db, request.project, request.group)))
        }

        implement(ProjectGroups.count) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(queries.groupsCount(db, project))
        }

        implement(ProjectGroups.view) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(
                queries.viewGroup(
                    db,
                    ctx.securityPrincipal.username,
                    project,
                    request.id
                )
            )
        }

        implement(ProjectGroups.lookupByTitle) {
            ok(
                queries.lookupGroupByTitle(
                    db,
                    request.projectId,
                    request.title
                )
            )
        }

        implement(ProjectGroups.lookupProjectAndGroup) {
            ok(
                queries.lookupProjectAndGroup(db, request.project, request.group)
            )
        }
    }
}
