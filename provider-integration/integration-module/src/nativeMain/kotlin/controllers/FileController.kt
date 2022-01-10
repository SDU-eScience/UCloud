package dk.sdu.cloud.controllers

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.http.HttpContext
import dk.sdu.cloud.http.OutgoingCallResponse
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.IpcServer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.FilePlugin
import dk.sdu.cloud.plugins.ProductBasedPlugins
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.storage.PathConverter
import dk.sdu.cloud.plugins.storage.UCloudFile
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class DownloadSessionWithPlugin(
    val pluginName: String,
    val session: String,
    val pluginData: String,
)

object FilesDownloadIpc : IpcContainer("files.download") {
    val register = updateHandler<DownloadSessionWithPlugin, Unit>("registerDownloadSession")
    val retrieve = retrieveHandler<FindByStringId, DownloadSessionWithPlugin>()
}

@Serializable
data class IMFileDownloadRequest(val token: String)

class FileController(
    controllerContext: ControllerContext,
    private val envoyConfig: EnvoyConfigurationService?,
) : BaseResourceController<Product.Storage, FSSupport, UFile, FilePlugin, FilesProvider>(controllerContext) {
    override fun retrievePlugins(): ProductBasedPlugins<FilePlugin>? = controllerContext.plugins.files
    override fun retrieveApi(providerId: String): FilesProvider = FilesProvider(providerId)

    override fun configureIpc(server: IpcServer) {
        if (controllerContext.configuration.serverMode != ServerMode.Server) return
        val idMapper = controllerContext.plugins.identityMapper ?: return
        val envoyConfig = envoyConfig ?: return

        server.addHandler(FilesDownloadIpc.register.handler { user, request ->
            val ucloudIdentity = with(controllerContext.pluginContext) {
                with(idMapper) {
                    runCatching {
                        lookupUCloudIdentifyFromLocalIdentity(
                            mapUidToLocalIdentity(user.uid.toInt())
                        )
                    }.getOrNull() ?: throw RPCException("Unknown user", HttpStatusCode.Forbidden)
                }
            }

            dbConnection.prepareStatement(
                """
                    insert into file_download_sessions(session, plugin_name, plugin_data)
                    values (:session, :plugin_name, :plugin_data)
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("session", request.session)
                    bindString("plugin_name", request.pluginName)
                    bindString("plugin_data", request.pluginData)
                }
            )

            envoyConfig.requestConfiguration(
                EnvoyRoute.DownloadSession(
                    request.session,
                    controllerContext.configuration.core.providerId,
                    ucloudIdentity
                ),
                null,
            )
        })

        server.addHandler(FilesDownloadIpc.retrieve.handler { _, request ->
            var result: DownloadSessionWithPlugin? = null
            dbConnection.prepareStatement(
                """
                    select plugin_name, plugin_data
                    from file_download_sessions
                    where
                        session = :token
                """
            ).useAndInvoke(
                prepare = {
                    bindString("token", request.id)
                },
                readRow = { row ->
                    val pluginName = row.getString(0)!!
                    val pluginData = row.getString(1)!!
                    result = DownloadSessionWithPlugin(pluginName, request.id, pluginData)
                }
            )

            result ?: throw RPCException("Invalid token supplied", HttpStatusCode.NotFound)
        })
    }

    override fun H2OServer.configureCustomEndpoints(plugins: ProductBasedPlugins<FilePlugin>, api: FilesProvider) {
        val pathConverter = PathConverter(controllerContext.pluginContext)
        val providerId = controllerContext.configuration.core.providerId

        val downloadApi = object : CallDescriptionContainer("file.${providerId}.download") {
            val download = call<IMFileDownloadRequest, Unit, CommonErrorMessage>("download") {
                audit<Unit>()
                auth {
                    access = AccessRight.READ
                    roles = Roles.PUBLIC
                }

                http {
                    method = HttpMethod.Get
                    path { using(downloadPath(providerId)) }
                    params { +boundTo(IMFileDownloadRequest::token) }
                }
            }
        }

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

        implement(api.createFolder) {
            val result = request.items.map { createFolderRequest ->
                val collection = pathConverter.ucloudToCollection(UCloudFile.create(createFolderRequest.id))

                val plugin = plugins.lookup(collection.specification.product)
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        createFolder(bulkRequestOf(createFolderRequest))
                    }
                }
                null
            }
            OutgoingCallResponse.Ok(BulkResponse(result))
        }

        implement(api.createDownload) {
            val sessions = ArrayList<FilesCreateDownloadResponseItem>()

            request.items.forEach { downloadRequest ->
                val (name, plugin) = plugins.lookupWithName(downloadRequest.resolvedCollection.specification.product)

                with(controllerContext.pluginContext) {
                    with(plugin) {
                        createDownload(bulkRequestOf(downloadRequest)).forEach {
                            sessions.add(FilesCreateDownloadResponseItem(
                                downloadPath(providerId, it.session)
                            ))

                            ipcClient.sendRequest(
                                FilesDownloadIpc.register,
                                DownloadSessionWithPlugin(name, it.session, it.pluginData)
                            )
                        }
                    }
                }

            }

            OutgoingCallResponse.Ok(BulkResponse(sessions))
        }

        implement(downloadApi.download) {
            val token = request.token
            with(controllerContext.pluginContext) {
                val handler = ipcClient.sendRequest(FilesDownloadIpc.retrieve, FindByStringId(token))
                val plugin = plugins.plugins[handler.pluginName]
                    ?: throw RPCException("Download is no longer valid", HttpStatusCode.NotFound)

                with(plugin) {
                    handleDownload(ctx.serverContext as HttpContext, handler.session, handler.pluginData)
                }
            }

            OutgoingCallResponse.AlreadyDelivered()
        }
    }

    companion object {
        fun downloadPath(providerId: String, token: String? = null): String = buildString {
            append("/ucloud/${providerId}/download")
            if (token != null) append("?token=$token")
        }
    }
}
