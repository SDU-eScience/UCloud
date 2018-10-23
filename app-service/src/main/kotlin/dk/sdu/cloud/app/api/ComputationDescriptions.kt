package dk.sdu.cloud.app.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.client.defaultMapper
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

data class ComputationErrorMessage(
    val internalReason: String,
    val statusMessage: String
)

/**
 * Abstract [RESTDescriptions] for computation backends.
 *
 * @param namespace The sub-namespace for this computation backend. Examples: "abacus", "aws", "digitalocean".
 */
abstract class ComputationDescriptions(namespace: String) : RESTDescriptions("app.compute.$namespace") {
    val baseContext = "/api/app/compute/$namespace"

    private val client = OkHttpClient()

    /**
     * Submits a file for a job.
     *
     * This can only happen while the job is in state [JobState.TRANSFER_SUCCESS]
     *
     * It is submitted as a multi-part form (not currently supported in service-common).
     *
     * @see SUBMIT_FILE_FIELD_JOB
     * @see SUBMIT_FILE_FIELD_PARAM_NAME
     * @see SUBMIT_FILE_FIELD_FILE_DATA
     */
    val submitFile = callDescription<Unit, Unit, ComputationErrorMessage> {
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

    /**
     * Notifies this computation backend that a job has been verified.
     *
     * The computation backend is allowed to return an error.
     */
    val jobVerified = callDescription<VerifiedJob, Unit, ComputationErrorMessage> {
        name = "jobVerified"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"job-verified"
        }

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        body { bindEntireRequestFromBody() }
    }

    /**
     * Notifies this computation backend that a job has been prepared. The computation backend should then begin
     * to schedule the job, followed by a notification when job has completed.
     */
    val jobPrepared = callDescription<VerifiedJob, Unit, ComputationErrorMessage> {
        name = "jobPrepared"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"job-prepared"
        }

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        body { bindEntireRequestFromBody() }
    }

    /**
     * Notifies the backend that cleanup is required for a job.
     */
    val cleanup = callDescription<VerifiedJob, Unit, ComputationErrorMessage> {
        name = "cleanup"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"cleanup"
        }

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        body { bindEntireRequestFromBody() }
    }


    val follow = callDescription<FollowStdStreamsRequest, FollowStdStreamsResponse, CommonErrorMessage> {
        name = "follow"
        method = HttpMethod.Get

        path {
            using(baseContext)
            +"follow"
            +boundTo(FollowStdStreamsRequest::jobId)
        }

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        params {
            +boundTo(FollowStdStreamsRequest::stderrLineStart)
            +boundTo(FollowStdStreamsRequest::stderrMaxLines)
            +boundTo(FollowStdStreamsRequest::stdoutLineStart)
            +boundTo(FollowStdStreamsRequest::stdoutMaxLines)
        }
    }

    fun submitFile(
        cloud: RefreshingJWTAuthenticatedCloud,

        job: VerifiedJob,
        parameterName: String,

        causedBy: String? = null,

        dataWriter: (BufferedSink) -> Unit
    ): Pair<HttpStatusCode, String> {
        val token = cloud.tokenRefresher.retrieveTokenRefreshIfNeeded()
        return submitFile(cloud.parent, token, job, parameterName, causedBy, dataWriter)
    }

    fun submitFile(
        cloud: CloudContext,
        token: String,

        job: VerifiedJob,
        parameterName: String,

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
            addFormDataPart(SUBMIT_FILE_FIELD_JOB, defaultMapper.writeValueAsString(job))
            addFormDataPart(SUBMIT_FILE_FIELD_PARAM_NAME, parameterName)
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

    companion object {
        const val SUBMIT_FILE_FIELD_JOB = "job"
        const val SUBMIT_FILE_FIELD_PARAM_NAME = "parameterName"
        const val SUBMIT_FILE_FIELD_FILE_DATA = "fileData"
    }
}
