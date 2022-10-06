package dk.sdu.cloud.cli

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProviderRegisteredResource
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobUpdate
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollectionIncludeFlags
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsControl
import dk.sdu.cloud.ipc.IpcProxyCall
import dk.sdu.cloud.ipc.IpcProxyRequestInterceptor
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.utils.sendTerminalMessage
import kotlin.system.exitProcess

fun EvilCli(controllerContext: ControllerContext) {
    val pluginContext = controllerContext.pluginContext
    val config = pluginContext.config

    pluginContext.commandLineInterface?.addHandler(CliHandler("evil") { args ->
        val rpcClient = run {
            val client = RpcClient()
            client.attachRequestInterceptor(IpcProxyRequestInterceptor(pluginContext.ipcClient))
            AuthenticatedClient(client, IpcProxyCall, afterHook = null, authenticator = {})
        }

        fun sendHelp(): Nothing {
            sendTerminalMessage { red { line("Something went wrong. Please see the code: $args") } }
            exitProcess(0)
        }

        when (args.getOrNull(0)) {
            "job-update" -> {
                val jobId = args.getOrNull(1) ?: sendHelp()
                val newState = args.getOrNull(2)?.let { runCatching { JobState.valueOf(it) }.getOrNull() } ?: sendHelp()

                JobsControl.update.call(
                    bulkRequestOf(
                        ResourceUpdateAndId(
                            jobId,
                            JobUpdate(newState)
                        )
                    ),
                    rpcClient
                ).also { sendTerminalMessage { line(it.statusCode.toString()) } }
            }

            "register-drive" -> {
                val isUser = (args.getOrNull(1) ?: sendHelp()) == "user"
                val entityId = args.getOrNull(2) ?: sendHelp()

                FileCollectionsControl.register.call(
                    bulkRequestOf(
                        ProviderRegisteredResource(
                            FileCollection.Spec(
                                "Evil",
                                config.products.storage!!.values.first().first().let { p ->
                                    ProductReference(p.name, p.category.name, p.category.provider)
                                }
                            ),
                            createdBy = if (isUser) entityId else null,
                            project = if (isUser) null else entityId
                        )
                    ),
                    rpcClient
                ).also { sendTerminalMessage { line(it.statusCode.toString()) } }
            }

            "retrieve-drive" -> {
                val id = args.getOrNull(1) ?: sendHelp()
                FileCollectionsControl.retrieve.call(
                    ResourceRetrieveRequest(
                        FileCollectionIncludeFlags(),
                        id
                    ),
                    rpcClient
                ).also { sendTerminalMessage { line(it.statusCode.toString()) } }
            }

            "browse-drives" -> {
                val providerIds = args.getOrNull(1) ?: sendHelp()

                FileCollectionsControl.browse.call(
                    ResourceBrowseRequest(
                        FileCollectionIncludeFlags(
                            filterProviderIds = providerIds
                        ),
                    ),
                    rpcClient
                ).also {
                    sendTerminalMessage {
                        line(it.statusCode.toString())
                        line(it.orNull().toString())
                    }
                }
            }

            else -> sendHelp()
        }
    })
}
