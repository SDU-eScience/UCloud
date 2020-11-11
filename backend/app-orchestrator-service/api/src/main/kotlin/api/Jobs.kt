package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.UCloudApiExperimental
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest

@UCloudApiExperimental(UCloudApiExperimental.Level.ALPHA)
data class Job(
    @UCloudApiDoc(
        "Unique identifier for this job.\n\n" +
            "UCloud guarantees that no other job, regardless of compute provider, has the same unique identifier."
    )
    val id: String,

    @UCloudApiDoc(
        "A name for this job assigned by the user.\n\n" +
            "The name can help a user identify why and with which parameters a job was started. " +
            "This value is suitable for display in user interfaces."
    )
    val name: String?,

    @UCloudApiDoc("A reference to the owner of this job")
    val owner: JobOwner,

    @UCloudApiDoc(
        "A list of status updates from the compute backend.\n\n" +
            "The status updates tell a story of what happened with the job. " +
            "This list is ordered by the timestamp in ascending order. " +
            "The current state of the job will always be the last element. " +
            "`updates` is guaranteed to always contain at least one element."
    )
    val updates: List<JobUpdate>,

    @UCloudApiDoc("The amount of credits charged to the `owner` of this job.")
    val creditsCharged: Long,

    @UCloudApiDoc("The parameters used to launch this job.\n\n" +
        "This property is always available but must be explicitly requested.")
    val parameters: JobParameters?,

    @UCloudApiDoc("Information regarding the output of this job.")
    val output: JobOutput?,

    val vnc: JobVncLink?,

    val web: JobWebLink?,

    val shell: JobShellLink?
)

@UCloudApiExperimental(UCloudApiExperimental.Level.ALPHA)
data class JobOwner(
    @UCloudApiDoc("The username of the user which started the job")
    val launchedBy: String,

    @UCloudApiDoc(
        "The project ID of the project which owns this job\n\n" +
            "This value can be null and this signifies that the job belongs to the personal workspace of the user."
    )
    val project: String? = null,
)

data class JobUpdate(
    val timestamp: Long,
    val state: JobState? = null,
    val status: String? = null,
)

data class JobParameters(
    val application: NameAndVersion,
    val product: ComputeProductReference,
    val name: String? = null,
    val replicas: Int = 1,
    val allowDuplicateJob: Boolean = false,
    val parameters: Map<String, Any> = emptyMap(),
    val mounts: List<Any> = emptyList(),
    val peers: List<ApplicationPeer> = emptyList(),
    val publicUrl: String? = null,
)

data class ComputeProductReference(
    val id: String,
    val category: String,
    val provider: String,
)

data class JobOutput(
    val outputFolder: String,
)

data class JobWebLink(val link: String)
data class JobVncLink(val link: String, val password: String? = null)
data class JobShellLink(val link: String)

interface JobDataIncludeFlags {
    val includeParameters: Boolean?
    val includeOutput: Boolean?
    val includeWeb: Boolean?
    val includeVnc: Boolean?
    val includeShell: Boolean?
}

typealias JobsCreateRequest = BulkRequest<JobParameters>

data class JobsCreateResponse(val ids: List<String>)

data class JobsRetrieveRequest(
    val id: String,
    override val includeParameters: Boolean?,
    override val includeOutput: Boolean?,
    override val includeWeb: Boolean?,
    override val includeVnc: Boolean?,
    override val includeShell: Boolean?,
) : JobDataIncludeFlags
typealias JobsRetrieveResponse = Job

data class JobsBrowseRequest(
    override val itemsPerPage: Int?,
    override val page: Int?,
    override val includeParameters: Boolean?,
    override val includeOutput: Boolean?,
    override val includeWeb: Boolean?,
    override val includeVnc: Boolean?,
    override val includeShell: Boolean?,
) : WithPaginationRequest, JobDataIncludeFlags
typealias JobsBrowseResponse = Page<Job>

typealias JobsDeleteRequest = BulkRequest<FindByStringId>
typealias JobsDeleteResponse = Unit

typealias JobsFollowRequest = FindByStringId
data class JobsFollowResponse(
    val updates: List<JobUpdate>,
    val log: List<JobsLog>
)
data class JobsLog(val rank: Int, val stdout: String?, val stderr: String?)

typealias JobsExtendRequest = BulkRequest<JobsExtensionRequest>
typealias JobsExtendResponse = Unit
data class JobsExtensionRequest(
    val jobId: String,
    val requestedTime: SimpleDuration
)

@UCloudApiExperimental(UCloudApiExperimental.Level.ALPHA)
object Jobs : CallDescriptionContainer("jobs") {
    const val baseContext = "/api/jobs"

    init {
        title = "Jobs"
        description = """
            This is a test description
        """.trimIndent()
    }

    val create = call<JobsCreateRequest, JobsCreateResponse, CommonErrorMessage>("create") {
        httpCreate(baseContext)

        documentation {
            summary = "Start a compute job"
        }
    }

    val delete = call<JobsDeleteRequest, JobsDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext)

        documentation {
            summary = "Request job cancellation and destruction"
            description = """
                This call will request the cancellation of the associated jobs. This will make sure that the jobs
                are eventually stopped and resources are released. If the job is running a virtual machine, then the
                virtual machine will be stopped and destroyed. Persistent storage attached to the job will not be
                deleted only temporary data from the job will be deleted.
                
                This call is asynchronous and the cancellation may not be immediately visible in the job. Progress can
                be followed using the ${docRef(::retrieve)}, ${docRef(::browse)}, ${docRef(::follow)} calls.
            """.trimIndent()
        }
    }

    val retrieve = call<JobsRetrieveRequest, JobsRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext)

        documentation {
            summary = "Retrieve a single Job"
        }
    }

    val browse = call<JobsBrowseRequest, JobsBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)

        documentation {
            summary = "Browse the jobs available to this user"
        }
    }

    val follow = call<JobsFollowRequest, JobsFollowResponse, CommonErrorMessage>("follow") {
        auth { access = AccessRight.READ }
        websocket(baseContext)

        documentation {
            summary = "Follow the progress of a job"
        }
    }

    val extend = call<JobsExtendRequest, JobsExtendResponse, CommonErrorMessage>("extend") {
        httpUpdate(baseContext, "extend")

        documentation {
            summary = "Extend the duration of a job"
        }
    }
}
