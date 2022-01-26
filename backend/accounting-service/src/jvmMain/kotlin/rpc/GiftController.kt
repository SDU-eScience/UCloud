package dk.sdu.cloud.grant.rpc

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.accounting.services.grants.GiftService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.grant.api.Gifts
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class GiftController(
    private val gifts: GiftService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Gifts.availableGifts) {
            ok(gifts.findAvailableGifts(actorAndProject))
        }

        implement(Gifts.claimGift) {
            gifts.claimGift(actorAndProject, request.giftId)
            ok(Unit)
        }

        implement(Gifts.createGift) {
            ok(FindByLongId(gifts.createGift(actorAndProject, request)))
        }

        implement(Gifts.deleteGift) {
            gifts.deleteGift(actorAndProject, request.giftId)
            ok(Unit)
        }
    }
}
