package dk.sdu.cloud.cli

import dk.sdu.cloud.app.orchestrator.api.SSHKey
import dk.sdu.cloud.app.orchestrator.api.SSHKeysControl
import dk.sdu.cloud.app.orchestrator.api.SSHKeysControlBrowseRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.ipcServer
import dk.sdu.cloud.plugins.rpcClient
import kotlinx.serialization.Serializable
import java.io.File

fun SshKeyCli(controllerContext: ControllerContext) {
    val pluginContext = controllerContext.pluginContext
    val config = pluginContext.config
    pluginContext.commandLineInterface?.addHandler(CliHandler("ssh") { args ->
        fun sendHelp(): Nothing = sendCommandLineUsage("ssh", "View information about relevant SSH keys of users") {
            subcommand("retrieve", "Retrieves the SSH keys of one or more users") {
                arg("users") {
                    description = "One or more users separated by a comma (,)"
                }

                arg("outputDirectory=directory") {
                    description = "Specifies the output directory to use"
                }
            }
        }

        val ipcClient = pluginContext.ipcClient
        genericCommandLineHandler {
            when (args.getOrNull(0)) {
                "retrieve" -> {
                    val users = args.getOrNull(1)?.split(",")?.map { it.trim() }?.takeIf { it.isNotEmpty() }
                        ?: sendHelp()

                    val outputDir = args.getOrNull(2)?.let { File(it) }?.also { it.mkdirs() }
                        ?.takeIf { it.isDirectory } ?: sendHelp()

                    val result = ipcClient.sendRequest(
                        SshKeyIpc.retrieve,
                        SshKeyIpcRetrieveRequest(users)
                    )

                    result.responses
                        .groupBy { it.owner }
                        .forEach { (user, keys) ->
                            File(outputDir, user).writeText(keys.map { it.specification.key }.joinToString("\n"))
                        }
                }
            }
        }
    })

    if (config.shouldRunServerCode()) {
        val rpcClient = pluginContext.rpcClient
        val ipcServer = pluginContext.ipcServer

        ipcServer.addHandler(SshKeyIpc.retrieve.handler { user, request ->
            if (user.uid != 0) throw RPCException("Root is required for this script", HttpStatusCode.Forbidden)

            val allItems = ArrayList<SSHKey>()
            var next: String? = null
            while (true) {
                val page = SSHKeysControl.browse.call(
                    SSHKeysControlBrowseRequest(
                        request.usernames,
                        itemsPerPage = 250,
                        next = next
                    ),
                    rpcClient
                ).orThrow()

                allItems.addAll(page.items)
                next = page.next ?: break
            }

            BulkResponse(allItems)
        })
    }
}

@Serializable
private data class SshKeyIpcRetrieveRequest(val usernames: List<String>)

private object SshKeyIpc : IpcContainer("ssh_keys") {
    val retrieve = retrieveHandler(SshKeyIpcRetrieveRequest.serializer(), BulkResponse.serializer(SSHKey.serializer()))
}
