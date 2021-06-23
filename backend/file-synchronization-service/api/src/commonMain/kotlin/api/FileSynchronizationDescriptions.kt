package dk.sdu.cloud.file.synchronization.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import io.ktor.http.*
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
    val device_id: String
)

@Serializable
data class SynchronizationBrowseDevicesRequest(
    val provider: String
)
typealias SynchronizationBrowseDevicesResponse = PageV2<SynchronizationDevice>

@Serializable
data class SynchronizationAddFolderRequest(
    val path: String,
    val provider: String
)
typealias SynchronizationAddFolderResponse = Unit

@Serializable
data class SynchronizationRemoveFolderRequest(
    val id: String,
    val provider: String
)
typealias SynchronizationRemoveFolderResponse = Unit

@Serializable
data class SynchronizationAddDeviceRequest(
    val id: String,
    val provider: String
)
typealias SynchronizationAddDeviceResponse = Unit

@Serializable
data class SynchronizationRemoveDeviceRequest(
    val id: String,
    val provider: String
)
typealias SynchronizationRemoveDeviceResponse = Unit

@Serializable
data class SynchronizationRetrieveFolderRequest(
    val path: String,
    val provider: String
)
typealias SynchronizationRetrieveFolderResponse = SynchronizedFolder


object FileSynchronizationDescriptions: CallDescriptionContainer("files.synchronization") {
    const val baseContext = "/api/files/synchronization"

    val retrieveFolder = call<SynchronizationRetrieveFolderRequest, SynchronizationRetrieveFolderResponse,
        CommonErrorMessage>("retrieveFolder") {
        httpRetrieve(baseContext)
    }

    val addFolder = call<SynchronizationAddFolderRequest, SynchronizationAddFolderResponse,
        CommonErrorMessage>("addFolder") {
        auth {
            roles = Roles.END_USER
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"folder"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val removeFolder = call<SynchronizationRemoveFolderRequest, SynchronizationRemoveFolderResponse,
        CommonErrorMessage>("removeFolder") {
        auth {
            roles = Roles.END_USER
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
                +"folder"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val addDevice = call<SynchronizationAddDeviceRequest, SynchronizationAddDeviceResponse,
        CommonErrorMessage>("addDevice") {
        auth {
            roles = Roles.END_USER
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"device"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val removeDevice = call<SynchronizationRemoveDeviceRequest, SynchronizationRemoveDeviceResponse,
        CommonErrorMessage>("removeDevice") {
        auth {
            roles = Roles.END_USER
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
                +"device"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val browseDevices = call<SynchronizationBrowseDevicesRequest, SynchronizationBrowseDevicesResponse,
        CommonErrorMessage>("browseDevices") {
        httpBrowse(baseContext)
    }
}

