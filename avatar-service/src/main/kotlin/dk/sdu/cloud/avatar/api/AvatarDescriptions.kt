package dk.sdu.cloud.avatar.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

data class CreateRequest(
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

data class CreateResponse(
    val id: Long
)

data class UpdateRequest(
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

typealias UpdateResponse = Unit

typealias FindRequest = Unit

data class FindResponse(
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

object AvatarDescriptions : RESTDescriptions("avatar") {
    val baseContext = "/api/avatar"

    val create = callDescription<CreateRequest, CreateResponse, CommonErrorMessage> {
        name = "create"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"create"
        }

        body { bindEntireRequestFromBody() }
    }

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
