package dk.sdu.cloud.project.rpc

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.project.services.ProjectException
import dk.sdu.cloud.project.services.ProjectService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.project.services.QueryService
import io.ktor.http.HttpStatusCode

class ProjectController(
    private val db: DBContext,
    private val projects: ProjectService,
    private val queries: QueryService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Projects.create) {
            ok(projects.create(db, ctx.securityPrincipal, request.title))
        }

        implement(Projects.invite) {
            projects.inviteMember(db, ctx.securityPrincipal.username, request.projectId, request.username)
            ok(Unit)
        }

        implement(Projects.deleteMember) {
            projects.deleteMember(db, ctx.securityPrincipal.username, request.projectId, request.member)
            ok(Unit)
        }

        implement(Projects.changeUserRole) {
            projects.changeRoleOfMember(
                db,
                ctx.securityPrincipal.username,
                request.member,
                request.projectId,
                request.newRole
            )
            ok(Unit)
        }

        implement(Projects.viewMemberInProject) {
            val role = projects.findRoleOfMember(db, request.projectId, request.username)
                ?: throw ProjectException.NotFound()
            ok(ViewMemberInProjectResponse(ProjectMember(request.username, role)))
        }

        implement(Projects.listProjects) {
            val user = when (ctx.securityPrincipal.role) {
                in Roles.PRIVILEDGED -> {
                    request.user ?: ctx.securityPrincipal.username
                }
                else -> ctx.securityPrincipal.username
            }

            val pagination = when {
                request.itemsPerPage == null && request.page == null &&
                    ctx.securityPrincipal.role in Roles.PRIVILEDGED -> null

                else -> request.normalize()
            }

            ok(queries.listProjects(db, user, pagination))
        }

        implement(Projects.verifyMembership) {
            val project = ctx.project
            if (project == null) {
                ok(Unit)
            } else {
                projects.verifyMembership(db, ctx.securityPrincipal.username, project)
                ok(Unit)
            }
        }

        implement(Projects.acceptInvite) {
            projects.acceptInvite(db, ctx.securityPrincipal.username, request.projectId)
            ok(Unit)
        }

        implement(Projects.rejectInvite) {
            projects.rejectInvite(
                db,
                ctx.securityPrincipal.username,
                request.projectId,
                request.username ?: ctx.securityPrincipal.username
            )

            ok(Unit)
        }

        implement(Projects.leaveProject) {
            projects.leaveProject(
                db,
                ctx.securityPrincipal.username,
                ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            )

            ok(Unit)
        }

        implement(Projects.listIngoingInvites) {
            ok(queries.listIngoingInvites(db, ctx.securityPrincipal.username, request.normalize()))
        }

        implement(Projects.listOutgoingInvites) {
            ok(
                queries.listOutgoingInvites(
                    db,
                    ctx.securityPrincipal.username,
                    ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest),
                    request.normalize()
                )
            )
        }

        implement(Projects.transferPiRole) {
            projects.transferPrincipalInvestigatorRole(
                db,
                ctx.securityPrincipal.username,
                ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest),
                request.newPrincipalInvestigator
            )

            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
