package dk.sdu.cloud.grant.rpc

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.grant.api.AvailableGiftsResponse
import dk.sdu.cloud.grant.api.Gifts
import dk.sdu.cloud.grant.api.ListGiftsResponse
import dk.sdu.cloud.grant.services.GiftService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.toActor
import io.ktor.http.HttpStatusCode

class GiftController(
    private val gifts: GiftService,
    private val db: DBContext
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Gifts.availableGifts) {
            ok(AvailableGiftsResponse(gifts.findAvailableGifts(db, ctx.securityPrincipal.toActor())))
        }

        implement(Gifts.claimGift) {
            gifts.claimGift(db, ctx.securityPrincipal.toActor(), request.giftId)
            ok(Unit)
        }

        implement(Gifts.createGift) {
            ok(FindByLongId(gifts.createGift(db, ctx.securityPrincipal.toActor(), request)))
        }

        implement(Gifts.deleteGift) {
            gifts.deleteGift(db, ctx.securityPrincipal.toActor(), request.giftId)
            ok(Unit)
        }

        implement(Gifts.listGifts) {
            ok(
                ListGiftsResponse(
                    gifts.listGifts(
                        db,
                        ctx.securityPrincipal.toActor(),
                        ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
                    )
                )
            )
        }
    }
}
