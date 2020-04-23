package dk.sdu.cloud.project.rpc

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.project.api.CreateProjectResponse
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.ShouldVerifyMembershipResponse
import dk.sdu.cloud.project.api.ViewMemberInProjectResponse
import dk.sdu.cloud.project.services.ProjectService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

class ProjectController(
    private val service: ProjectService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Projects.create) {
            ok(CreateProjectResponse(service.create(request.title, request.principalInvestigator).id))
        }

        implement(Projects.addMember) {
            service.addMember(ctx.securityPrincipal.username, request.projectId, request.member)
            ok(Unit)
        }

        implement(Projects.deleteMember) {
            service.deleteMember(ctx.securityPrincipal.username, request.projectId, request.member)
            ok(Unit)
        }

        implement(Projects.changeUserRole) {
            service.changeMemberRole(ctx.securityPrincipal.username, request.projectId, request.member, request.newRole)
            ok(Unit)
        }

        implement(Projects.view) {
            ok(service.view(ctx.securityPrincipal.username, request.id))
        }

        implement(Projects.viewMemberInProject) {
            ok(ViewMemberInProjectResponse(service.viewMemberInProject(request.username, request.projectId)))
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

            ok(service.listProjects(user, pagination))
        }

        implement(Projects.shouldVerifyMembership) {
            val project = ctx.project
            val shouldVerify = if (project == null) {
                false
            } else {
                service.shouldVerify(ctx.securityPrincipal.username, project)
            }

            ok(ShouldVerifyMembershipResponse(shouldVerify))
        }

        implement(Projects.verifyMembership) {
            val project = ctx.project
            if (project == null) {
                ok(Unit)
            } else {
                service.verifyMembership(ctx.securityPrincipal.username, project)
                ok(Unit)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
