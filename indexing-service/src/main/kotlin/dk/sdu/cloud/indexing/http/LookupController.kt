package dk.sdu.cloud.indexing.http

import dk.sdu.cloud.filesearch.api.LookupDescriptions
import dk.sdu.cloud.filesearch.api.ReverseLookupResponse
import dk.sdu.cloud.indexing.services.ReverseLookupService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.routing.Route

class LookupController(
    private val lookupService: ReverseLookupService
) : Controller {
    override val baseContext = LookupDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(LookupDescriptions.reverseLookup) { req ->
            logEntry(log, req)
            ok(ReverseLookupResponse(lookupService.reverseLookupBatch(req.allIds)))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}