package dk.sdu.cloud.file.gateway.http

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.gateway.api.ShareGWDescriptions
import dk.sdu.cloud.file.gateway.api.resourcesToLoad
import dk.sdu.cloud.file.gateway.services.FileAnnotationService
import dk.sdu.cloud.file.gateway.services.UserCloudService
import dk.sdu.cloud.file.gateway.services.withNewItems
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.share.api.Shares

class ShareController(
    private val userCloudService: UserCloudService,
    private val fileAnnotationService: FileAnnotationService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ShareGWDescriptions.listFiles) {
            val userCloud = userCloudService.createUserCloud(ctx as HttpCall)

            val pageOfFiles = Shares.listFiles.call(
                Shares.ListFiles.Request(request.itemsPerPage, request.page),
                userCloud
            ).orThrow()

            ok(
                pageOfFiles.withNewItems(
                    fileAnnotationService.annotate(request.resourcesToLoad, pageOfFiles.items, userCloud)
                )
            )
        }
    }
}
