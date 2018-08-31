package dk.sdu.cloud.metadata.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.currentUsername
import dk.sdu.cloud.metadata.api.MetadataDescriptions
import dk.sdu.cloud.metadata.api.MetadataQueryDescriptions
import dk.sdu.cloud.metadata.api.ProjectMetadataWithRightsInfo
import dk.sdu.cloud.metadata.services.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.LoggerFactory

class MetadataController(
    private val metadataCommandService: MetadataCommandService,
    private val metadataQueryService: MetadataQueryService,
    private val metadataAdvancedQueryService: MetadataAdvancedQueryService,

    private val projectService: ProjectService<*>
) : Controller {
    override val baseContext: String = MetadataDescriptions.baseContext

    override fun configure(routing: Route) = with(routing) {
        implement(MetadataDescriptions.updateProjectMetadata) {
            logEntry(log, it)

            metadataCommandService.update(call.request.currentUsername, it.id, it)
            ok(Unit)
        }

        implement(MetadataDescriptions.findById) {
            logEntry(log, it)

            val result = metadataQueryService.getById(call.request.currentUsername, it.id)
            if (result == null) {
                error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
            } else {
                val canEdit = metadataCommandService.canEdit(call.request.currentUsername, it.id)
                ok(ProjectMetadataWithRightsInfo(result, canEdit = canEdit))
            }
        }

        implement(MetadataDescriptions.findByPath) {
            logEntry(log, it)

            try {
                val project = projectService.findByFSRoot(it.path)
                val projectId = project.id!!

                // TODO Bad copy & paste
                val result = metadataQueryService.getById(call.request.currentUsername, projectId)
                if (result == null) {
                    error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
                } else {
                    val canEdit = metadataCommandService.canEdit(call.request.currentUsername, projectId)
                    ok(ProjectMetadataWithRightsInfo(result, canEdit = canEdit))
                }
            } catch (e: ProjectException.NotFound) {
                error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
            }
        }

        implement(MetadataQueryDescriptions.simpleQuery) {
            logEntry(log, it)

            tryWithProject {
                ok(metadataAdvancedQueryService.simpleQuery(call.request.currentUsername, it.query, it.normalize()))
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MetadataController::class.java)
    }
}