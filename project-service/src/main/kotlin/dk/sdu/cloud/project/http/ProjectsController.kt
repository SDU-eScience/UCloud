package dk.sdu.cloud.project.http

import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.services.ProjectsDAO
import dk.sdu.cloud.service.implement
import io.ktor.routing.Route

class ProjectsController(private val projects: ProjectsDAO) {
    fun configure(route: Route): Unit = with(route) {
        implement(ProjectDescriptions.findMyProjects) {
            val who = call.request.validatedPrincipal.subject
            ok(projects.findAllMyProjects(who))

            // use error() when an error occurs
            // error(CommonErrorMessage("Something went wrong!", HttpStatusCode.InternalServerError))
        }

        implement(ProjectDescriptions.findById) {
            // TODO Implement
        }
    }
}