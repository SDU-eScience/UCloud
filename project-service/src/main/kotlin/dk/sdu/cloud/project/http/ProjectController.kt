package dk.sdu.cloud.project.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.project.api.CreateProjectResponse
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.api.ViewMemberInProjectResponse
import dk.sdu.cloud.project.services.ProjectService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

class ProjectController(
    private val service: ProjectService<*>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ProjectDescriptions.create) {
            ok(CreateProjectResponse(service.create(request.title, request.principalInvestigator).id))
        }

        implement(ProjectDescriptions.delete) {
            service.delete(request.id)
            ok(Unit)
        }

        implement(ProjectDescriptions.addMember) {
            service.addMember(ctx.securityPrincipal.username, request.projectId, request.member)
            ok(Unit)
        }

        implement(ProjectDescriptions.deleteMember) {
            service.deleteMember(ctx.securityPrincipal.username, request.projectId, request.member)
            ok(Unit)
        }

        implement(ProjectDescriptions.changeUserRole) {
            service.changeMemberRole(ctx.securityPrincipal.username, request.projectId, request.member, request.newRole)
            ok(Unit)
        }

        implement(ProjectDescriptions.view) {
            ok(service.view(ctx.securityPrincipal.username, request.id))
        }

        implement(ProjectDescriptions.viewMemberInProject) {
            ok(ViewMemberInProjectResponse(service.viewMemberInProject(request.username, request.projectId)))
        }

        implement(ProjectDescriptions.listProjects) {
            ok(service.listProjects(ctx.securityPrincipal.username, request.normalize()))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
