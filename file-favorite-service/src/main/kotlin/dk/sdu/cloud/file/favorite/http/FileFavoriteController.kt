package dk.sdu.cloud.file.favorite.http

import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import io.ktor.routing.Route

class FileFavoriteController : Controller {
    override val baseContext = FileFavoriteDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileFavoriteDescriptions.call) {
            // Implement call here
            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
