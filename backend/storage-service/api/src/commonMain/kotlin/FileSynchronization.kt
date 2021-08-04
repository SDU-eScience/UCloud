package dk.sdu.cloud.file.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

enum class SynchronizationType(val syncthingValue: String) {
    SEND_RECEIVE("sendreceive"),
    SEND_ONLY("sendonly")
}

@Serializable
data class SynchronizationDevice(val id: String)
@Serializable
data class SynchronizedFolder(
    val id: String,
    val path: String,
    val device: String
)

@Serializable
data class SynchronizedFolderBrowseItem(
    val id: String,
    val path: String
)

@Serializable
data class SynchronizationBrowseDevicesRequest(
    val provider: String,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2
typealias SynchronizationBrowseDevicesResponse = PageV2<SynchronizationDevice>

@Serializable
data class SynchronizationBrowseFoldersRequest(
    val device: String
)
typealias SynchronizationBrowseFoldersResponse = List<SynchronizedFolderBrowseItem>

@Serializable
data class SynchronizationAddFolderItem(
    val path: String,
    val provider: String
)
typealias SynchronizationAddFolderRequest = BulkRequest<SynchronizationAddFolderItem>

typealias SynchronizationAddFolderResponse = Unit

@Serializable
data class SynchronizationDeleteFolderItem(
    val id: String,
    val provider: String
)
typealias SynchronizationRemoveFolderRequest = BulkRequest<SynchronizationDeleteFolderItem>
typealias SynchronizationRemoveFolderResponse = Unit

@Serializable
data class SynchronizationDeviceItem(
    val id: String,
    val provider: String
)
typealias SynchronizationAddDeviceRequest = BulkRequest<SynchronizationDeviceItem>

typealias SynchronizationAddDeviceResponse = Unit

typealias SynchronizationRemoveDeviceRequest = BulkRequest<SynchronizationDeviceItem>
typealias SynchronizationRemoveDeviceResponse = Unit

@Serializable
data class SynchronizationRetrieveFolderRequest(
    val path: String,
    val provider: String
)
typealias SynchronizationRetrieveFolderResponse = SynchronizedFolder


object FileSynchronization: CallDescriptionContainer("files.synchronization") {
    const val baseContext = "/api/files/synchronization"

    val retrieveFolder = call<SynchronizationRetrieveFolderRequest, SynchronizationRetrieveFolderResponse,
        CommonErrorMessage>("retrieveFolder") {
        httpRetrieve(baseContext, roles = Roles.PRIVILEGED)
    }

    val addFolder = call<SynchronizationAddFolderRequest, SynchronizationAddFolderResponse,
        CommonErrorMessage>("addFolder") {
        httpCreate(joinPath(baseContext, "folder"), roles = Roles.PRIVILEGED)
    }

    val browseFolders = call<SynchronizationBrowseFoldersRequest, SynchronizationBrowseFoldersResponse,
        CommonErrorMessage>("browseFolders") {
        httpBrowse(joinPath(baseContext, "folder"), roles = Roles.SERVICE)
    }

    val removeFolder = call<SynchronizationRemoveFolderRequest, SynchronizationRemoveFolderResponse,
        CommonErrorMessage>("removeFolder") {
        httpDelete(joinPath(baseContext, "folder"), roles = Roles.PRIVILEGED)
    }

    val addDevice = call<SynchronizationAddDeviceRequest, SynchronizationAddDeviceResponse,
        CommonErrorMessage>("addDevice") {
        httpCreate(joinPath(baseContext, "device"), roles = Roles.PRIVILEGED)
    }

    val removeDevice = call<SynchronizationRemoveDeviceRequest, SynchronizationRemoveDeviceResponse,
        CommonErrorMessage>("removeDevice") {
        httpDelete(joinPath(baseContext, "device"), roles = Roles.PRIVILEGED)
    }

    val browseDevices = call<SynchronizationBrowseDevicesRequest, SynchronizationBrowseDevicesResponse,
        CommonErrorMessage>("browseDevices") {
        httpBrowse(baseContext, roles = Roles.PRIVILEGED)
    }
}

