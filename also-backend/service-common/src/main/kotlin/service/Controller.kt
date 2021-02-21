package dk.sdu.cloud.service

import dk.sdu.cloud.calls.server.RpcServer

interface Controller {
    fun configure(rpcServer: RpcServer)
}

fun RpcServer.configureControllers(vararg controllers: Controller) {
    controllers.forEach {
        it.configure(this)
    }
}
