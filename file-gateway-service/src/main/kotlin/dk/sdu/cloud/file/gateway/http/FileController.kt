package dk.sdu.cloud.file.gateway.http

import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.ListDirectoryRequest
import dk.sdu.cloud.file.api.LookupFileInDirectoryRequest
import dk.sdu.cloud.file.gateway.api.FileGatewayDescriptions
import dk.sdu.cloud.file.gateway.api.StatResponse
import dk.sdu.cloud.file.gateway.api.resourcesToLoad
import dk.sdu.cloud.file.gateway.services.FileAnnotationService
import dk.sdu.cloud.file.gateway.services.UserCloudService
import dk.sdu.cloud.file.gateway.services.withNewItems
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.orThrow
import io.ktor.routing.Route

class FileController(
    private val userCloudService: UserCloudService,
    private val fileAnnotationService: FileAnnotationService
) : Controller {
    override val baseContext = FileGatewayDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileGatewayDescriptions.listAtDirectory) { req ->
            val userCloud = userCloudService.createUserCloud(call)

            val pageOfFiles = FileDescriptions.listAtPath.call(
                ListDirectoryRequest(
                    path = req.path,
                    itemsPerPage = req.itemsPerPage,
                    page = req.page,
                    order = req.order,
                    sortBy = req.sortBy
                ),
                userCloud
            ).orThrow()

            ok(
                pageOfFiles.withNewItems(
                    fileAnnotationService.annotate(req.resourcesToLoad, pageOfFiles.items, userCloud)
                )
            )
        }

        implement(FileGatewayDescriptions.lookupFileInDirectory) { req ->
            val userCloud = userCloudService.createUserCloud(call)

            val result = FileDescriptions.lookupFileInDirectory.call(
                LookupFileInDirectoryRequest(
                    req.path,
                    req.itemsPerPage,
                    req.order,
                    req.sortBy
                ),
                userCloud
            ).orThrow()

            ok(
                result.withNewItems(
                    fileAnnotationService.annotate(req.resourcesToLoad, result.items, userCloud)
                )
            )
        }

        implement(FileGatewayDescriptions.stat) { req ->
            val userCloud = userCloudService.createUserCloud(call)

            val result = FileDescriptions.stat.call(
                FindByPath(req.path),
                userCloud
            ).orThrow()

            ok(fileAnnotationService.annotate(req.resourcesToLoad, listOf(result), userCloud).single())
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
