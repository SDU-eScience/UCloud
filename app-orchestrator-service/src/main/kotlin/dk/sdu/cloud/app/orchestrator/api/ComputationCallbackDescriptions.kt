package dk.sdu.cloud.app.orchestrator.api

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.audit
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.bindToSubProperty
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.types.BinaryStream
import io.ktor.http.HttpMethod

data class AddStatusJob(val id: String, val status: String)
data class StateChangeRequest(val id: String, val newState: JobState, val newStatus: String? = null)
data class JobCompletedRequest(
    val id: String,
    val wallDuration: SimpleDuration?,
    val success: Boolean
)

data class SubmitComputationResult(
    val jobId: String,
    val filePath: String,
    val needsExtraction: Boolean?,
    @JsonIgnore val fileData: BinaryStream
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
    val submitFile = call<SubmitComputationResult, Unit, CommonErrorMessage>("submitFileV2") {
        audit<SubmitComputationResult> {
            longRunningResponseTime = true
        }

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

            headers {
                +boundTo("JobSubmit-Id", SubmitComputationResult::jobId)
                +boundTo("JobSubmit-Path", SubmitComputationResult::filePath)
                +boundTo("JobSubmit-Extraction", SubmitComputationResult::needsExtraction)
            }

            body { bindToSubProperty(SubmitComputationResult::fileData) }
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
