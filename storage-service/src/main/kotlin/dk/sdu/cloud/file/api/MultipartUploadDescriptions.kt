package dk.sdu.cloud.file.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.audit
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.types.StreamingFile
import dk.sdu.cloud.calls.types.StreamingRequest
import io.ktor.http.HttpMethod

data class UploadRequest(
    val location: String,
    val sensitivity: SensitivityLevel? = null,
    val policy: WriteConflictPolicy? = null,
    val upload: StreamingFile?
)

data class UploadRequestAudit(val path: String, val sensitivityLevel: SensitivityLevel?, val owner: String)

data class MultiPartUploadAudit(val request: UploadRequestAudit?)

data class BulkUploadRequest(
    val location: String,
    val policy: WriteConflictPolicy,
    val format: String,
    val upload: StreamingFile
)

data class BulkUploadErrorMessage(val message: String)
data class BulkUploadAudit(val path: String, val policy: WriteConflictPolicy, val owner: String)

object MultiPartUploadDescriptions : CallDescriptionContainer("files.upload") {
    const val baseContext = "/api/files/upload"

    val upload =
        call<StreamingRequest<UploadRequest>, Unit, CommonErrorMessage>("upload") {
            audit<MultiPartUploadAudit>()

            auth {
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post
                path {
                    using(baseContext)
                }

                body { bindEntireRequestFromBody() }
            }
        }

    val bulkUpload =
        call<StreamingRequest<BulkUploadRequest>, BulkUploadErrorMessage, CommonErrorMessage>("bulkUpload") {
            audit<BulkUploadAudit>()

            auth {
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"bulk"
                }

                body { bindEntireRequestFromBody() }
            }
        }
}
