package dk.sdu.cloud.indexing.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.ReverseLookupFilesResponse
import dk.sdu.cloud.indexing.api.ReverseLookupResponse
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

/**
 * A controller for [LookupDescriptions]
 */
class LookupController(
    private val lookupService: ElasticQueryService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(LookupDescriptions.reverseLookup) {
            ok(
                ReverseLookupResponse(
                    lookupService.reverseLookupBatch(request.allIds)
                )
            )
        }

        implement(LookupDescriptions.reverseLookupFiles) {
            val results = lookupService.reverseLookupFileBatch(request.allIds)
            val idToFileWithSensitivity =
                lookupService.lookupInheritedSensitivity(results.filterNotNull()).associateBy { it.fileId }

            ok(
                ReverseLookupFilesResponse(
                    results.map {
                        if (it == null) null
                        else idToFileWithSensitivity[it.fileId]
                    }
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
