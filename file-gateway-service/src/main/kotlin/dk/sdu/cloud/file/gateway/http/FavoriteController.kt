package dk.sdu.cloud.file.gateway.http

import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.file.favorite.api.ListRequest
import dk.sdu.cloud.file.gateway.api.FavoriteGWDescriptions
import dk.sdu.cloud.file.gateway.api.resourcesToLoad
import dk.sdu.cloud.file.gateway.services.FileAnnotationService
import dk.sdu.cloud.file.gateway.services.UserCloudService
import dk.sdu.cloud.file.gateway.services.withNewItems
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.orThrow
import io.ktor.routing.Route

class FavoriteController(
    private val userCloudService: UserCloudService,
    private val fileAnnotationService: FileAnnotationService
) : Controller {
    override val baseContext: String = FavoriteGWDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FavoriteGWDescriptions.list) { req ->
            val userCloud = userCloudService.createUserCloud(call)

            val pageOfFiles = FileFavoriteDescriptions.list.call(
                ListRequest(req.itemsPerPage, req.page),
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
