package dk.sdu.cloud.plugins

import dk.sdu.cloud.IMConfiguration
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.cli.CommandLineInterface
import dk.sdu.cloud.ipc.IpcClient
import dk.sdu.cloud.ipc.IpcServer

interface PluginContext {
    val rpcClientOptional: AuthenticatedClient?
    val config: IMConfiguration
    val ipcClientOptional: IpcClient?
    val ipcServerOptional: IpcServer?
    val commandLineInterface: CommandLineInterface?
}

val PluginContext.rpcClient: AuthenticatedClient
    get() = rpcClientOptional ?: error("This plugin does not have access to an RPC client!")

val PluginContext.ipcClient: IpcClient
    get() = ipcClientOptional ?: error("This plugin does not have access to an IPC client!")

val PluginContext.ipcServer: IpcServer
    get() = ipcServerOptional ?: error("This plugin does not have access to an IPC server!")

class SimplePluginContext(
    override val rpcClientOptional: AuthenticatedClient?,
    override val config: IMConfiguration,
    override val ipcClientOptional: IpcClient?,
    override val ipcServerOptional: IpcServer?,
    override val commandLineInterface: CommandLineInterface?
) : PluginContext