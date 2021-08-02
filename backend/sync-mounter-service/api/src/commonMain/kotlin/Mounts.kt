package dk.sdu.cloud.sync.mounter.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class MountFolder(
    val path: String
)

typealias MountRequest = BulkRequest<MountFolder>
typealias MountResponse = Unit

typealias UnmountRequest = BulkRequest<MountFolder>
typealias UnmountResponse = Unit

typealias StateRequest = MountFolder
typealias StateResponse = Unit

object Mounts: CallDescriptionContainer("sync.mounter") {
    val baseContext = "/api/sync/mount"

    val mount = call<MountRequest, MountResponse, CommonErrorMessage>("mount") {
        httpCreate(baseContext, roles = Roles.SERVICE)
    }

    val unmount = call<UnmountRequest, UnmountResponse, CommonErrorMessage>("unmount") {
        httpDelete(baseContext, roles = Roles.SERVICE)
    }

    val state = call<StateRequest, StateResponse, CommonErrorMessage>("state") {
        httpRetrieve(baseContext, roles = Roles.SERVICE)
    }
}