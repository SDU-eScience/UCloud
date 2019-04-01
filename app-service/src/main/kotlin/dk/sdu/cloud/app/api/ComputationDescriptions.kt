package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.bindToSubProperty
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.calls.types.StreamingFile
import dk.sdu.cloud.calls.types.StreamingRequest
import io.ktor.http.HttpMethod

data class ComputationErrorMessage(
    val internalReason: String,
    val statusMessage: String
)

data class SubmitFileToComputation(
    val jobId: String,
    val parameterName: String,
    @JsonIgnore val fileData: BinaryStream
)

/**
 * Abstract [RESTDescriptions] for computation backends.
 *
 * @param namespace The sub-namespace for this computation backend. Examples: "abacus", "aws", "digitalocean".
 */
abstract class ComputationDescriptions(namespace: String) : CallDescriptionContainer("app.compute.$namespace") {
    val baseContext = "/api/app/compute/$namespace"

    /**
     * Submits a file for a job.
     *
     * This can only happen while the job is in state [JobState.TRANSFER_SUCCESS]
     */
    val submitFile = call<SubmitFileToComputation, Unit, ComputationErrorMessage>("submitFileV2") {
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
                +boundTo("JobSubmit-Id", SubmitFileToComputation::jobId)
                +boundTo("JobSubmit-Parameter", SubmitFileToComputation::parameterName)
            }

            body { bindToSubProperty(SubmitFileToComputation::fileData) }
        }
    }

    /**
     * Notifies this computation backend that a job has been verified.
     *
     * The computation backend is allowed to return an error.
     */
    val jobVerified = call<VerifiedJob, Unit, ComputationErrorMessage>("jobVerified") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"job-verified"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Notifies this computation backend that a job has been prepared. The computation backend should then begin
     * to schedule the job, followed by a notification when job has completed.
     */
    val jobPrepared = call<VerifiedJob, Unit, ComputationErrorMessage>("jobPrepared") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"job-prepared"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Notifies the backend that cleanup is required for a job.
     */
    val cleanup = call<VerifiedJob, Unit, ComputationErrorMessage>("cleanup") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"cleanup"
            }

            body { bindEntireRequestFromBody() }
        }
    }


    val follow = call<InternalFollowStdStreamsRequest, InternalStdStreamsResponse, CommonErrorMessage>("follow") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"follow"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
