package dk.sdu.cloud.app.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.MultipartRequest
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.StreamingFile
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

data class ComputationErrorMessage(
    val internalReason: String,
    val statusMessage: String
)

data class SubmitFileToComputation(
    val job: VerifiedJob,
    val parameterName: String,
    val fileData: StreamingFile
)

/**
 * Abstract [RESTDescriptions] for computation backends.
 *
 * @param namespace The sub-namespace for this computation backend. Examples: "abacus", "aws", "digitalocean".
 */
abstract class ComputationDescriptions(namespace: String) : RESTDescriptions("app.compute.$namespace") {
    val baseContext = "/api/app/compute/$namespace"

    /**
     * Submits a file for a job.
     *
     * This can only happen while the job is in state [JobState.TRANSFER_SUCCESS]
     */
    val submitFile = callDescription<MultipartRequest<SubmitFileToComputation>, Unit, ComputationErrorMessage> {
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


    val follow = callDescription<InternalFollowStdStreamsRequest, InternalStdStreamsResponse, CommonErrorMessage> {
        name = "follow"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"follow"
        }

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        body { bindEntireRequestFromBody() }
    }
}
