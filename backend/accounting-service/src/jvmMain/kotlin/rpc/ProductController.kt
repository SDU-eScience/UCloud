package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.services.products.ProductService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.securityPrincipalOrNull
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.toActor
import dk.sdu.cloud.toActorOrGuest

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

        @Suppress("DEPRECATION")
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

        @Suppress("DEPRECATION")
        implement(Products.listProductsByType) {
            ok(
                products.listByArea(
                    db,
                    ctx.securityPrincipalOrNull.toActorOrGuest(),
                    request.area,
                    request.provider,
                    request.normalize(),
                    request.showHidden
                )
            )
        }

        @Suppress("DEPRECATION")
        implement(Products.retrieveAllFromProvider) {
            ok(
                products.listAllByProvider(db, ctx.securityPrincipal.toActor(), request.provider, request.showHidden)
            )
        }

        implement(Products.browse) {
            ok(products.browse(db, ctx.securityPrincipal.toActor(), ctx.project, request))
        }

        return@with
    }
}
