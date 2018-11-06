package dk.sdu.cloud.file.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.MultipartRequest
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.StreamingFile
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

data class UploadRequest(
    val location: String,
    val sensitivity: SensitivityLevel?,
    val upload: StreamingFile?
)

data class UploadRequestAudit(val path: String, val sensitivityLevel: SensitivityLevel, val owner: String)

data class MultiPartUploadAudit(val request: UploadRequestAudit?)

data class BulkUploadRequest(
    val location: String,
    val policy: WriteConflictPolicy,
    val format: String,
    val upload: StreamingFile
)

data class BulkUploadErrorMessage(val message: String, val rejectedUploads: List<String>)
data class BulkUploadAudit(val path: String, val policy: WriteConflictPolicy, val owner: String)

object MultiPartUploadDescriptions : RESTDescriptions("files.upload") {
    const val baseContext = "/api/files/upload"

    val upload =
        callDescriptionWithAudit<MultipartRequest<UploadRequest>, Unit, CommonErrorMessage, MultiPartUploadAudit> {
            method = HttpMethod.Post
            name = "upload"

            auth {
                access = AccessRight.READ_WRITE
            }

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }

    val bulkUpload = callDescriptionWithAudit<
            MultipartRequest<BulkUploadRequest>,
            BulkUploadErrorMessage,
            CommonErrorMessage,
            BulkUploadAudit> {
        name = "bulkUpload"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"bulk"
        }
    }
}
