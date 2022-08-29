package dk.sdu.cloud.file.ucloud.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.orchestrator.api.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class UCloudFilesDownloadRequest(val token: String)

class UCloudFileDownload(providerId: String) : CallDescriptionContainer("file.ucloud.download") {
    val download = call("download", UCloudFilesDownloadRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        audit(Unit.serializer())
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

