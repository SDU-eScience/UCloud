package dk.sdu.cloud.app.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.types.StreamingFile
import dk.sdu.cloud.calls.types.StreamingRequest
import io.ktor.http.HttpMethod

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

object ComputationCallbackDescriptions : CallDescriptionContainer("app.compute") {
    val baseContext = "/api/app/compute"

    /**
     * Posts a new status for a live job.
     */
    val addStatus = call<AddStatusJob, Unit, CommonErrorMessage>("addStatus") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"status"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Requests a state change for a job.
     */
    val requestStateChange = call<StateChangeRequest, Unit, CommonErrorMessage>("requestStateChange") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"state-change"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Submits a file for a job.
     *
     * This can only happen while the job is in state [JobState.RUNNING]
     */
    val submitFile = call<StreamingRequest<SubmitComputationResult>, Unit, CommonErrorMessage>("submitFile") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"submit"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val lookup = call<FindByStringId, VerifiedJob, CommonErrorMessage>("lookup") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"lookup"
                +boundTo(FindByStringId::id)
            }
        }
    }

    val completed = call<JobCompletedRequest, Unit, CommonErrorMessage>("completed") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"completed"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
