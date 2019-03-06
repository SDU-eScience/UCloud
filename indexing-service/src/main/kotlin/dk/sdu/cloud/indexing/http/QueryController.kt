package dk.sdu.cloud.indexing.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.services.IndexQueryService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Page

/**
 * A controller for [QueryDescriptions]
 */
class QueryController(
    private val queryService: IndexQueryService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(QueryDescriptions.query) {
            val results = queryService.query(
                request.query.copy(roots = request.query.roots.map { it.normalizePath() }),
                request.normalize(),
                request.sortBy
            )

            ok(
                Page(
                    itemsInTotal = results.itemsInTotal,
                    itemsPerPage = results.itemsPerPage,
                    pageNumber = results.pageNumber,
                    items = queryService.lookupInheritedSensitivity(results.items)
                )
            )
        }

        implement(QueryDescriptions.statistics) {
            ok(
                queryService.statisticsQuery(
                    request.copy(
                        query = request.query.copy(
                            roots = request.query.roots.map { it.normalizePath() }
                        )
                    )
                )
            )
        }
    }

    private fun String.normalizePath(): String {
        return normalize().removeSuffix("/")
    }

    companion object : Loggable {
        override val log = logger()
    }
}
