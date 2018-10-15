package dk.sdu.cloud.indexing.http

import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.services.IndexQueryService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.routing.Route

class QueryController(
    private val queryService: IndexQueryService
) : Controller {
    override val baseContext = QueryDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(QueryDescriptions.query) { req ->
            logEntry(log, req)

            ok(queryService.query(req.query, req.normalize()))
        }

        implement(QueryDescriptions.statistics) { req ->
            logEntry(log, req)

            ok(queryService.statisticsQuery(req))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
