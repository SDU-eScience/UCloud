package dk.sdu.cloud.app.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

data class AddStatusJob(val id: String, val status: String)
data class StateChangeRequest(val id: String, val newState: JobState, val newStatus: String? = null)
data class JobCompletedRequest(
    val id: String,
    val wallDuration: SimpleDuration,
    val success: Boolean
)

object ComputationCallbackDescriptions : RESTDescriptions("app.compute") {
    val baseContext = "/api/app/compute"

    const val SUBMIT_FILE_FIELD_FILE_NAME = "fileName"
    const val SUBMIT_FILE_FIELD_FILE_DATA = "fileData"
    const val SUBMIT_FILE_FIELD_LENGTH = "fileLength"

    private val client = OkHttpClient()

    /**
     * Posts a new status for a live job.
     */
    val addStatus = callDescription<AddStatusJob, Unit, CommonErrorMessage> {
        name = "addStatus"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"status"
        }

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        body { bindEntireRequestFromBody() }
    }

    /**
     * Requests a state change for a job.
     */
    val requestStateChange = callDescription<StateChangeRequest, Unit, CommonErrorMessage> {
        name = "requestStateChange"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"state-change"
        }

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        body { bindEntireRequestFromBody() }
    }

    /**
     * Submits a file for a job.
     *
     * This can only happen while the job is in state [JobState.RUNNING]
     *
     * It is submitted as a multi-part form (not currently supported in service-common).
     *
     * @see SUBMIT_FILE_FIELD_FILE_NAME
     * @see SUBMIT_FILE_FIELD_LENGTH
     * @see SUBMIT_FILE_FIELD_FILE_DATA
     */
    val submitFile = callDescription<Unit, Unit, CommonErrorMessage> {
        name = "submitFile"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"submit"
        }

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }
    }

    val lookup = callDescription<FindByStringId, VerifiedJob, CommonErrorMessage> {
        name = "lookup"
        method = HttpMethod.Get

        path {
            using(baseContext)
            +"lookup"
        }

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }
    }

    val completed = callDescription<JobCompletedRequest, Unit, CommonErrorMessage> {
        name = "completed"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"completed"
        }

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        body { bindEntireRequestFromBody() }
    }

    fun submitFile(
        cloud: RefreshingJWTAuthenticatedCloud,

        fileName: String,
        fileLength: Long,

        causedBy: String? = null,

        dataWriter: (BufferedSink) -> Unit
    ): Pair<HttpStatusCode, String> {
        val token = cloud.tokenRefresher.retrieveTokenRefreshIfNeeded()
        return submitFile(cloud.parent, token, fileName, fileLength, causedBy, dataWriter)
    }

    fun submitFile(
        cloud: CloudContext,
        token: String,

        fileName: String,
        fileLength: Long,

        causedBy: String? = null,

        dataWriter: (BufferedSink) -> Unit
    ): Pair<HttpStatusCode, String> {
        val endpoint = cloud.resolveEndpoint(namespace).removeSuffix("/") + baseContext + "/submit"
        val streamingBody = object : RequestBody() {
            override fun contentType(): MediaType {
                return MediaType.parse(ContentType.Application.OctetStream.toString())!!
            }

            override fun writeTo(sink: BufferedSink) {
                dataWriter(sink)
            }
        }

        val requestBody = MultipartBody.Builder().run {
            setType(MultipartBody.FORM)
            addFormDataPart(SUBMIT_FILE_FIELD_FILE_NAME, fileName)
            addFormDataPart(SUBMIT_FILE_FIELD_LENGTH, fileLength.toString())
            addFormDataPart(SUBMIT_FILE_FIELD_FILE_DATA, "file", streamingBody)
        }.build()

        val request = Request.Builder().run {
            header("Authorization", "Bearer $token")
            if (causedBy != null) header("Caused-By", causedBy)

            url(endpoint)
            post(requestBody)
        }.build()

        val response = client.newCall(request).execute()
        return HttpStatusCode.fromValue(response.code()) to (response.body()?.string() ?: "")
    }
}
