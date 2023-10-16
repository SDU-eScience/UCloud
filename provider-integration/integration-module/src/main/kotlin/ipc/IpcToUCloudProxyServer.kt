package dk.sdu.cloud.ipc

import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobIncludeFlags
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.controllers.UserMapping
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsControl
import dk.sdu.cloud.utils.ResourceVerification.verifyAccessToResource
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.SimpleCache
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

// NOTE(Dan): The IpcToUCloudProxyServer allows plugins and user-instances semi-direct access to RPC with UCloud/Core.
// User-instances are even given a "normal" RPC client which will have an identical API to the normal RPC client.
// However, this proxy server _must_ verify that the end-user making the IPC call is actually authorized to do such an
// action in UCloud. This proxy server will use the _PROVIDER CREDENTIALS_ for all its requests. Which grants
// _VERY BROAD POWERS_. Whenever you want to add a call here, please make sure that the end-user making the IPC is
// actually allowed to do such an action. See below for some examples of how to do it before you try to add your own.
class IpcToUCloudProxyServer(private val client: AuthenticatedClient) {
    fun init(server: IpcServer, client: AuthenticatedClient) {
        server.addHandler(IpcHandler(IpcProxyRequestInterceptor.IPC_PROXY_METHOD) { user, req ->
            val proxyRequest = defaultMapper.decodeFromJsonElement(IpcProxyRequest.serializer(), req.params)
            val ucloudId = UserMapping.localIdToUCloudId(user.uid)
            var requireFurtherVerification = false

            val call: CallDescription<*, *, *> = when (proxyRequest.call) {
                // NOTE(DAN): PLEASE MAKE SURE YOU UNDERSTAND WHAT YOU ARE DOING BEFORE ADDING A NEW ENTRY
                JobsControl.update.fullName -> {
                    val request = defaultMapper.decodeFromJsonElement(
                        JobsControl.update.requestType,
                        proxyRequest.request
                    )

                    for (reqItem in request.items) {
                        verifyAccessToResource(
                            ucloudId,
                            jobCache.get(reqItem.id) ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                        )
                    }

                    JobsControl.update
                }

                FileCollectionsControl.register.fullName -> {
                    val request = defaultMapper.decodeFromJsonElement(
                        FileCollectionsControl.register.requestType,
                        proxyRequest.request
                    )

                    for (reqItem in request.items) {
                        if (reqItem.createdBy == null && reqItem.project == null) {
                            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                        }

                        verifyAccessToResource(
                            ucloudId,
                            reqItem.createdBy ?: "_ucloud",
                            reqItem.project,
                            emptyList()
                        )
                    }
                    FileCollectionsControl.register
                }

                FileCollectionsControl.retrieve.fullName -> {
                    requireFurtherVerification = true
                    FileCollectionsControl.retrieve
                }

                FileCollectionsControl.browse.fullName -> {
                    requireFurtherVerification = true
                    FileCollectionsControl.browse
                }

                ProductsV2.retrieve.fullName -> {
                    // NOTE(Dan): This is public
                    ProductsV2.retrieve
                }

                // NOTE(DAN): PLEASE MAKE SURE YOU UNDERSTAND WHAT YOU ARE DOING BEFORE ADDING A NEW ENTRY

                else -> null
            } ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            runBlocking {
                @Suppress("UNCHECKED_CAST")
                call as CallDescription<Any, Any, Any>

                val modifiedResponse: Any = run {
                    val response = client.client.call(
                        call,
                        defaultMapper.decodeFromJsonElement(call.requestType, proxyRequest.request),
                        client.backend,
                        client.authenticator,
                        afterHook = client.afterHook
                    ).orThrow()

                    if (requireFurtherVerification) {
                        when (call.fullName) {
                            FileCollectionsControl.retrieve.fullName -> {
                                val fc = response as FileCollection
                                verifyAccessToResource(ucloudId, fc)

                                fc
                            }

                            FileCollectionsControl.browse.fullName -> {
                                @Suppress("UNCHECKED_CAST")
                                val page = response as PageV2<FileCollection>

                                val newItems = page.items.filter {
                                    runCatching { verifyAccessToResource(ucloudId, it) }.isSuccess
                                }

                                page.copy(items = newItems)
                            }

                            else -> error("Unhandled verification case for ${call.fullName}")
                        }
                    } else {
                        response
                    }
                }

                defaultMapper.encodeToJsonElement(call.successType, modifiedResponse) as JsonObject
            }
        })
    }


    private val jobCache = SimpleCache<String, Job>(
        maxAge = 60_000 * 30,
        lookup = { jobId ->
            JobsControl.retrieve.call(
                ResourceRetrieveRequest(
                    JobIncludeFlags(
                        includeApplication = true,
                        includeOthers = true,
                        includeParameters = true,
                    ),
                    jobId
                ),
                client
            ).orNull()
        }
    )

    companion object : Loggable {
        override val log: Logger = logger()
    }
}

