package dk.sdu.cloud.file.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.audit
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.HttpServerConverter
import dk.sdu.cloud.calls.types.StreamingFile
import dk.sdu.cloud.calls.types.StreamingRequest
import dk.sdu.cloud.service.Loggable
import io.ktor.application.call
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import kotlinx.coroutines.io.ByteReadChannel

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
    val sensitivity: SensitivityLevel? = null,
    val upload: StreamingFile
)

data class BulkUploadErrorMessage(val message: String)
data class BulkUploadAudit(val path: String, val policy: WriteConflictPolicy, val owner: String)

sealed class SimpleUploadRequest {
    class Ingoing(private val ctx: HttpCall) : SimpleUploadRequest() {
        val location: String = ctx.call.request.header("Upload-Location")
            ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        val policy = ctx.call.request.header("Upload-Policy")?.let { WriteConflictPolicy.valueOf(it) }
        val sensitivity = ctx.call.request.header("Upload-Sensitivity")?.let { SensitivityLevel.valueOf(it) }

        val file get() = ctx.call.request.receiveChannel()
    }

    class Outgoing(
        val location: String,
        val sensitivity: SensitivityLevel? = null,
        val policy: WriteConflictPolicy? = null,
        val file: ByteReadChannel
    ) : SimpleUploadRequest()
    // TODO Add these guys to header.

    fun asIngoing(): SimpleUploadRequest.Ingoing = this as Ingoing

    companion object : HttpServerConverter.IngoingBody<SimpleUploadRequest>, Loggable {
        override val log = logger()

        override suspend fun serverIngoingBody(
            description: CallDescription<*, *, *>,
            call: HttpCall
        ): SimpleUploadRequest {
            return SimpleUploadRequest.Ingoing(call)
        }
    }
}

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
