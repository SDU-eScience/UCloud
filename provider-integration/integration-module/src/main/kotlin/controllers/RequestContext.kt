package dk.sdu.cloud.controllers

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.cli.CommandLineInterface
import dk.sdu.cloud.config.VerifiedConfig
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.ipc.IpcClient
import dk.sdu.cloud.ipc.RealIpcClient
import dk.sdu.cloud.ipc.IpcServer
import dk.sdu.cloud.plugins.PluginContext

interface RequestContext : PluginContext {
    val ucloudUsername: String?
}

class SimpleRequestContext(
    private val delegate: PluginContext,
    override val ucloudUsername: String?,
) : RequestContext {
    override val rpcClientOptional: AuthenticatedClient?
        get() = delegate.rpcClientOptional
    override val config: VerifiedConfig
        get() = delegate.config
    override val ipcClientOptional: IpcClient?
        get() = delegate.ipcClientOptional
    override val ipcServerOptional: IpcServer?
        get() = delegate.ipcServerOptional
    override val commandLineInterface: CommandLineInterface?
        get() = delegate.commandLineInterface
    override val debugSystem: DebugSystem?
        get() = delegate.debugSystem
}

fun CallHandler<*, *, *>.requestContext(controllerContext: ControllerContext): RequestContext {
    return SimpleRequestContext(controllerContext.pluginContext, ucloudUsername)
}
