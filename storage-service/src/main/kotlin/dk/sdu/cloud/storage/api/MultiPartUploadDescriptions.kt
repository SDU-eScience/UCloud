package dk.sdu.cloud.storage.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.RESTDescriptions
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.defaultForFilePath
import okhttp3.*
import okio.BufferedSink
import org.slf4j.LoggerFactory
import java.util.*

data class BulkUploadErrorMessage(val message: String, val rejectedUploads: List<String>)

object MultiPartUploadDescriptions : RESTDescriptions(StorageServiceDescription) {
    private const val baseContext = "/api/upload"
    private val client = OkHttpClient()

    // TODO FIXME Really need that multi-part support
    val upload = callDescription<Unit, Unit, CommonErrorMessage>(
        body = {
            method = HttpMethod.Post
            prettyName = "upload"

            path {
                using(baseContext)
            }
        }
    )

    // TODO FIXME Really need that multi-part support
    val bulkUpload = callDescription<Unit, BulkUploadErrorMessage, CommonErrorMessage> {
        prettyName = "bulkUpload"
        method = HttpMethod.Post

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
        val call = upload.prepare(Unit)
        val endpoint = cloud.resolveEndpoint(call).removeSuffix("/") + baseContext

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
