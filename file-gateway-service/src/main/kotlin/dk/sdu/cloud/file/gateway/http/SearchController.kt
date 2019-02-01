package dk.sdu.cloud.file.gateway.http

import dk.sdu.cloud.file.gateway.api.SearchGWDescriptions
import dk.sdu.cloud.file.gateway.api.resourcesToLoad
import dk.sdu.cloud.file.gateway.services.FileAnnotationService
import dk.sdu.cloud.file.gateway.services.UserCloudService
import dk.sdu.cloud.file.gateway.services.withNewItems
import dk.sdu.cloud.filesearch.api.FileSearchDescriptions
import dk.sdu.cloud.filesearch.api.SimpleSearchRequest
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.orThrow
import io.ktor.routing.Route

class SearchController(
    private val userCloudService: UserCloudService,
    private val fileAnnotationService: FileAnnotationService
) : Controller {
    override val baseContext: String = SearchGWDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(SearchGWDescriptions.simpleSearch) { req ->
            val userCloud = userCloudService.createUserCloud(call)
            val pageOfFiles = FileSearchDescriptions.simpleSearch.call(
                SimpleSearchRequest(req.query, req.itemsPerPage, req.page),
                userCloud
            ).orThrow()

            ok(
                pageOfFiles.withNewItems(
                    fileAnnotationService.annotate(req.resourcesToLoad, pageOfFiles.items, userCloud)
                )
            )
        }

        implement(SearchGWDescriptions.advancedSearch) { req ->
            val userCloud = userCloudService.createUserCloud(call)

            val pageOfFiles = FileSearchDescriptions.advancedSearch.call(
                req.request,
                userCloud
            ).orThrow()


            ok(
                pageOfFiles.withNewItems(
                    fileAnnotationService.annotate(req.resourcesToLoad, pageOfFiles.items, userCloud)
                )
            )
        }

        return@with
    }
}
