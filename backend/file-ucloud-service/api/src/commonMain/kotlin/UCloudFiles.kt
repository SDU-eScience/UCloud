package dk.sdu.cloud.file.ucloud.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.orchestrator.api.*
import kotlinx.serialization.Serializable

@Serializable
data class UCloudSyncFoldersBrowseRequest(
    val device: String
)

@Serializable
data class SyncFolderBrowseItem(
    val id: Long,
    val path: String,
    val synchronizationType: SynchronizationType
)

typealias UCloudSyncFoldersBrowseResponse = List<SyncFolderBrowseItem>


@Serializable
data class UCloudFilesDownloadRequest(val token: String)

class UCloudFileDownload(providerId: String) : CallDescriptionContainer("file.ucloud.download") {
    val download = call<UCloudFilesDownloadRequest, Unit, CommonErrorMessage>("download") {
        audit<Unit>()
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get
            path { using("/ucloud/$providerId/download") }
            params { +boundTo(UCloudFilesDownloadRequest::token) }
        }
    }
}

class UCloudSyncFoldersBrowse(providerId: String) : CallDescriptionContainer("file.ucloud.sync.folders") {
    val browse = call<UCloudSyncFoldersBrowseRequest, UCloudSyncFoldersBrowseResponse,
        CommonErrorMessage>("browse") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get
            path { using("/ucloud/$providerId/sync/folders") }
            params { +boundTo(UCloudSyncFoldersBrowseRequest::device) }
        }
    }
}
