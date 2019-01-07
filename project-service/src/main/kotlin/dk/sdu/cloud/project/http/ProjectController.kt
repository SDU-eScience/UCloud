package dk.sdu.cloud.project.http

import dk.sdu.cloud.project.api.CreateProjectResponse
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.api.ViewMemberInProjectResponse
import dk.sdu.cloud.project.services.ProjectService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.routing.Route

class ProjectController(
    private val service: ProjectService<*>
) : Controller {
    override val baseContext = ProjectDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ProjectDescriptions.create) { req ->
            ok(CreateProjectResponse(service.create(req.title, req.principalInvestigator).id))
        }

        implement(ProjectDescriptions.delete) { req ->
            service.delete(req.id)
            ok(Unit)
        }

        implement(ProjectDescriptions.addMember) { req ->
            service.addMember(call.securityPrincipal.username, req.projectId, req.member)
            ok(Unit)
        }

        implement(ProjectDescriptions.deleteMember) { req ->
            service.deleteMember(call.securityPrincipal.username, req.projectId, req.member)
            ok(Unit)
        }

        implement(ProjectDescriptions.changeUserRole) { req ->
            service.changeMemberRole(call.securityPrincipal.username, req.projectId, req.member, req.newRole)
            ok(Unit)
        }

        implement(ProjectDescriptions.view) { req ->
            ok(service.view(call.securityPrincipal.username, req.id))
        }

        implement(ProjectDescriptions.viewMemberInProject) { req ->
            ok(ViewMemberInProjectResponse(service.viewMemberInProject(req.username, req.projectId)))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
