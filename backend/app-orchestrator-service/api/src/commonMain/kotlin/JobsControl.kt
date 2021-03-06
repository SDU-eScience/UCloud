package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

typealias JobsControlUpdateRequest = BulkRequest<JobsControlUpdateRequestItem>
typealias JobsControlUpdateResponse = Unit
@Serializable
data class JobsControlUpdateRequestItem(
    val jobId: String,
    val state: JobState? = null,
    val status: String? = null,
    @UCloudApiDoc(
        "Indicates that this request should be ignored if the current state does not match the expected state"
    )
    val expectedState: JobState? = null,

    @UCloudApiDoc("Indicates that this request should be ignored if the current state equals `state`")
    val expectedDifferentState: Boolean? = null
)

typealias JobsControlChargeCreditsRequest = BulkRequest<JobsControlChargeCreditsRequestItem>
@Serializable
data class JobsControlChargeCreditsResponse(
    @UCloudApiDoc("A list of jobs which could not be charged due to lack of funds. " +
        "If all jobs were charged successfully then this will empty.")
    val insufficientFunds: List<FindByStringId>,

    @UCloudApiDoc("A list of jobs which could not be charged due to it being a duplicate charge. " +
        "If all jobs were charged successfully this will be empty.")
    val duplicateCharges: List<FindByStringId>,
)

@Serializable
data class JobsControlRetrieveRequest(
    val id: String,
    override val includeParameters: Boolean? = null,
    override val includeUpdates: Boolean? = null,
    override val includeApplication: Boolean? = null,
    override val includeProduct: Boolean? = null,
    override val includeSupport: Boolean? = null,
) : JobDataIncludeFlags
typealias JobsControlRetrieveResponse = Job

@Serializable
data class JobsControlChargeCreditsRequestItem(
    @UCloudApiDoc("The ID of the job")
    val id: String,

    @UCloudApiDoc(
        "The ID of the charge\n\n" +
            "This charge ID must be unique for the job, UCloud will reject charges which are not unique."
    )
    val chargeId: String,

    @UCloudApiDoc(
        "Amount of compute time to charge the user\n\n" +
            "The wall duration should be for a single job replica and should only be for the time used since the last" +
            "update. UCloud will automatically multiply the amount with the number of job replicas."
    )
    val wallDuration: SimpleDuration,
)

@Serializable
data class JobsControlSubmitFileRequest(
    val jobId: String,
    val filePath: String,
)
typealias JobsControlSubmitFileResponse = Unit

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object JobsControl : CallDescriptionContainer("jobs.control") {
    const val baseContext = "/api/jobs/control"

    init {
        title = "Job control"
        description = """
            Internal API between UCloud and compute providers. This API allows compute providers to push state changes
            to UCloud.
        """.trimIndent()
    }

    val update = call<JobsControlUpdateRequest, JobsControlUpdateResponse, CommonErrorMessage>("update") {
        httpUpdate(baseContext, "update", roles = Roles.PROVIDER)

        documentation {
            summary = "Push state changes to UCloud"
            description = """
                Pushes one or more state changes to UCloud. UCloud will always treat all updates as a single
                transaction. UCloud may reject the status updates if it deems them to be invalid. For example, an
                update may be rejected if it performs an invalid state transition, such as from a terminal state to
                a running state.
            """.trimIndent()

            example("State transition to running") {
                request = bulkRequestOf(
                    JobsControlUpdateRequestItem(
                        "jobId",
                        JobState.RUNNING,
                        "The job is now running"
                    )
                )
            }

            example("Invalid state transition") {
                statusCode = HttpStatusCode.BadRequest
                error = CommonErrorMessage(
                    "Invalid state transition",
                    "INVALID_TRANSITION"
                )
            }
        }
    }

    val chargeCredits = call<JobsControlChargeCreditsRequest, JobsControlChargeCreditsResponse, CommonErrorMessage>(
        "chargeCredits"
    ) {
        httpUpdate(baseContext, "chargeCredits", roles = Roles.PROVIDER)

        documentation {
            summary = "Charge the user for the job"
        }
    }

    val retrieve = call<JobsControlRetrieveRequest, JobsControlRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext, roles = Roles.PROVIDER)

        documentation {
            summary = "Retrieve job information"
            description = """
                Allows the compute backend to query the UCloud database for a job owned by the compute provider.
            """.trimIndent()
        }
    }

    val submitFile = call<JobsControlSubmitFileRequest, JobsControlSubmitFileResponse, CommonErrorMessage>(
        "submitFile"
    ) {
        audit<JobsControlSubmitFileRequest> {
            longRunningResponseTime = true
        }

        auth {
            roles = Roles.PROVIDER
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"submitFile"
            }

            headers {
                +boundTo("ComputeJobId", JobsControlSubmitFileRequest::jobId)
                +boundTo("ComputeJobPath", JobsControlSubmitFileRequest::filePath)
            }

            // body { bindToSubProperty(JobsControlSubmitFileRequest::fileData) }
        }

        documentation {
            summary = "Submit output file to UCloud"
            description = """
                Submits an output file to UCloud which is not available to be put directly into the storage resources
                mounted by the compute provider.
                
                Note: We do not recommend using this endpoint for transferring large quantities of data/files.
            """.trimIndent()
        }
    }
}
