package dk.sdu.cloud.upload.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.WriteConflictPolicy
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.defaultForFilePath
import okhttp3.*
import okio.BufferedSink
import org.slf4j.LoggerFactory
import java.util.*

data class BulkUploadErrorMessage(val message: String, val rejectedUploads: List<String>)

data class UploadRequestAudit(val path: String, val sensitivityLevel: SensitivityLevel, val owner: String)
data class MultiPartUploadAudit(val request: UploadRequestAudit?)
data class BulkUploadAudit(val path: String, val policy: WriteConflictPolicy, val owner: String)

object MultiPartUploadDescriptions : RESTDescriptions("upload") {
    const val baseContext = "/api/upload"
    private val client = OkHttpClient()

    // TODO FIXME Really need that multi-part support
    val upload = callDescriptionWithAudit<Unit, Unit, CommonErrorMessage, MultiPartUploadAudit> (
        body = {
            method = HttpMethod.Post
            name = "upload"

            auth {
                access = AccessRight.READ_WRITE
            }

            path {
                using(baseContext)
            }
        }
    )

    // TODO FIXME Really need that multi-part support
    val bulkUpload = callDescriptionWithAudit<Unit, BulkUploadErrorMessage, CommonErrorMessage, BulkUploadAudit> {
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

    fun callUpload(
        cloud: RefreshingJWTAuthenticatedCloud,
        location: String,

        causedBy: String? = null,

        owner: String? = null,
        sensitivityLevel: SensitivityLevel = SensitivityLevel.CONFIDENTIAL,
        mediaType: String = ContentType.defaultForFilePath(location).toString(),

        writer: (BufferedSink) -> Unit

    ) {
        callUpload(cloud.parent, cloud.tokenRefresher, location, causedBy, owner, sensitivityLevel, mediaType, writer)
    }

    fun callUpload(
        cloud: CloudContext,
        auth: RefreshingJWTAuthenticator,
        location: String,

        causedBy: String? = null,
        owner: String? = null,
        sensitivityLevel: SensitivityLevel = SensitivityLevel.CONFIDENTIAL,
        mediaType: String = ContentType.defaultForFilePath(location).toString(),

        writer: (BufferedSink) -> Unit
    ) {
        callUpload(
            cloud,
            auth.retrieveTokenRefreshIfNeeded(),
            location,
            causedBy,
            owner,
            sensitivityLevel,
            mediaType,
            writer
        )
    }

    /**
     * Uploads a file. Do not call [close] on the writer.
     */
    fun callUpload(
        cloud: CloudContext,
        token: String,
        location: String,

        causedBy: String? = null,
        owner: String? = null,
        sensitivityLevel: SensitivityLevel = SensitivityLevel.CONFIDENTIAL,
        mediaType: String = ContentType.defaultForFilePath(location).toString(),

        writer: (BufferedSink) -> Unit
    ) {
        val endpoint = cloud.resolveEndpoint(namespace).removeSuffix("/") + baseContext

        val streamingBody = object : RequestBody() {
            override fun contentType(): MediaType {
                return MediaType.parse(mediaType)!!
            }

            override fun writeTo(sink: BufferedSink) {
                writer(sink)
            }
        }

        val requestBody = MultipartBody.Builder().run {
            setType(MultipartBody.FORM)

            if (owner != null) addFormDataPart("owner", owner)
            addFormDataPart("location", location)
            addFormDataPart("sensitivity", sensitivityLevel.name)
            addFormDataPart("upload", location.substringAfterLast('/'), streamingBody)

            build()
        }


        val request = Request.Builder().run {
            header("Authorization", "Bearer $token")
            header("Job-Id", UUID.randomUUID().toString())
            if (causedBy != null) header("Caused-By", causedBy)

            url(endpoint)
            post(requestBody)
            build()
        }

        val response = client.newCall(request).execute()
        val code = response.code()
        when (code) {
            in 200..299 -> {
                // Everything is good
            }

            in 400..499 -> {
                throw IllegalArgumentException("Bad request created! ${response.body()?.string()}")
            }

            else -> {
                throw IllegalStateException(response.body()?.string())
            }
        }
    }

    private val log = LoggerFactory.getLogger(MultiPartUploadDescriptions::class.java)
}
