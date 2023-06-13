package dk.sdu.cloud.avatar.http

import dk.sdu.cloud.avatars.api.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.responseAllocator
import dk.sdu.cloud.messages.BinaryAllocator
import dk.sdu.cloud.service.Controller

class BinaryController(private val serviceClient: AuthenticatedClient) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(BinaryTest.noRequest) {
            useAllocator {
                ok(
                    Avatar(
                        Top.EYEPATCH,
                        TopAccessory.BLANK,
                        HairColor.AUBURN,
                        FacialHair.BEARD_LIGHT,
                        FacialHairColor.AUBURN,
                        Clothes.BLAZER_SHIRT,
                        ColorFabric.BLACK,
                        Eyes.CLOSE,
                        Eyebrows.ANGRY,
                        MouthTypes.CONCERNED,
                        SkinColors.BLACK,
                        ClothesGraphic.BAT,
                        HatColor.BLACK,
                    )
                )
            }
        }

        implement(BinaryTest.echo) {
            ok(request)
        }

        implement(BinaryTest.callThroughEcho) {
            ok(BinaryTest.echo.call(request, serviceClient).orThrow())
        }
    }
}

inline fun <R> CallHandler<*, *, *>.useAllocator(block: BinaryAllocator.() -> R): R {
    return ctx.responseAllocator.run(block)
}
