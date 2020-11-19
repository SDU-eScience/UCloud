package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.UCloudApiExperimental
import dk.sdu.cloud.service.*
import io.ktor.http.*

enum class JobState {
    /**
     * Any job which has been submitted and not yet in a final state where the number of tasks running is less than
     * the number of tasks requested
     */
    IN_QUEUE,

    /**
     * A job where all the tasks are running
     */
    RUNNING,

    /**
     * A job which has been cancelled, either by user request or system request
     */
    CANCELING,

    /**
     * A job which has terminated. The job terminated with no _scheduler_ error.
     *
     * Note: A job will complete successfully even if the user application exits with an unsuccessful status code.
     */
    SUCCESS,

    /**
     * A job which has terminated with a failure.
     *
     * Note: A job will fail _only_ if it is the scheduler's fault
     */
    FAILURE;

    fun isFinal(): Boolean =
        when (this) {
            SUCCESS, FAILURE -> true
            else -> false
        }
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class Job(
    @UCloudApiDoc(
        "Unique identifier for this job.\n\n" +
            "UCloud guarantees that no other job, regardless of compute provider, has the same unique identifier."
    )
    val id: String,

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

    val billing: JobBilling,

    @UCloudApiDoc("The parameters used to launch this job.\n\n" +
        "This property is always available but must be explicitly requested.")
    val parameters: JobParameters,

    @UCloudApiDoc("Information regarding the output of this job.")
    val output: JobOutput? = null,

    val vnc: JobVncLink? = null,

    val web: JobWebLink? = null,

    val shell: JobShellLink? = null,
)

data class JobBilling(
    @UCloudApiDoc("The amount of credits charged to the `owner` of this job")
    val creditsCharged: Long,

    @UCloudApiDoc("The unit price of this job")
    val pricePerUnit: Long,
)

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
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
    @UCloudApiDoc("A reference to the application which this job should execute")
    val application: NameAndVersion,

    @UCloudApiDoc("A reference to the product that this job will be executed on")
    val product: ComputeProductReference,

    @UCloudApiDoc(
        "A name for this job assigned by the user.\n\n" +
            "The name can help a user identify why and with which parameters a job was started. " +
            "This value is suitable for display in user interfaces."
    )
    val name: String? = null,

    @UCloudApiDoc(
        "The number of replicas to start this job in\n\n" +
            "The `resources` supplied will be mounted in every replica. Some `resources` might only be supported in " +
            "an 'exclusive use' mode. This will cause the job to fail if `replicas != 1`."
    )
    val replicas: Int = 1,

    @UCloudApiDoc(
        "Allows the job to be started even when a job is running in an identical configuration\n\n" +
            "By default, UCloud will prevent you from accidentally starting two jobs with identical configuration. " +
            "This field must be set to `true` to allow you to create two jobs with identical configuration."
    )
    val allowDuplicateJob: Boolean = false,

    @UCloudApiDoc(
        "Parameters which are consumed by the job\n\n" +
            "The available parameters are defined by the `application`. " +
            "This attribute is not included by default unless `includeParameters` is specified."
    )
    val parameters: Map<String, AppParameterValue>? = null,

    @UCloudApiDoc(
        "Additional resources which are made available into the job\n\n" +
            "This attribute is not included by default unless `includeParameters` is specified. " +
            "Note: Not all resources can be attached to a job. UCloud supports the following parameter types as " +
            "resources:\n\n" +
            " - `file`\n" +
            " - `peer`\n" +
            " - `network`\n" +
            " - `block_storage`\n" +
            " - `ingress`\n"
    )
    val resources: List<AppParameterValue>? = null,

    @UCloudApiDoc(
        "Time allocation for the job\n\n" +
            "This value can be `null` which signifies that the job should not (automatically) expire. " +
            "Note that some providers do not support `null`. When this value is not `null` it means that the job " +
            "will be terminated, regardless of result, after the duration has expired. Some providers support " +
            "extended this duration via the `extend` operation."
    )
    val timeAllocation: SimpleDuration? = null,

    @UCloudApiDoc("The resolved product referenced by `product`.\n\n" +
        "This attribute is not included by default unless `includeProduct` is specified.")
    val resolvedProduct: Product.Compute? = null,

    @UCloudApiDoc("The resolved application referenced by `application`.\n\n" +
        "This attribute is not included by default unless `includeApplication` is specified.")
    val resolvedApplication: Application? = null,
) {
    init {
        if (name != null && !name.matches(nameRegex)) {
            throw RPCException(
                "Provided job name is invalid. It cannot contain any special characters",
                HttpStatusCode.BadRequest
            )
        }
    }

    companion object {
        private val nameRegex = Regex("""[\w _-]+""")
    }
}

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
    @UCloudApiDoc("Includes `parameters.parameters` and `parameters.resources`")
    val includeParameters: Boolean?

    @UCloudApiDoc("Includes `web`")
    val includeWeb: Boolean?

    @UCloudApiDoc("Includes `vnc`")
    val includeVnc: Boolean?

    @UCloudApiDoc("Includes `shell`")
    val includeShell: Boolean?

    @UCloudApiDoc("Includes `updates`")
    val includeUpdates: Boolean?

    @UCloudApiDoc("Includes `parameters.resolvedApplication`")
    val includeApplication: Boolean?

    @UCloudApiDoc("Includes `parameters.resolvedProduct`")
    val includeProduct: Boolean?
}

