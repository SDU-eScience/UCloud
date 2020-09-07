package dk.sdu.cloud.project.rpc

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.project.api.LookupAdminsBulkResponse
import dk.sdu.cloud.project.api.LookupAdminsResponse
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.HttpStatusCode
import dk.sdu.cloud.project.services.QueryService
import dk.sdu.cloud.service.toActor

class MembershipController(
    private val db: DBContext,
    private val queries: QueryService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(ProjectMembers.userStatus) {
            val inputUsername = if (ctx.securityPrincipal.role in Roles.PRIVILEGED) {
                request.username
            } else {
                null
            }

            val username = inputUsername ?: ctx.securityPrincipal.username
            ok(queries.summarizeMembershipForUser(db, username))
        }

        implement(ProjectMembers.search) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(
                queries.membershipSearch(
                    db,
                    ctx.securityPrincipal.username,
                    project,
                    request.query,
                    request.normalize(),
                    request.notInGroup
                )
            )
        }

        implement(ProjectMembers.count) {
            val project = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            ok(
                queries.membersCount(
                    db,
                    ctx.securityPrincipal,
                    project
                )
            )
        }

        implement(ProjectMembers.lookupAdmins) {
            ok(
                LookupAdminsResponse(
                    queries.lookupAdmins(db, ctx.securityPrincipal.toActor(), request.projectId
                    )
                )
            )
        }

        implement(ProjectMembers.lookupAdminsBulk) {
            ok(
                LookupAdminsBulkResponse(
                    queries.lookupAdminsBulk(db, ctx.securityPrincipal.toActor(), request.projectId)
                )
            )
        }
    }
}
