package dk.sdu.cloud.file.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.audit
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.bindToSubProperty
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.calls.types.StreamingFile
import dk.sdu.cloud.calls.types.StreamingRequest
import io.ktor.http.HttpMethod

data class UploadRequestAudit(val path: String, val sensitivityLevel: SensitivityLevel?, val owner: String)

data class MultiPartUploadAudit(val request: UploadRequestAudit?)

data class BulkUploadErrorMessage(val message: String)
data class BulkUploadAudit(val path: String, val policy: WriteConflictPolicy, val owner: String)

data class SimpleUploadRequest(
    val location: String,
    val file: BinaryStream,
    val policy: WriteConflictPolicy? = null,
    val sensitivity: SensitivityLevel? = null
)

data class SimpleBulkUpload(
    val location: String,
    val format: String,
    val file: BinaryStream,
    val name: String? = null,
    val policy: WriteConflictPolicy? = null,
    val sensitivity: SensitivityLevel? = null
)

object MultiPartUploadDescriptions : CallDescriptionContainer("files.upload") {
    const val baseContext = "/api/files/upload"

    val simpleUpload = call<SimpleUploadRequest, Unit, CommonErrorMessage>("simpleUpload") {
        audit<MultiPartUploadAudit>()

        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"file"
            }

            headers {
                +boundTo("Upload-Sensitivity", SimpleUploadRequest::sensitivity)
                +boundTo("Upload-Policy", SimpleUploadRequest::policy)
                +boundTo("Upload-Location", SimpleUploadRequest::location)
            }

            body {
                bindToSubProperty(SimpleUploadRequest::file)
            }
        }
    }

    val simpleBulkUpload =
        call<SimpleBulkUpload, BulkUploadErrorMessage, CommonErrorMessage>("simpleBulkUpload") {
            audit<BulkUploadAudit>()

            auth {
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"archive"
                }

                headers {
                    +boundTo("Upload-Sensitivity", SimpleBulkUpload::sensitivity)
                    +boundTo("Upload-Policy", SimpleBulkUpload::policy)
                    +boundTo("Upload-Location", SimpleBulkUpload::location)
                    +boundTo("Upload-Format", SimpleBulkUpload::format)
                    +boundTo("Upload-Name", SimpleBulkUpload::name)
                }

                body {
                    bindToSubProperty(SimpleBulkUpload::file)
                }
            }
        }
}
