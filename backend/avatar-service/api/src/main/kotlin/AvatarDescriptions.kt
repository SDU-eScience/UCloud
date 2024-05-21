package dk.sdu.cloud.avatar.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer


typealias UpdateRequest = Avatar
typealias UpdateResponse = Unit
typealias FindRequest = Unit
typealias FindResponse = Avatar

@Serializable
@UCloudApiInternal(InternalLevel.STABLE)
data class FindBulkRequest(
    val usernames: List<String>
)

@Serializable
@UCloudApiInternal(InternalLevel.STABLE)
data class FindBulkResponse(
    val avatars: Map<String, Avatar>
)

typealias Avatars = AvatarDescriptions

@TSTopLevel
@UCloudApiInternal(InternalLevel.STABLE)
object AvatarDescriptions : CallDescriptionContainer("avatar") {
    val baseContext = "/api/avatar"

    init {
        title = "Avatars"
        description = """
Provides user avatars. User avatars are provided by the https://avataaars.com/ library.

All users have an avatar associated with them. A default avatar will be
returned if one is not found in the database. As a result, this service does
not need to listen for user created events.

The avatar are mainly used as a way for users to easier distinguish between different users when sharing or
working in projects.

 ${ApiConventions.nonConformingApiWarning}
        """.trimIndent()
    }

    val update = call("update", UpdateRequest.serializer(), UpdateResponse.serializer(), CommonErrorMessage.serializer()) {
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

        documentation {
            summary = "Update the avatar of the current user."
        }
    }

    val findAvatar = call("findAvatar", FindRequest.serializer(), FindResponse.serializer(), CommonErrorMessage.serializer()) {
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

        documentation {
            summary = "Request the avatar of the current user."
        }
    }

    val findBulk = call("findBulk", FindBulkRequest.serializer(), FindBulkResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"bulk"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Request the avatars of one or more users by username."
        }
    }
}
