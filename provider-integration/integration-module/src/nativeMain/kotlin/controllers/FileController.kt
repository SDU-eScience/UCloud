package dk.sdu.cloud.controllers

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.http.HttpContext
import dk.sdu.cloud.http.RpcServer
import dk.sdu.cloud.calls.*
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
data class FileSessionWithPlugin(
    val pluginName: String,
    val session: String,
    val pluginData: String,
)

object FilesDownloadIpc : IpcContainer("files.download") {
    val register = updateHandler<FileSessionWithPlugin, Unit>("register")
    val retrieve = retrieveHandler<FindByStringId, FileSessionWithPlugin>()
}

object FilesUploadIpc : IpcContainer("files.upload") {
    val register = updateHandler<FileSessionWithPlugin, Unit>("register")
    val retrieve = retrieveHandler<FindByStringId, FileSessionWithPlugin>()
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
            var result: FileSessionWithPlugin? = null
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
                    result = FileSessionWithPlugin(pluginName, request.id, pluginData)
                }
            )

            result ?: throw RPCException("Invalid token supplied", HttpStatusCode.NotFound)
        })

        server.addHandler(FilesUploadIpc.register.handler { user, request ->
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
                    insert into file_upload_sessions(session, plugin_name, plugin_data)
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
                EnvoyRoute.UploadSession(
                    request.session,
                    controllerContext.configuration.core.providerId,
                    ucloudIdentity
                ),
                null,
            )
        })

        server.addHandler(FilesUploadIpc.retrieve.handler { _, request ->
            var result: FileSessionWithPlugin? = null
            dbConnection.prepareStatement(
                """
                    select plugin_name, plugin_data
                    from file_upload_sessions
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
                    result = FileSessionWithPlugin(pluginName, request.id, pluginData)
                }
            )

            result ?: throw RPCException("Invalid token supplied", HttpStatusCode.NotFound)
        })
    }

    override fun RpcServer.configureCustomEndpoints(plugins: ProductBasedPlugins<FilePlugin>, api: FilesProvider) {
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

        val uploadApi = object : CallDescriptionContainer("file.${providerId}.upload") {
            val upload = call<Unit, Unit, CommonErrorMessage>("upload") {
                audit<Unit>()
                auth {
                    access = AccessRight.READ
                    roles = Roles.PUBLIC
                }

                http {
                    method = HttpMethod.Post
                    path { using(uploadPath(providerId)) }
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
                LongRunningTask.Complete()
            }
            OutgoingCallResponse.Ok(BulkResponse(result))
        }

        implement(api.move) {
            val result = request.items.map { moveRequest ->
                val collection = moveRequest.resolvedNewCollection.specification.product

                val plugin = plugins.lookup(collection)
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        move(bulkRequestOf(moveRequest))
                    }
                }
                LongRunningTask.Complete()
            }
            OutgoingCallResponse.Ok(BulkResponse(result))
        }

        implement(api.copy) {
            val result = request.items.map { copyRequest ->
                val collection = copyRequest.resolvedNewCollection.specification.product
                val plugin = plugins.lookup(collection)
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        copy(bulkRequestOf(copyRequest))
                    }
                }
                LongRunningTask.Complete()
            }
            OutgoingCallResponse.Ok(BulkResponse(result))
        }

        implement(api.trash) {
            val result = request.items.map { request ->
                val collection = request.resolvedCollection.specification.product

                val plugin = plugins.lookup(collection)
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        moveToTrash(bulkRequestOf(request))
                    }
                }
                LongRunningTask.Complete()
            }
            OutgoingCallResponse.Ok(BulkResponse(result))
        }

        implement(api.emptyTrash) {
            val result = request.items.map { request ->
                val collection = request.resolvedCollection.specification.product

                val plugin = plugins.lookup(collection)
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        emptyTrash(bulkRequestOf(request))
                    }
                }
                LongRunningTask.Complete()
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
                                FileSessionWithPlugin(name, it.session, it.pluginData)
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

        implement(api.createUpload) {
            val sessions = ArrayList<FilesCreateUploadResponseItem>()

            request.items.forEach { uploadRequest ->
                val (name, plugin) = plugins.lookupWithName(uploadRequest.resolvedCollection.specification.product)

                with(controllerContext.pluginContext) {
                    with(plugin) {
                        createUpload(bulkRequestOf(uploadRequest)).forEach {
                            sessions.add(FilesCreateUploadResponseItem(
                                uploadPath(providerId),
                                UploadProtocol.CHUNKED,
                                it.session
                            ))

                            ipcClient.sendRequest(
                                FilesUploadIpc.register,
                                FileSessionWithPlugin(name, it.session, it.pluginData)
                            )
                        }
                    }
                }
            }

            OutgoingCallResponse.Ok(BulkResponse(sessions))
        }

        implement(uploadApi.upload) {
            val sctx = ctx.serverContext as? HttpContext ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            val offset = sctx.headers.find { it.header.equals("Chunked-Upload-Offset", true) }?.value?.toLongOrNull()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            val token = sctx.headers.find { it.header.equals("Chunked-Upload-Token", true) }?.value
                ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

            with(controllerContext.pluginContext) {
                val handler = ipcClient.sendRequest(FilesUploadIpc.retrieve, FindByStringId(token))
                val plugin = plugins.plugins[handler.pluginName]
                    ?: throw RPCException("Download is no longer valid", HttpStatusCode.NotFound)

                with(plugin) {
                    handleUpload(token, handler.pluginData, offset, sctx.payload)
                }
            }

            OutgoingCallResponse.Ok(Unit)
        }
    }

    companion object {
        fun downloadPath(providerId: String, token: String? = null): String = buildString {
            append("/ucloud/${providerId}/download")
            if (token != null) append("?token=$token")
        }

        fun uploadPath(providerId: String): String = buildString {
            append("/ucloud/${providerId}/chunked/upload")
        }
    }
}
