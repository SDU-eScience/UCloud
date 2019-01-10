package dk.sdu.cloud.avatar.http

import dk.sdu.cloud.avatar.api.Avatar
import dk.sdu.cloud.avatar.api.AvatarDescriptions
import dk.sdu.cloud.avatar.api.Clothes
import dk.sdu.cloud.avatar.api.ClothesGraphic
import dk.sdu.cloud.avatar.api.ColorFabric
import dk.sdu.cloud.avatar.api.CreateResponse
import dk.sdu.cloud.avatar.api.Eyebrows
import dk.sdu.cloud.avatar.api.Eyes
import dk.sdu.cloud.avatar.api.FacialHair
import dk.sdu.cloud.avatar.api.FacialHairColor
import dk.sdu.cloud.avatar.api.FindResponse
import dk.sdu.cloud.avatar.api.HairColor
import dk.sdu.cloud.avatar.api.MouthTypes
import dk.sdu.cloud.avatar.api.SkinColors
import dk.sdu.cloud.avatar.api.Top
import dk.sdu.cloud.avatar.api.TopAccessory
import dk.sdu.cloud.avatar.services.AvatarService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import java.lang.IllegalArgumentException

class AvatarController<DBSession> (
    private val avatarService: AvatarService<DBSession>
) : Controller {
    override val baseContext = AvatarDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(AvatarDescriptions.create) { req ->
            val avatar = approvedAvatar(
                req.user,
                req.top,
                req.topAccessory,
                req.hairColor,
                req.facialHair,
                req.facialHairColor,
                req.clothes,
                req.colorFabric,
                req.eyes,
                req.eyebrows,
                req.mouthTypes,
                req.skinColors,
                req.clothesGraphic
            )
            if (avatar != null) {
                ok(CreateResponse(avatarService.insert(req.user, avatar)))
            }
            else {
                error(HttpStatusCode.BadRequest)
            }
        }

        implement(AvatarDescriptions.update) { req ->
            val avatar = approvedAvatar(
                req.user,
                req.top,
                req.topAccessory,
                req.hairColor,
                req.facialHair,
                req.facialHairColor,
                req.clothes,
                req.colorFabric,
                req.eyes,
                req.eyebrows,
                req.mouthTypes,
                req.skinColors,
                req.clothesGraphic
            )
            if (avatar != null) {
                avatarService
                ok(Unit)
            }
            else {
                error(HttpStatusCode.BadRequest)
            }
        }

        implement(AvatarDescriptions.findAvatar) {

            ok(FindResponse("1","2","3","4","5","6","7","8","9","10","11","12"))
        }
    }

    private fun approvedAvatar (
        user: String,
        top: String,
        topAccessory: String,
        hairColor: String,
        facialHair: String,
        facialHairColor: String,
        clothes: String,
        colorFabric: String,
        eyes: String,
        eyebrows: String,
        mouthTypes: String,
        skinColors: String,
        clothesGraphic: String
    ) : Avatar? {
        return try {
            Avatar(
                null,
                user,
                Top.fromString(top),
                TopAccessory.fromString(topAccessory),
                HairColor.fromString(hairColor),
                FacialHair.fromString(facialHair),
                FacialHairColor.fromString(facialHairColor),
                Clothes.fromString(clothes),
                ColorFabric.fromString(colorFabric),
                Eyes.fromString(eyes),
                Eyebrows.fromString(eyebrows),
                MouthTypes.fromString(mouthTypes),
                SkinColors.fromString(skinColors),
                ClothesGraphic.fromString(clothesGraphic)
            )
        } catch (e: IllegalArgumentException) {
            return null
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
