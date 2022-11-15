package dk.sdu.cloud.controllers

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.config.VerifiedConfig
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.ipc.*
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.secureToken
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.coroutines.coroutineContext

@Serializable
data class FileSessionWithPlugin(
    val pluginName: String,
    val session: String,
    val pluginData: String,
)

object FilesDownloadIpc : IpcContainer("files.download") {
    val register = updateHandler("register", FileSessionWithPlugin.serializer(), Unit.serializer())
    val retrieve = retrieveHandler(FindByStringId.serializer(), FileSessionWithPlugin.serializer())
}

object FilesUploadIpc : IpcContainer("files.upload") {
    val register = updateHandler("register", FileSessionWithPlugin.serializer(), Unit.serializer())
    val retrieve = retrieveHandler(FindByStringId.serializer(), FileSessionWithPlugin.serializer())
}

@Serializable
data class TaskSpecification(val title: String)

// TODO(Dan): Move this somewhere else
object TaskIpc : IpcContainer("tasks") {
    val register = createHandler(TaskSpecification.serializer(), FindByStringId.serializer())
    val markAsComplete = updateHandler("markAsComplete", FindByStringId.serializer(), Unit.serializer())
}

@Serializable
data class IMFileDownloadRequest(val token: String)

class FileController(
    controllerContext: ControllerContext,
    private val envoyConfig: EnvoyConfigurationService?,
) : BaseResourceController<Product.Storage, FSSupport, UFile, FilePlugin, FilesProvider>(controllerContext) {
    override fun retrievePlugins() = controllerContext.configuration.plugins.files.values
    override fun retrieveApi(providerId: String): FilesProvider = FilesProvider(providerId)

    override fun configureIpc(server: IpcServer) {
        if (!controllerContext.configuration.shouldRunServerCode()) return
        val envoyConfig = envoyConfig ?: return

        server.addHandler(TaskIpc.register.handler { user, request ->
            UserMapping.localIdToUCloudId(user.uid)
                ?: throw RPCException("Unknown user", HttpStatusCode.BadRequest)

            dbConnection.withSession { session ->
                val id = secureToken(32).replace("/", "-")

                session.prepareStatement(
                    """
                        insert into tasks(title, ucloud_task_id, local_identity)
                        values (:title, :task_id, :local)
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("title", request.title)
                        bindString("task_id", id)
                        bindString("local", user.uid.toString())
                    }
                )

                FindByStringId(id)
            }
        })

        server.addHandler(TaskIpc.markAsComplete.handler { user, request ->
            UserMapping.localIdToUCloudId(user.uid)
                ?: throw RPCException("Unknown user", HttpStatusCode.BadRequest)

            var doesExist = false
            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                        select ucloud_task_id
                        from tasks
                        where
                            ucloud_task_id = :task_id and
                            local_identity = :local_id
                    """
                ).useAndInvoke(
                    prepare = {
                        bindString("task_id", request.id)
                        bindString("local_id", user.uid.toString())
                    },
                    readRow = { doesExist = true }
                )
            }

            if (!doesExist) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            FilesControl.markAsComplete.callBlocking(
                bulkRequestOf(FilesControlMarkAsCompleteRequestItem(request.id)),
                controllerContext.pluginContext.rpcClient
            ).orThrow()
        })

        server.addHandler(FilesDownloadIpc.register.handler { user, request ->
            val ucloudIdentity = if (controllerContext.configuration.core.launchRealUserInstances) {
                UserMapping.localIdToUCloudId(user.uid) ?: throw RPCException("Unknown user", HttpStatusCode.BadRequest)
            } else {
                null
            }

            dbConnection.withSession { session ->
                session.prepareStatement(
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
            }
        })

        server.addHandler(FilesDownloadIpc.retrieve.handler { _, request ->
            var result: FileSessionWithPlugin? = null
            dbConnection.withSession { session ->
                session.prepareStatement(
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
            }

            result ?: throw RPCException("Invalid token supplied", HttpStatusCode.NotFound)
        })

        server.addHandler(FilesUploadIpc.register.handler { user, request ->
            val ucloudIdentity = if (controllerContext.configuration.core.launchRealUserInstances) {
                UserMapping.localIdToUCloudId(user.uid) ?: throw RPCException("Unknown user", HttpStatusCode.BadRequest)
            } else {
                null
            }

            dbConnection.withSession { session ->
                session.prepareStatement(
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
            }
        })

        server.addHandler(FilesUploadIpc.retrieve.handler { _, request ->
            var result: FileSessionWithPlugin? = null
            dbConnection.withSession { session ->
                session.prepareStatement(
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
            }


            result ?: throw RPCException("Invalid token supplied", HttpStatusCode.NotFound)
        })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun RpcServer.configureCustomEndpoints(plugins: Collection<FilePlugin>, api: FilesProvider) {
        val providerId = controllerContext.configuration.core.providerId

        val downloadApi = object : CallDescriptionContainer("file.${providerId}.download") {
            val download = call(
                "download",
                IMFileDownloadRequest.serializer(),
                Unit.serializer(),
                CommonErrorMessage.serializer()
            ) {
                audit(Unit.serializer())
                auth {
                    access = AccessRight.READ
                    roles = Roles.PUBLIC
                }

                http {
                    method = HttpMethod.Get
                    path { using(downloadPath(null, providerId)) }
                    params { +boundTo(IMFileDownloadRequest::token) }
                }
            }
        }

        val uploadApi = object : CallDescriptionContainer("file.${providerId}.upload") {
            val upload = call("upload", Unit.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
                audit(Unit.serializer())
                auth {
                    access = AccessRight.READ
                    roles = Roles.PUBLIC
                }

                http {
                    method = HttpMethod.Post
                    path { using(uploadPath(null, providerId)) }
                }
            }
        }

        implement(api.browse) {
            val path = request.browse.flags.path
                ?: throw RPCException("Bad request from UCloud (no  path)", HttpStatusCode.BadRequest)

            val plugin = lookupPlugin(request.resolvedCollection.specification.product)
            with(requestContext(controllerContext)) {
                with(plugin) {
                    ok(browse(UCloudFile.create(path), request))
                }
            }
        }

        implement(api.retrieve) {
            val plugin = lookupPlugin(request.resolvedCollection.specification.product)
            with(requestContext(controllerContext)) {
                with(plugin) {
                    ok(retrieve(request))
                }
            }
        }

        implement(api.createFolder) {
            val result = dispatchToPlugin(
                plugins = plugins,
                items = request.items,
                selector = { collectionCache.get(it.id.components().firstOrNull()!!)!! },
                dispatcher = { plugin, request ->
                    with(plugin) {
                        BulkResponse(createFolder(request))
                    }
                }
            )

            ok(result)
        }

        implement(api.move) {
            val result = dispatchToPlugin(
                plugins = plugins,
                items = request.items,
                selector = { it.resolvedNewCollection },
                dispatcher = { plugin, request ->
                    with(plugin) {
                        BulkResponse(move(request))
                    }
                }
            )

            ok(result)
        }

        implement(api.copy) {
            val result = dispatchToPlugin(
                plugins = plugins,
                items = request.items,
                selector = { it.resolvedNewCollection },
                dispatcher = { plugin, request ->
                    with(plugin) {
                        BulkResponse(copy(request))
                    }
                }
            )

            ok(result)
        }

        implement(api.trash) {
            val response = dispatchToPlugin(plugins, request.items, { it.resolvedCollection }) { plugin, request ->
                with(plugin) {
                    BulkResponse(moveToTrash(request))
                }
            }
            ok(response)
        }

        implement(api.emptyTrash) {
            val result = dispatchToPlugin(
                plugins = plugins,
                items = request.items,
                selector = { it.resolvedCollection },
                dispatcher = { plugin, request ->
                    with(plugin) {
                        BulkResponse(emptyTrash(request))
                    }
                }
            )

            ok(result)
        }

        implement(api.createDownload) {
            val sessions = ArrayList<FilesCreateDownloadResponseItem>()

            request.items.forEach { downloadRequest ->
                val plugin = lookupPlugin(downloadRequest.resolvedCollection.specification.product)
                val name = plugin.pluginName

                with(requestContext(controllerContext)) {
                    with(plugin) {
                        createDownload(bulkRequestOf(downloadRequest)).forEach {
                            sessions.add(
                                FilesCreateDownloadResponseItem(
                                    downloadPath(config, providerId, it.session)
                                )
                            )

                            ipcClient.sendRequest(
                                FilesDownloadIpc.register,
                                FileSessionWithPlugin(name, it.session, it.pluginData)
                            )
                        }
                    }
                }

            }

            ok(BulkResponse(sessions))
        }

        implement(downloadApi.download) {
            val config = controllerContext.configuration
            if (config.shouldRunServerCode() && !config.shouldRunUserCode()) {
                // NOTE(Dan): For some reason, it would appear that the configuration in Envoy is not yet active.
                // Delay for a small while and ask the client to retry at almost the same address.
                val sctx = (ctx as? HttpCall)
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                val attempt = sctx.ktor.call.request.queryParameters["attempt"]?.toIntOrNull() ?: 0
                if (attempt > 5) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                delay(100)

                sctx.ktor.call.respondRedirect(
                    downloadPath(config, controllerContext.configuration.core.providerId, request.token) +
                            "&attempt=${attempt + 1}"
                )

                okContentAlreadyDelivered()
                return@implement
            }

            val token = request.token
            with(requestContext(controllerContext)) {
                val handler = ipcClient.sendRequest(FilesDownloadIpc.retrieve, FindByStringId(token))

                val plugin = controllerContext.configuration.plugins.files[handler.pluginName]
                    ?: throw RPCException("Download is no longer valid", HttpStatusCode.NotFound)

                with(plugin) {
                    handleDownload(ctx as HttpCall, handler.session, handler.pluginData)
                }
            }

            okContentAlreadyDelivered()
        }

        implement(api.createUpload) {
            val sessions = ArrayList<FilesCreateUploadResponseItem>()

            request.items.forEach { uploadRequest ->
                val plugin = lookupPlugin(uploadRequest.resolvedCollection.specification.product)
                val name = plugin.pluginName

                with(requestContext(controllerContext)) {
                    with(plugin) {
                        createUpload(bulkRequestOf(uploadRequest)).forEach {
                            sessions.add(
                                FilesCreateUploadResponseItem(
                                    uploadPath(config, providerId),
                                    UploadProtocol.CHUNKED,
                                    it.session
                                )
                            )

                            ipcClient.sendRequest(
                                FilesUploadIpc.register,
                                FileSessionWithPlugin(name, it.session, it.pluginData)
                            )
                        }
                    }
                }
            }

            ok(BulkResponse(sessions))
        }

        implement(uploadApi.upload) {
            val sctx = ctx as? HttpCall ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            val offset = sctx.ktor.call.request.header("Chunked-Upload-Offset")?.toLongOrNull()
                ?: throw RPCException("Missing or invalid offset", HttpStatusCode.BadRequest)

            val token = sctx.ktor.call.request.header("Chunked-Upload-Token")
                ?: throw RPCException("Missing or invalid token", HttpStatusCode.BadRequest)

            with(requestContext(controllerContext)) {
                val handler = ipcClient.sendRequest(FilesUploadIpc.retrieve, FindByStringId(token))
                val plugin = controllerContext.configuration.plugins.files[handler.pluginName]
                    ?: throw RPCException("Download is no longer valid", HttpStatusCode.NotFound)

                with(plugin) {
                    handleUpload(
                        token,
                        handler.pluginData,
                        offset,
                        sctx.ktor.call.request.receiveChannel()
                    )
                }
            }

            ok(Unit)
        }

        implement(api.streamingSearch) {
            val currentFolder = request.currentFolder
            val relevantPlugins = if (currentFolder != null) {
                val collection = currentFolder.components().getOrNull(0)?.let { id ->
                    collectionCache.get(id)
                } ?: run {
                    ok(FilesProviderStreamingSearchResult.EndOfResults())
                    return@implement
                }

                val plugin = lookupPluginOrNull(collection.specification.product) ?: run {
                    ok(FilesProviderStreamingSearchResult.EndOfResults())
                    return@implement
                }

                listOf(plugin)
            } else {
                retrievePlugins().filter { plugin ->
                    plugin.productAllocationResolved.any { it.category == request.category }
                }
            }

            // NOTE(Dan): We throttle the number of batches we send out to aid clients a bit with the rendering
            // process. We attempt to not send more than 4 batches per second but the first batch will go out as soon
            // as it is ready. This variable controls when we are allowed to send the next batch. An onTimeout in the
            // select call below ensures that we send out all batches in a timely manner.
            var nextAllowedSend = 0L

            with(requestContext(controllerContext)) {
                val channels = relevantPlugins.mapNotNull { plugin ->
                    with(plugin) {
                        runCatching { streamingSearch(request) }.getOrNull()
                    }
                }

                val batch = ArrayList<PartialUFile>()
                while (coroutineContext.isActive) {
                    var isAllClosed = true
                    val result = select<FilesProviderStreamingSearchResult?> {
                        for (channel in channels) {
                            if (channel.isClosedForReceive) continue

                            isAllClosed = false
                            channel.onReceiveCatching { message ->
                                message.getOrNull()
                            }
                        }

                        onTimeout(50) { null }
                    }

                    if (result != null) {
                        if (result is FilesProviderStreamingSearchResult.Result) {
                            batch.addAll(result.batch)
                        }
                    }

                    val now = Time.now()
                    if (batch.isNotEmpty() && now > nextAllowedSend) {
                        sendWSMessage(FilesProviderStreamingSearchResult.Result(batch))
                        batch.clear()
                        nextAllowedSend = now + 250
                    }

                    if (isAllClosed) break
                }

                if (batch.isNotEmpty()) {
                    sendWSMessage(FilesProviderStreamingSearchResult.Result(batch))
                }
            }

            ok(FilesProviderStreamingSearchResult.EndOfResults())
        }
    }

    private val collectionCache = SimpleCache<String, FileCollection>(
        maxAge = 60_000 * 10L,
        lookup = { collectionId ->
            FileCollectionsControl.retrieve.call(
                ResourceRetrieveRequest(FileCollectionIncludeFlags(), collectionId),
                controllerContext.pluginContext.rpcClient
            ).orThrow()
        }
    )

    companion object {
        fun downloadPath(config: VerifiedConfig?, providerId: String, token: String? = null): String = buildString {
            if (config != null) append(config.core.hosts.self?.toStringOmitDefaultPort() ?: "")
            append("/ucloud/${providerId}/download")
            if (token != null) append("?token=$token")
        }

        fun uploadPath(config: VerifiedConfig?, providerId: String): String = buildString {
            if (config != null) append(config.core.hosts.self?.toStringOmitDefaultPort() ?: "")
            append("/ucloud/${providerId}/chunked/upload")
        }
    }
}
