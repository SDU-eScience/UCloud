package dk.sdu.cloud.indexing.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.SizeResponse
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Page

/**
 * A controller for [QueryDescriptions]
 */
class QueryController(
    private val queryService: ElasticQueryService
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
                    items = results.items.map { it.toMaterializedFile() }
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

        implement(QueryDescriptions.size) {
            ok(SizeResponse(queryService.calculateSize(request.paths)))
        }
    }

    private fun String.normalizePath(): String {
        return normalize().removeSuffix("/")
    }

    companion object : Loggable {
        override val log = logger()
    }
}
