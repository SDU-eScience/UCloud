package dk.sdu.cloud.indexing.http

import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.services.IndexQueryService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import io.ktor.routing.Route

/**
 * A controller for [QueryDescriptions]
 */
class QueryController(
    private val queryService: IndexQueryService
) : Controller {
    override val baseContext = QueryDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(QueryDescriptions.query) { req ->
            ok(queryService.query(req.query, req.normalize()))
        }

        implement(QueryDescriptions.statistics) { req ->
            ok(queryService.statisticsQuery(req))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
