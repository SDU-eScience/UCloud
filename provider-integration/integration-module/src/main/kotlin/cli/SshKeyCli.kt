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
import dk.sdu.cloud.utils.sendTerminalFrame
import dk.sdu.cloud.utils.sendTerminalMessage
import kotlinx.serialization.Serializable
import java.io.File

fun SshKeyCli(controllerContext: ControllerContext) {
    val pluginContext = controllerContext.pluginContext
    val config = pluginContext.config
    pluginContext.commandLineInterface?.addHandler(CliHandler("ssh") { args ->
        fun sendHelp(): Nothing = sendCommandLineUsage("ssh", "View information about relevant SSH keys of users") {
            subcommand("retrieve", "Retrieves the SSH keys of one or more users") {
                arg("outputDirectory") {
                    description = "Specifies the output directory to use"
                }

                arg("users") {
                    description = "One or more users separated by a comma (,)"
                }
            }
        }

        val ipcClient = pluginContext.ipcClient
        genericCommandLineHandler {
            when (args.getOrNull(0)) {
                "retrieve" -> {
                    val users = args.getOrNull(2)?.split(",")?.map { it.trim() }?.takeIf { it.isNotEmpty() }
                        ?: sendHelp()

                    val outputDir = args.getOrNull(1)?.let { File(it) }?.also { it.mkdirs() }
                        ?.takeIf { it.isDirectory } ?: sendHelp()

                    val result = ipcClient.sendRequest(
                        SshKeyIpc.retrieve,
                        SshKeyIpcRetrieveRequest(users)
                    )

                    var userCount = 0
                    var keyCount = 0
                    result.responses
                        .groupBy { it.owner }
                        .forEach { (user, keys) ->
                            userCount += 1
                            keyCount += keys.size
                            File(outputDir, user).writeText(keys.map { it.specification.key }.joinToString("\n"))
                        }

                    sendTerminalMessage {
                        green {
                            inline("Successfully wrote ")
                            bold { inline(keyCount.toString()) }
                            inline(" key(s) for ")
                            bold { inline(userCount.toString()) }
                            inline(" user(s) at ")
                            bold { inline(outputDir.absolutePath) }
                            line()
                        }
                    }
                }

                else -> sendHelp()
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
