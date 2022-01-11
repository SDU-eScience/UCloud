package dk.sdu.cloud.controllers

import dk.sdu.cloud.IMConfiguration
import dk.sdu.cloud.althttp.RpcServer
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.ipc.IpcServer
import dk.sdu.cloud.plugins.LoadedPlugins
import dk.sdu.cloud.plugins.PluginContext

interface Controller {
    fun RpcServer.configure()
    fun configureIpc(server: IpcServer) {}
}

class ControllerContext(
    val ownExecutable: String,
    val configuration: IMConfiguration,
    val pluginContext: PluginContext,
    val plugins: LoadedPlugins,
)
