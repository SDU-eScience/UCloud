package dk.sdu.cloud.avatar.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

/**
 * A serialized avatar. Should be used whenever going over the wire.
 */
data class SerializedAvatar(
    val top: String,
    val topAccessory: String,
    val hairColor: String,
    val facialHair: String,
    val facialHairColor: String,
    val clothes: String,
    val colorFabric: String,
    val eyes: String,
    val eyebrows: String,
    val mouthTypes: String,
    val skinColors: String,
    val clothesGraphic: String
)

typealias UpdateRequest = SerializedAvatar

typealias UpdateResponse = Unit

typealias FindRequest = Unit

typealias FindResponse = SerializedAvatar

object AvatarDescriptions : RESTDescriptions("avatar") {
    val baseContext = "/api/avatar"

    val update = callDescription<UpdateRequest, UpdateResponse, CommonErrorMessage> {
        name = "update"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"update"
        }

        body { bindEntireRequestFromBody() }
    }

    val findAvatar = callDescription<FindRequest, FindResponse, CommonErrorMessage> {
        name = "findAvatar"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"find"
        }
    }
}
