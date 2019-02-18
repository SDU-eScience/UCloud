package dk.sdu.cloud.metadata.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.metadata.api.MetadataDescriptions
import dk.sdu.cloud.metadata.api.MetadataQueryDescriptions
import dk.sdu.cloud.metadata.api.ProjectMetadataWithRightsInfo
import dk.sdu.cloud.metadata.services.MetadataAdvancedQueryService
import dk.sdu.cloud.metadata.services.MetadataCommandService
import dk.sdu.cloud.metadata.services.MetadataQueryService
import dk.sdu.cloud.metadata.services.tryWithProject
import dk.sdu.cloud.service.Controller
import io.ktor.http.HttpStatusCode

class MetadataController(
    private val metadataCommandService: MetadataCommandService,
    private val metadataQueryService: MetadataQueryService,
    private val metadataAdvancedQueryService: MetadataAdvancedQueryService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(MetadataDescriptions.updateProjectMetadata) {
            metadataCommandService.update(ctx.securityPrincipal.username, request.id, request)
            ok(Unit)
        }

        implement(MetadataDescriptions.findById) {
            val result = metadataQueryService.getById(ctx.securityPrincipal.username, request.id)
            if (result == null) {
                error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
            } else {
                val canEdit = metadataCommandService.canEdit(ctx.securityPrincipal.username, request.id)
                ok(ProjectMetadataWithRightsInfo(result, canEdit = canEdit))
            }
        }

        implement(MetadataQueryDescriptions.simpleQuery) {
            tryWithProject {
                ok(
                    metadataAdvancedQueryService.simpleQuery(
                        ctx.securityPrincipal.username,
                        request.query,
                        request.normalize()
                    )
                )
            }
        }
    }
}
