package dk.sdu.cloud.metadata.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.metadata.api.CreateProjectFromFormResponse
import dk.sdu.cloud.metadata.api.ProjectDescriptions
import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.metadata.api.UserEditableProjectMetadata
import dk.sdu.cloud.metadata.services.ElasticMetadataService
import dk.sdu.cloud.metadata.services.ProjectService
import dk.sdu.cloud.service.Controller
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory

class ProjectsController(
    private val projectService: ProjectService,
    private val elastic: ElasticMetadataService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ProjectDescriptions.create) {
            val id = if (checkMetadata(request)) {
                projectService.createProject(request.title!!, ctx.securityPrincipal.username)
            } else {
                return@implement error(CommonErrorMessage("Not correct metadata"), HttpStatusCode.BadRequest)
            }

            val projectMetadata = ProjectMetadata(
                title = request.title!!,
                description = request.description!!,
                license = request.license,
                projectId = id,
                keywords = request.keywords,
                notes = request.notes,
                contributors = request.contributors,
                references = request.references,
                grants = request.grants,
                subjects = request.subjects
            )

            elastic.create(projectMetadata)
            ok(CreateProjectFromFormResponse(id))
        }
    }

    private fun checkMetadata(metadata: UserEditableProjectMetadata): Boolean {
        if (metadata.title.isNullOrBlank() || metadata.description.isNullOrBlank()) return false
        return true
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProjectsController::class.java)
        const val PROJECT_ANNOTATION = "P"
    }
}

