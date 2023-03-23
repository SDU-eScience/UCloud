package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.services.products.ProductService
import dk.sdu.cloud.accounting.services.wallets.AccountingService
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class ProductController(
    private val products: ProductService,
    private val accounting: AccountingService,
    private val client: AuthenticatedClient
) : Controller {
    private fun <R : Any, S : Any, E : Any> RpcServer.implementOrDispatch(
        call: CallDescription<R, S, E>,
        handler: suspend CallHandler<R, S, E>.() -> Unit,
    ) {
        implement(call) {
            val activeProcessor = accounting.retrieveActiveProcessorAddress()
            if (activeProcessor == null) {
                handler()
            } else {
                ok(
                    call.call(
                        request,
                        client.withFixedHost(HostInfo(activeProcessor, "http", 8080))
                    ).orThrow()
                )
            }
        }
    }

    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Products.create) {
            products.create(actorAndProject, request)
            ok(Unit)
        }

        //Note(Henrik): Both browse and retrieve can include balance, so it requires to be handled on the
        //correct instance since it has need of the Accounting Processor.
        implementOrDispatch(Products.browse) {
            ok(products.browse(actorAndProject, request))
        }

        implementOrDispatch(Products.retrieve) {
            ok(products.retrieve(actorAndProject, request))
        }
    }
}