fun JobDataIncludeFlags(
    includeParameters: Boolean? = null,
    includeWeb: Boolean? = null,
    includeVnc: Boolean? = null,
    includeShell: Boolean? = null,
    includeUpdates: Boolean? = null,
    includeApplication: Boolean? = null,
    includeProduct: Boolean? = null,
) = JobDataIncludeFlagsImpl(
    includeParameters,
    includeWeb,
    includeVnc,
    includeShell,
    includeUpdates,
    includeApplication,
    includeProduct
)

data class JobDataIncludeFlagsImpl(
    override val includeParameters: Boolean? = null,
    override val includeWeb: Boolean? = null,
    override val includeVnc: Boolean? = null,
    override val includeShell: Boolean? = null,
    override val includeUpdates: Boolean? = null,
    override val includeApplication: Boolean? = null,
    override val includeProduct: Boolean? = null,
) : JobDataIncludeFlags

typealias JobsCreateRequest = BulkRequest<JobParameters>

data class JobsCreateResponse(val ids: List<String>)

data class JobsRetrieveRequest(
    val id: String,
    override val includeParameters: Boolean? = null,
    override val includeWeb: Boolean? = null,
    override val includeVnc: Boolean? = null,
    override val includeShell: Boolean? = null,
    override val includeUpdates: Boolean? = null,
    override val includeApplication: Boolean? = null,
    override val includeProduct: Boolean? = null,
) : JobDataIncludeFlags
typealias JobsRetrieveResponse = Job

data class JobsBrowseRequest(
    override val itemsPerPage: Int,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
    override val includeParameters: Boolean? = null,
    override val includeWeb: Boolean? = null,
    override val includeVnc: Boolean? = null,
    override val includeShell: Boolean? = null,
    override val includeUpdates: Boolean? = null,
    override val includeApplication: Boolean? = null,
    override val includeProduct: Boolean? = null,
) : WithPaginationRequestV2, JobDataIncludeFlags
typealias JobsBrowseResponse = PageV2<Job>

typealias JobsDeleteRequest = BulkRequest<FindByStringId>
typealias JobsDeleteResponse = Unit

typealias JobsFollowRequest = FindByStringId

data class JobsFollowResponse(
    val updates: List<JobUpdate>,
    val log: List<JobsLog>,
)

data class JobsLog(val rank: Int, val stdout: String?, val stderr: String?)

typealias JobsExtendRequest = BulkRequest<JobsExtendRequestItem>
typealias JobsExtendResponse = Unit

data class JobsExtendRequestItem(
    val jobId: String,
    val requestedTime: SimpleDuration,
)

typealias JobsSuspendRequest = BulkRequest<JobsSuspendRequestItem>
typealias JobsSuspendResponse = Unit
typealias JobsSuspendRequestItem = FindByStringId

val Job.files: List<AppParameterValue.File>
    get() {
        return (parameters.resources?.filterIsInstance<AppParameterValue.File>() ?: emptyList()) +
            (parameters.parameters?.values?.filterIsInstance<AppParameterValue.File>() ?: emptyList())
    }

val Job.peers: List<AppParameterValue.Peer>
    get() {
        return (parameters.resources?.filterIsInstance<AppParameterValue.Peer>() ?: emptyList()) +
            (parameters.parameters?.values?.filterIsInstance<AppParameterValue.Peer>() ?: emptyList())
    }

val Job.ingressPoints: List<AppParameterValue.Ingress>
    get() {
        return (parameters.resources?.filterIsInstance<AppParameterValue.Ingress>() ?: emptyList()) +
            (parameters.parameters?.values?.filterIsInstance<AppParameterValue.Ingress>() ?: emptyList())
    }

val Job.networks: List<AppParameterValue.Network>
    get() {
        return (parameters.resources?.filterIsInstance<AppParameterValue.Network>() ?: emptyList()) +
            (parameters.parameters?.values?.filterIsInstance<AppParameterValue.Network>() ?: emptyList())
    }

val Job.blockStorage: List<AppParameterValue.BlockStorage>
    get() {
        return (parameters.resources?.filterIsInstance<AppParameterValue.BlockStorage>() ?: emptyList()) +
            (parameters.parameters?.values?.filterIsInstance<AppParameterValue.BlockStorage>() ?: emptyList())
    }

val Job.currentState: JobState
    get() = updates.findLast { it.state != null }?.state ?: error("job contains no states")

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
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
                be followed using the ${docCallRef(::retrieve)}, ${docCallRef(::browse)}, ${docCallRef(::follow)} calls.
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
            summary = "Extend the duration of one or more jobs"
            description = """
                This will extend the duration of one or more jobs in a bulk request. Extension of a job will add to
                the current deadline of a job. Note that not all providers support this features. Providers which
                do not support it will have it listed in their manifest. If a provider is asked to extend a deadline
                when not supported it will send back a 400 bad request.
                
                This call makes no guarantee that all jobs are extended in a single transaction. If the provider
                supports it, then all requests made against a single provider should be made in a single transaction.
                Clients can determine if their extension request against a specific target was successful by checking
                if the time remaining of the job has been updated.
                
                This call will return 2XX if all jobs have successfully been extended. The job will fail with a
                status code from the provider one the first extension which fails. UCloud will not attempt to extend
                more jobs after the first failure.
            """.trimIndent()
        }
    }

    val suspend = call<JobsSuspendRequest, JobsSuspendResponse, CommonErrorMessage>("suspend") {
        httpUpdate(baseContext, "suspend")

        documentation {
            summary = "Suspend a job"
            description = """
                Suspends the job, putting it in a paused state. Not all compute backends support this operation.
                For compute backends which deals with Virtual Machines this will shutdown the Virtual Machine
                without deleting any data.
            """.trimIndent()
        }
    }
}
