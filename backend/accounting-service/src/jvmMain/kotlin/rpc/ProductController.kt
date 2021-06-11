package dk.sdu.cloud.accounting.services

import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.securityPrincipalOrNull
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.toActor
import dk.sdu.cloud.toActorOrGuest

class ProductController(
    private val db: AsyncDBSessionFactory,
    private val products: ProductService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Products.createProduct) {
            db.withSession { session ->
                request.items.forEach { req ->
                    products.create(session, ctx.securityPrincipal.toActor(), req)
                }
            }
            ok(Unit)
        }

        implement(Products.updateProduct) {
            db.withSession { session ->
                request.items.forEach { req ->
                    products.update(session, ctx.securityPrincipal.toActor(), req)
                }
            }
            ok(Unit)
        }

        implement(Products.findProduct) {
            ok(products.find(db, ctx.securityPrincipal.toActor(), request))
        }

        implement(Products.browse) {
            ok(products.browse(db, ctx.securityPrincipal.toActor(), ctx.project, request))
        }

        return@with
    }
}
