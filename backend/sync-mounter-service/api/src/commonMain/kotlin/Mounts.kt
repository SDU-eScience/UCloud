package dk.sdu.cloud.sync.mounter.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class MountFolder(
    val id: String,
    val path: String
)

@Serializable
data class MountFolderId(
    val id: String
)

typealias MountRequest = BulkRequest<MountFolder>
typealias MountResponse = Unit

typealias UnmountRequest = BulkRequest<MountFolderId>
typealias UnmountResponse = Unit

typealias StateRequest = MountFolderId
typealias StateResponse = Unit

object Mounts: CallDescriptionContainer("sync.mounter") {
    private const val baseContext = "/api/sync/mount"

    val mount = call<MountRequest, MountResponse, CommonErrorMessage>("mount") {
        httpCreate(baseContext, roles = Roles.PRIVILEGED)
    }

    val unmount = call<UnmountRequest, UnmountResponse, CommonErrorMessage>("unmount") {
        httpDelete(baseContext, roles = Roles.PRIVILEGED)
    }

    val state = call<StateRequest, StateResponse, CommonErrorMessage>("state") {
        httpRetrieve(baseContext, roles = Roles.PRIVILEGED)
    }
}