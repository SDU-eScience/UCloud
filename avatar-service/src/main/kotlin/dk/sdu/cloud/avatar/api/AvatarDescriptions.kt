package dk.sdu.cloud.avatar.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
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

data class FindBulkRequest(
    val usernames: List<String>
)

data class FindBulkResponse(
    val avatars: Map<String, SerializedAvatar>
)

object AvatarDescriptions : CallDescriptionContainer("avatar") {
    val baseContext = "/api/avatar"

    val update = call<UpdateRequest, UpdateResponse, CommonErrorMessage>("update") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"update"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val findAvatar = call<FindRequest, FindResponse, CommonErrorMessage>("findAvatar") {
        auth {
            access = AccessRight.READ
        }
        http {
            method = HttpMethod.Get


            path {
                using(baseContext)
                +"find"
            }
        }
    }

    val findBulk = call<FindBulkRequest, FindBulkResponse, CommonErrorMessage>("findBulk") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"bulk"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
