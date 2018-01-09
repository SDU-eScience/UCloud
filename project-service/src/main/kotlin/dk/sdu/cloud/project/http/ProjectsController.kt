package dk.sdu.cloud.project.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.services.ProjectsDAO
import dk.sdu.cloud.service.implement
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route

class ProjectsController(private val projects: ProjectsDAO) {
    fun configure(route: Route): Unit = with(route) {
        implement(ProjectDescriptions.findMyProjects) {
            val who = call.request.validatedPrincipal.subject
            ok(projects.findAllMyProjects(who))
        }

        implement(ProjectDescriptions.findById) {
            val project = projects.findById(it.id)
            if (project == null) {
                error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
            } else {
                ok(project)
            }
        }

        implement(ProjectDescriptions.findByIdWithMembers) {
            val projectWithMembers = projects.findByIdWithMembers(it.id)
            if (projectWithMembers == null) {
                error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
            } else {
                ok(projectWithMembers)
            }
        }
    }
}