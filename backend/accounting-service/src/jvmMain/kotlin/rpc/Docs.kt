@file:Suppress("DEPRECATION")

package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.provider.api.ResourcesDoc
import dk.sdu.cloud.service.Controller
import io.ktor.http.*

class Docs : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ResourcesDoc.create) {
            throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
        implement(ResourcesDoc.browse) {
            throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
        return@with
    }
}
