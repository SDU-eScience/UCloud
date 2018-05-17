package dk.sdu.cloud.metadata.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.currentUsername
import dk.sdu.cloud.metadata.api.MetadataDescriptions
import dk.sdu.cloud.metadata.api.MetadataQueryDescriptions
import dk.sdu.cloud.metadata.services.MetadataAdvancedQueryService
import dk.sdu.cloud.metadata.services.MetadataCommandService
import dk.sdu.cloud.metadata.services.MetadataQueryService
import dk.sdu.cloud.metadata.services.tryWithProject
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.LoggerFactory

class MetadataController(
    private val metadataCommandService: MetadataCommandService,
    private val metadataQueryService: MetadataQueryService,
    private val metadataAdvancedQueryService: MetadataAdvancedQueryService
) {
    fun configure(routing: Route) = with(routing) {
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
                ok(result)
            }
        }

        implement(MetadataQueryDescriptions.simpleQuery) {
            logEntry(log, it)

            tryWithProject {
                ok(metadataAdvancedQueryService.simpleQuery(call.request.currentUsername, it.query, it.pagination))
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MetadataController::class.java)
    }
}