package dk.sdu.cloud.sync.mounter.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class MountFolder(
    val id: Long,
    val path: String
)

@Serializable
data class MountFolderId(
    val id: Long
)

typealias MountRequest = BulkRequest<MountFolder>
typealias MountResponse = Unit

typealias UnmountRequest = BulkRequest<MountFolderId>
typealias UnmountResponse = Unit

@Serializable
data class ReadyRequest(
    val configurationId: String? = null,
)

@Serializable
data class ReadyResponse(
    val ready: Boolean,
    val requireConfigurationId: String,
)

object Mounts: CallDescriptionContainer("sync.mounter") {
    private const val baseContext = "/ucloud/ucloud/sync/mount"

    val mount = call<MountRequest, MountResponse, CommonErrorMessage>("mount") {
        httpCreate(baseContext, roles = Roles.PROVIDER)
    }

    val unmount = call<UnmountRequest, UnmountResponse, CommonErrorMessage>("unmount") {
        httpDelete(baseContext, roles = Roles.PROVIDER)
    }

    val ready = call<ReadyRequest, ReadyResponse, CommonErrorMessage>("ready") {
        httpRetrieve(baseContext, roles = Roles.PROVIDER)
    }
}