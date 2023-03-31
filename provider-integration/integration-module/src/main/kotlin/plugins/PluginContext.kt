package dk.sdu.cloud.plugins

import dk.sdu.cloud.config.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.cli.CommandLineInterface
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.ipc.IpcClient
import dk.sdu.cloud.ipc.RealIpcClient
import dk.sdu.cloud.ipc.IpcServer

interface PluginContext {
    val rpcClientOptional: AuthenticatedClient?
    val config: VerifiedConfig
    val ipcClientOptional: IpcClient?
    val ipcServerOptional: IpcServer?
    val commandLineInterface: CommandLineInterface?
    val debugSystem: DebugSystem
}

val PluginContext.rpcClient: AuthenticatedClient
    get() = rpcClientOptional ?: error("This plugin does not have access to an RPC client!")

val PluginContext.ipcClient: IpcClient
    get() = ipcClientOptional ?: error("This plugin does not have access to an IPC client!")

val PluginContext.ipcServer: IpcServer
    get() = ipcServerOptional ?: error("This plugin does not have access to an IPC server!")

class SimplePluginContext(
    override val rpcClientOptional: AuthenticatedClient?,
    override val config: VerifiedConfig,
    override val ipcClientOptional: IpcClient?,
    override val ipcServerOptional: IpcServer?,
    override val commandLineInterface: CommandLineInterface?,
    override val debugSystem: DebugSystem,
) : PluginContext
