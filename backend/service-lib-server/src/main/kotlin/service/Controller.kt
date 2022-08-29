package dk.sdu.cloud.service

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server

var microWhichIsConfiguringCalls: Micro? = null

interface Controller {
    fun configure(rpcServer: RpcServer)
}

fun CommonServer.configureControllers(vararg controllers: Controller) {
    microWhichIsConfiguringCalls = micro
    controllers.forEach {
        it.configure(micro.server)
    }
    microWhichIsConfiguringCalls = null
}


fun RpcServer.configureControllers(micro: Micro, vararg controllers: Controller) {
    microWhichIsConfiguringCalls = micro
    controllers.forEach {
        it.configure(this)
    }
    microWhichIsConfiguringCalls = null
}