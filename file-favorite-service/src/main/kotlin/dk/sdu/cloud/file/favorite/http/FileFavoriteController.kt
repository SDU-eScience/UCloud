package dk.sdu.cloud.file.favorite.http

import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.bearerAuth
import dk.sdu.cloud.file.favorite.api.FavoriteStatusResponse
import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteResponse
import dk.sdu.cloud.file.favorite.services.FileFavoriteService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.bearer
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.optionallyCausedBy
import dk.sdu.cloud.service.safeJobId
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.routing.Route

class FileFavoriteController<DBSession>(
    private val fileFavoriteService: FileFavoriteService<DBSession>,
    private val cloudContext: CloudContext
) : Controller {
    override val baseContext = FileFavoriteDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileFavoriteDescriptions.toggleFavorite) { req ->
            ok(
                ToggleFavoriteResponse(
                    fileFavoriteService.toggleFavorite(
                        req.files,
                        call.securityPrincipal.username,
                        cloudContext.bearerAuth(call.request.bearer!!).optionallyCausedBy(call.request.safeJobId)
                    )
                )
            )
        }

        implement(FileFavoriteDescriptions.favoriteStatus) { req ->
            ok(
                FavoriteStatusResponse(
                    fileFavoriteService.getFavoriteStatus(req.files, call.securityPrincipal.username)
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
