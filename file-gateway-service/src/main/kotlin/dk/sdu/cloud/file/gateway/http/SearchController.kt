package dk.sdu.cloud.file.gateway.http

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.gateway.api.SearchGWDescriptions
import dk.sdu.cloud.file.gateway.api.resourcesToLoad
import dk.sdu.cloud.file.gateway.services.FileAnnotationService
import dk.sdu.cloud.file.gateway.services.UserCloudService
import dk.sdu.cloud.file.gateway.services.withNewItems
import dk.sdu.cloud.filesearch.api.FileSearchDescriptions
import dk.sdu.cloud.filesearch.api.SimpleSearchRequest
import dk.sdu.cloud.service.Controller

class SearchController(
    private val userCloudService: UserCloudService,
    private val fileAnnotationService: FileAnnotationService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(SearchGWDescriptions.simpleSearch) {
            val userCloud = userCloudService.createUserCloud(ctx as HttpCall)
            val pageOfFiles = FileSearchDescriptions.simpleSearch.call(
                SimpleSearchRequest(request.query, request.itemsPerPage, request.page),
                userCloud
            ).orThrow()

            ok(
                pageOfFiles.withNewItems(
                    fileAnnotationService.annotate(request.resourcesToLoad, pageOfFiles.items, userCloud)
                )
            )
        }

        implement(SearchGWDescriptions.advancedSearch) {
            val userCloud = userCloudService.createUserCloud(ctx as HttpCall)

            val pageOfFiles = FileSearchDescriptions.advancedSearch.call(
                request.request,
                userCloud
            ).orThrow()


            ok(
                pageOfFiles.withNewItems(
                    fileAnnotationService.annotate(request.resourcesToLoad, pageOfFiles.items, userCloud)
                )
            )
        }

        return@with
    }
}
