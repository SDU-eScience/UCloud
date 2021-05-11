package dk.sdu.cloud.plugins

import dk.sdu.cloud.IMConfiguration
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.cli.CommandLineInterface
import dk.sdu.cloud.ipc.IpcClient
import dk.sdu.cloud.ipc.IpcServer

interface PluginContext {
    val rpcClient: AuthenticatedClient?
    val config: IMConfiguration
    val ipcClient: IpcClient?
    val ipcServer: IpcServer?
    val commandLineInterface: CommandLineInterface?
}

class SimplePluginContext(
    override val rpcClient: AuthenticatedClient?,
    override val config: IMConfiguration,
    override val ipcClient: IpcClient?,
    override val ipcServer: IpcServer?,
    override val commandLineInterface: CommandLineInterface?
) : PluginContext