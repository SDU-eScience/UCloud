package dk.sdu.cloud.file.ucloud.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsProvider
import dk.sdu.cloud.file.orchestrator.api.FilesProvider
import dk.sdu.cloud.file.orchestrator.api.SharesProvider
import io.ktor.http.*
import kotlinx.serialization.Serializable

@TSNamespace("file.ucloud.files")
object UCloudFiles : FilesProvider(UCLOUD_PROVIDER)
@TSNamespace("file.ucloud.filecollections")
object UCloudFileCollections : FileCollectionsProvider(UCLOUD_PROVIDER)
object UCloudShares : SharesProvider(UCLOUD_PROVIDER)

@Serializable
data class UCloudFilesDownloadRequest(val token: String)

object UCloudFileDownload : CallDescriptionContainer("file.ucloud.download") {
    val download = call<UCloudFilesDownloadRequest, Unit, CommonErrorMessage>("download") {
        audit<Unit>()
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get
            path { using("/ucloud/ucloud/download") }
            params { +boundTo(UCloudFilesDownloadRequest::token) }
        }
    }
}
