package dk.sdu.cloud.controllers

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.FSSupport
import dk.sdu.cloud.file.orchestrator.api.FilesProvider
import dk.sdu.cloud.file.orchestrator.api.UFile
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.http.OutgoingCallResponse
import dk.sdu.cloud.plugins.FilePlugin
import dk.sdu.cloud.plugins.ProductBasedPlugins
import dk.sdu.cloud.plugins.storage.PathConverter
import dk.sdu.cloud.plugins.storage.UCloudFile
import io.ktor.http.*

class FileController(
    controllerContext: ControllerContext
) : BaseResourceController<Product.Storage, FSSupport, UFile, FilePlugin, FilesProvider>(controllerContext) {
    override fun retrievePlugins(): ProductBasedPlugins<FilePlugin>? = controllerContext.plugins.files
    override fun retrieveApi(providerId: String): FilesProvider = FilesProvider(providerId)

    override fun H2OServer.configureCustomEndpoints(plugins: ProductBasedPlugins<FilePlugin>, api: FilesProvider) {
        implement(api.browse) {
            val path = request.browse.flags.path
                ?: throw RPCException("Bad request from UCloud (no  path)", HttpStatusCode.BadRequest)

            val plugin = plugins.lookup(request.resolvedCollection.specification.product)
            with(controllerContext.pluginContext) {
                with(plugin) {
                    OutgoingCallResponse.Ok(browse(UCloudFile.create(path), request))
                }
            }
        }

        implement(api.retrieve) {
            val plugin = plugins.lookup(request.resolvedCollection.specification.product)
            with(controllerContext.pluginContext) {
                with(plugin) {
                    OutgoingCallResponse.Ok(retrieve(request))
                }
            }
        }
    }
}
