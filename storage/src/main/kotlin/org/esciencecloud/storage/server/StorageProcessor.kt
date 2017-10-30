package org.esciencecloud.storage.server

import org.esciencecloud.storage.UserType

// Shared interface stuff. Should be published as a separate artifact
// These artifacts should be shared with others, such that they may be used for types

// TODO This should also include certain types of the storage interfaces, but no longer the storage interface themselves
// this will have to be changed later.

interface StorageRequest {
    val header: RequestHeader
}

data class RequestHeader(
        val uuid: String,
        val performedFor: ProxyClient
)

// This will chnage over time. Should use a token instead of a straight password. We won't need the username at that
// point, since we could retrieve this from the auth service instead.
data class ProxyClient(val username: String, val password: String)

data class CreateUserRequest(
        override val header: RequestHeader,

        val username: String,
        val password: String?,
        val userType: UserType // <-- Shared type that lives inside storage interface
) : StorageRequest

data class ModifyUserRequest(
        override val header: RequestHeader,

        val currentUsername: String,
        val newPassword: String?,
        val newUserType: UserType?
) : StorageRequest

class StorageResponse<out InputType : Any>(
        val successful: Boolean,
        val errorMessage: String?,
        val input: InputType
)

object StorageProcessor {
    val PREFIX = "storage"
}

object UserGroupsProcessor {
    val PREFIX = "${StorageProcessor.PREFIX}.ugs"

    val CreateUser = RequestResponseStream.create<CreateUserRequest>("$PREFIX.create_user")
    val ModifyUser = RequestResponseStream.create<ModifyUserRequest>("$PREFIX.modify_user")
    val Bomb = RequestResponseStream.create<Unit>("$PREFIX.BOMB9392") // TODO FIXME REMOVE THIS LATER
}
