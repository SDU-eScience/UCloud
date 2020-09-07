package dk.sdu.cloud.accounting.services

import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.toActor

class ProductController(
    private val db: AsyncDBSessionFactory,
    private val products: ProductService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Products.createProduct) {
            products.create(db, ctx.securityPrincipal.toActor(), request)
            ok(Unit)
        }

        implement(Products.updateProduct) {
            products.update(db, ctx.securityPrincipal.toActor(), request)
            ok(Unit)
        }

        implement(Products.findProduct) {
            ok(products.find(db, ctx.securityPrincipal.toActor(), request))
        }

        implement(Products.listProducts) {
            ok(
                products.list(
                    db,
                    ctx.securityPrincipal.toActor(),
                    request.provider,
                    request.normalize()
                )
            )
        }

        implement(Products.listProductsByType) {
            ok(
                products.listByArea(
                    db,
                    ctx.securityPrincipal.toActor(),
                    request.area,
                    request.provider,
                    request.normalize()
                )
            )
        }

        implement(Products.retrieveAllFromProvider) {
            ok(
                products.listAllByProvider(db, ctx.securityPrincipal.toActor(), request.provider)
            )
        }

        return@with
    }
}
