package dk.sdu.cloud.app.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.MultipartRequest
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.StreamingFile
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

data class SubmitComputationResult(
    val jobId: String,
    val filePath: String,
    val fileData: StreamingFile
)

object ComputationCallbackDescriptions : RESTDescriptions("app.compute") {
    val baseContext = "/api/app/compute"

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
     */
    val submitFile = callDescription<MultipartRequest<SubmitComputationResult>, Unit, CommonErrorMessage> {
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

        body { bindEntireRequestFromBody() }
    }

    val lookup = callDescription<FindByStringId, VerifiedJob, CommonErrorMessage> {
        name = "lookup"
        method = HttpMethod.Get

        path {
            using(baseContext)
            +"lookup"
            +boundTo(FindByStringId::id)
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
}
