package dk.sdu.cloud.service

import dk.sdu.cloud.calls.server.RpcServer

interface Controller {
    fun configure(rpcServer: RpcServer)
    fun onServerReady(rpcServer: RpcServer) {}
}
