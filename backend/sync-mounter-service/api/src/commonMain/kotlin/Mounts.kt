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

typealias ReadyRequest = Unit

@Serializable
data class ReadyResponse(
    val ready: Boolean
)

object Mounts: CallDescriptionContainer("sync.mounter") {
    private const val baseContext = "/api/sync/mount"

    val mount = call<MountRequest, MountResponse, CommonErrorMessage>("mount") {
        httpCreate(baseContext, roles = Roles.PUBLIC)
    }

    val unmount = call<UnmountRequest, UnmountResponse, CommonErrorMessage>("unmount") {
        httpDelete(baseContext, roles = Roles.PUBLIC)
    }

    val ready = call<ReadyRequest, ReadyResponse, CommonErrorMessage>("ready") {
        httpRetrieve(baseContext, roles = Roles.PUBLIC)
    }
}