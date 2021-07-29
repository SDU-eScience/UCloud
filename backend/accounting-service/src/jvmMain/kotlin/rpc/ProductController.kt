package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.services.products.ProductService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class ProductController(
    private val products: ProductService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Products.create) {
            products.create(actorAndProject, request)
            ok(Unit)
        }

        implement(Products.browse) {
            ok(products.browse(actorAndProject, request))
        }

        implement(Products.retrieve) {
            ok(products.retrieve(actorAndProject, request))
        }
    }
}
