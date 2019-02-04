package dk.sdu.cloud.indexing.http

import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.ReverseLookupFilesResponse
import dk.sdu.cloud.indexing.api.ReverseLookupResponse
import dk.sdu.cloud.indexing.services.ReverseLookupService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import io.ktor.routing.Route

/**
 * A controller for [LookupDescriptions]
 */
class LookupController(
    private val lookupService: ReverseLookupService
) : Controller {
    override val baseContext = LookupDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(LookupDescriptions.reverseLookup) { req ->
            ok(ReverseLookupResponse(lookupService.reverseLookupBatch(req.allIds)))
        }

        implement(LookupDescriptions.reverseLookupFiles) { req ->
            ok(ReverseLookupFilesResponse(lookupService.reverseLookupFileBatch(req.allIds)))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
