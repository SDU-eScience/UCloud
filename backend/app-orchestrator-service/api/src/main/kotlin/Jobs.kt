package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.accounting.api.providers.SupportByProvider
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.debug.DebugSensitive
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Time
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

@Serializable
@UCloudApiStable
data class ExportedParametersRequest(
    val application: NameAndVersion,
    val product: ProductReference,
    val name: String?,
    val replicas: Int,
    @Contextual
    val parameters: JsonObject,
    val resources: List<@Contextual JsonObject>,
    val timeAllocation: SimpleDuration?,
    @Contextual
    val resolvedProduct: JsonObject? = null,
    @Contextual
    val resolvedApplication: JsonObject? = null,
    @Contextual
    val resolvedSupport: JsonObject? = null,
    val allowDuplicateJob: Boolean = true,
    val sshEnabled: Boolean = false,
)

@UCloudApiStable
@Serializable
data class ExportedParameters(
    val siteVersion: Int,
    val request: ExportedParametersRequest,
    val resolvedResources: Resources = Resources(),

    // Backwards compatible information
    @Contextual
    val machineType: JsonObject,
) {
    @Serializable
    @UCloudApiStable
    data class Resources(
        val ingress: Map<String, Ingress> = emptyMap()
    )
}

@UCloudApiDoc("A value describing the current state of a Job", importance = 350)
@Serializable
@UCloudApiStable
enum class JobState {
    @UCloudApiDoc("""
        Any Job which is not yet ready
        
        More specifically, this state should apply to any $TYPE_REF Job for which all of the following holds:
        
        - The $TYPE_REF Job has been created
        - It has never been in a final state
        - The number of `replicas` which are running is less than the requested amount
    """)
    IN_QUEUE,

    @UCloudApiDoc("""
        A Job where all the tasks are running
        
        
        More specifically, this state should apply to any $TYPE_REF Job for which all of the following holds:
        
        - All `replicas` of the $TYPE_REF Job have been started
        
        ---
        
        __📝 NOTE:__ A $TYPE_REF Job can be `RUNNING` without actually being ready. For example, if a $TYPE_REF Job 
        exposes a web interface, then the web-interface doesn't have to be available yet. That is, the server might
        still be running its initialization code.
        
        ---
    """)
    RUNNING,

    @UCloudApiDoc("""
        A Job which has been cancelled but has not yet terminated
        
        ---
        
        __📝 NOTE:__ This is only a temporary state. The $TYPE_REF Job is expected to eventually transition to a final
        state, typically the `SUCCESS` state.
        
        ---
    """)
    CANCELING,

    @UCloudApiDoc("""
        A Job which has terminated without a _scheduler_ error  
        
        ---
    
        __📝 NOTE:__ A $TYPE_REF Job will complete successfully even if the user application exits with an unsuccessful 
        status code.
        
        ---
    """)
    SUCCESS,

    @UCloudApiDoc("""
        A Job which has terminated with a failure
        
        ---
    
        __📝 NOTE:__ A $TYPE_REF Job should _only_ fail if it is the scheduler's fault
        
        ---
    """)
    FAILURE,

    @UCloudApiDoc("""
        A Job which has expired and was terminated as a result
        
        This state should only be used if the [`timeAllocation`]($TYPE_REF_LINK JobSpecification) has expired. Any other
        form of cancellation/termination should result in either `SUCCESS` or `FAILURE`.
    """)
    EXPIRED,

    @UCloudApiDoc("""
        A Job which might have previously run but is no longer running, this state is not final.

        Unlike SUCCESS and FAILURE a Job can transition from this state to one of the active states again.
    """)
    SUSPENDED;

    fun isFinal(): Boolean =
        when (this) {
            SUCCESS, FAILURE, EXPIRED -> true
            else -> false
        }
}

@UCloudApiDoc("A value describing a type of 'interactive' session", importance = 300)
@Serializable
@UCloudApiStable
enum class InteractiveSessionType {
    WEB,
    VNC,
    SHELL
}

@UCloudApiDoc(
    """A `Job` in UCloud is the core abstraction used to describe a unit of computation.

They provide users a way to run their computations through a workflow similar to their own workstations but scaling to
much bigger and more machines. In a simplified view, a $TYPE_REF Job describes the following information:

- The `Application` which the provider should/is/has run (see [app-store](/docs/developer-guide/orchestration/compute/appstore/apps.md))
- The [input parameters]($TYPE_REF_LINK dk.sdu.cloud.app.store.api.ApplicationParameter) required by a `Job`
- A reference to the appropriate [compute infrastructure]($TYPE_REF_LINK dk.sdu.cloud.accounting.api.Product), this
  includes a reference to the _provider_

A `Job` is started by a user request containing the `specification` of a $TYPE_REF Job. This information is verified by the UCloud
orchestrator and passed to the provider referenced by the $TYPE_REF Job itself. Assuming that the provider accepts this
information, the $TYPE_REF Job is placed in its initial state, `IN_QUEUE`. You can read more about the requirements of the
compute environment and how to launch the software
correctly [here](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md).

At this point, the provider has acted on this information by placing the $TYPE_REF Job in its own equivalent of
a job queue. Once the provider realizes that the $TYPE_REF Job is running, it will contact UCloud and place the 
$TYPE_REF Job in the `RUNNING` state. This indicates to UCloud that log files can be retrieved and that interactive
interfaces (`VNC`/`WEB`) are available.

Once the `Application` terminates at the provider, the provider will update the state to `SUCCESS`. A $TYPE_REF Job has
terminated successfully if no internal error occurred in UCloud and in the provider. This means that a $TYPE_REF Job whose
software returns with a non-zero exit code is still considered successful. A $TYPE_REF Job might, for example, be placed
in `FAILURE` if the `Application` crashed due to a hardware/scheduler failure. Both `SUCCESS` or `FAILURE` are terminal
state. Any $TYPE_REF Job which is in a terminal state can no longer receive any updates or change its state.

At any point after the user submits the $TYPE_REF Job, they may request cancellation of the $TYPE_REF Job. This will
stop the $TYPE_REF Job, delete any
[ephemeral resources](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md#ephemeral-resources) and release
any [bound resources](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md#resources).""", importance = 500
)
@UCloudApiStable
@Serializable
data class Job(
    @UCloudApiDoc(
        "Unique identifier for this job.\n\n" +
            "UCloud guarantees that no other job, regardless of compute provider, has the same unique identifier."
    )
    override val id: String,

    @UCloudApiDoc("A reference to the owner of this job")
    override val owner: ResourceOwner,

    @UCloudApiDoc(
        "A list of status updates from the compute backend.\n\n" +
            "The status updates tell a story of what happened with the job. " +
            "This list is ordered by the timestamp in ascending order. " +
            "The current state of the job will always be the last element. " +
            "`updates` is guaranteed to always contain at least one element."
    )
    override val updates: List<JobUpdate>,

    @UCloudApiDoc(
        "The specification used to launch this job.\n\n" +
            "This property is always available but must be explicitly requested."
    )
    override val specification: JobSpecification,

    @UCloudApiDoc("A summary of the `Job`'s current status")
    override val status: JobStatus,

    override val createdAt: Long,

    @UCloudApiDoc("Information regarding the output of this job.")
    val output: JobOutput? = null,

    override val permissions: ResourcePermissions? = null,
) : Resource<Product.Compute, ComputeSupport>, DocVisualizable {
    companion object {
        fun fromSpecification(
            id: String,
            actorAndProject: ActorAndProject,
            specification: JobSpecification
        ): Job {
            return Job(
                id,
                ResourceOwner(actorAndProject.actor.safeUsername(), actorAndProject.project),
                emptyList(),
                specification,
                JobStatus(JobState.IN_QUEUE),
                Time.now()
            )
        }
    }
}

@UCloudApiStable
@Serializable
data class JobStatus(
    @UCloudApiDoc(
        "The current of state of the `Job`.\n\n" +
            "This will match the latest state set in the `updates`"
    )
    val state: JobState,

    val jobParametersJson: ExportedParameters? = null,

    @UCloudApiDoc(
        "Timestamp matching when the `Job` most recently transitioned to the `RUNNING` state.\n\n" +
            "For `Job`s which suspend this might occur multiple times. This will always point to the latest point" +
            "in time it started running."
    )
    val startedAt: Long? = null,

    @UCloudApiDoc(
        "Timestamp matching when the `Job` is set to expire.\n\n" +
            "This is generally equal to `startedAt + timeAllocation`. Note that this field might be `null` if " +
            "the `Job` has no associated deadline. For `Job`s that suspend however, this is more likely to be" +
            "equal to the initial `RUNNING` state + `timeAllocation`."
    )
    val expiresAt: Long? = null,

    @UCloudApiDoc(
        "The resolved application referenced by `application`.\n\n" +
            "This attribute is not included by default unless `includeApplication` is specified."
    )
    val resolvedApplication: Application? = null,

    override var resolvedSupport: ResolvedSupport<Product.Compute, ComputeSupport>? = null,

    override var resolvedProduct: Product.Compute? = null,

    val allowRestart: Boolean = false,
) : ResourceStatus<Product.Compute, ComputeSupport>

@Serializable
@UCloudApiOwnedBy(Jobs::class)
@UCloudApiStable
data class JobUpdate(
    val state: JobState? = null,
    val outputFolder: String? = null,
    override var status: String? = null,
    val expectedState: JobState? = null,
    val expectedDifferentState: Boolean? = null,
    val newTimeAllocation: Long? = null,
    val allowRestart: Boolean? = null,
    val newMounts: List<String>? = null,
    override var timestamp: Long = 0L
) : ResourceUpdate {
    init {
        if (allowRestart == true) {
            when (state) {
                JobState.SUCCESS,
                JobState.FAILURE,
                JobState.SUSPENDED -> {
                    // This is OK
                }

                else -> {
                    throw RPCException(
                        "Cannot set `allowRestart` with a state of $state!",
                        HttpStatusCode.BadRequest
                    )
                }
            }
        }
    }
}

@UCloudApiDoc("A specification of a Job", importance = 400)
@Serializable
@UCloudApiStable
data class JobSpecification(
    @UCloudApiDoc("A reference to the application which this job should execute")
    val application: NameAndVersion,

    @UCloudApiDoc("A reference to the product that this job will be executed on")
    override var product: ComputeProductReference,

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
    @UCloudApiInternal(InternalLevel.BETA)
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
    var resources: List<AppParameterValue>? = null,

    @UCloudApiDoc(
        "Time allocation for the job\n\n" +
            "This value can be `null` which signifies that the job should not (automatically) expire. " +
            "Note that some providers do not support `null`. When this value is not `null` it means that the job " +
            "will be terminated, regardless of result, after the duration has expired. Some providers support " +
            "extended this duration via the `extend` operation."
    )
    var timeAllocation: SimpleDuration? = null,

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    @UCloudApiDoc(
        """
            An optional path to the file which the user selected with the "Open with..." feature.
            
            This value is null if the application is not launched using the "Open with..." feature. The value of this
            is passed to the compute environment in a provider specific way. We encourage providers to expose this as
            an environment variable named `UCLOUD_OPEN_WITH_FILE` containing the absolute path of the file (in the
            current environment). Remember that this path is the _UCloud_ path to the file and not the provider's path.
        """
    )
    val openedFile: String? = null,

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    @UCloudApiDoc(
        """
            A flag which indicates if this job should be restarted on exit.
            
            Not all providers support this feature and the Job will be rejected if not supported. This information can
            also be queried through the product support feature.

            If this flag is `true` then the Job will automatically be restarted when the provider notifies the
            orchestrator about process termination. It is the responsibility of the orchestrator to notify the provider
            about restarts. If the restarts are triggered by the provider, then the provider must not notify the
            orchestrator about the termination. The orchestrator will trigger a new `create` request in a timely manner.
            The orchestrator decides when to trigger a new `create`. For example, if a process is terminating often,
            then the orchestrator might decide to wait before issuing a new `create`.
        """
    )
    val restartOnExit: Boolean? = null,

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    @UCloudApiDoc(
        """
            A flag which indicates that this job should use the built-in SSH functionality of the application/provider
            
            This flag can only be true of the application itself is marked as SSH enabled. When this flag is true, 
            an SSH server will be started which allows the end-user direct access to the associated compute workload.
        """
    )
    val sshEnabled: Boolean? = null,
) : ResourceSpecification {
    init {
        if (name != null && !name.matches(nameRegex)) {
            throw RPCException(
                "Provided job name is invalid. It cannot contain any special characters",
                HttpStatusCode.BadRequest
            )
        }

        if (name != null && name.length > 200) {
            throw RPCException("Provided job name is too long", HttpStatusCode.BadRequest)
        }
    }

    companion object {
        private val nameRegex = Regex("""[\w ():_-]+""")
    }
}

typealias ComputeProductReference = ProductReference

@UCloudApiStable
@Serializable
data class JobOutput(
    val outputFolder: String? = null,
)

@UCloudApiDoc("Flags used to tweak read operations of Jobs", importance = 300)
@Serializable
@UCloudApiStable
data class JobIncludeFlags(
    val filterApplication: String? = null,

    val filterState: JobState? = null,

    @UCloudApiDoc("Includes `specification.parameters` and `specification.resources`")
    val includeParameters: Boolean? = null,

    @UCloudApiDoc("Includes `specification.resolvedApplication`")
    val includeApplication: Boolean? = null,

    @UCloudApiDoc("Includes `specification.resolvedProduct`")
    override val includeProduct: Boolean = false,

    override val includeOthers: Boolean = false,
    override val includeUpdates: Boolean = false,
    override val includeSupport: Boolean = false,
    override val filterCreatedBy: String? = null,
    override val filterCreatedAfter: Long? = null,
    override val filterCreatedBefore: Long? = null,
    override val filterProvider: String? = null,
    override val filterProductId: String? = null,
    override val filterProductCategory: String? = null,
    override val filterProviderIds: String? = null,
    override val filterIds: String? = null,
    override val hideProductId: String? = null,
    override val hideProductCategory: String? = null,
    override val hideProvider: String? = null,
) : ResourceIncludeFlags

@Serializable
@UCloudApiExperimental(ExperimentalLevel.BETA)
data class JobsRetrieveUtilizationRequest(val jobId: String)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.BETA)
data class JobsRetrieveUtilizationResponse(
    @UCloudApiDoc("The total capacity of the entire compute system")
    val capacity: CpuAndMemory,
    @UCloudApiDoc("The capacity currently in use, by running jobs, of the entire compute system")
    val usedCapacity: CpuAndMemory,
    @UCloudApiDoc("The system of the queue")
    val queueStatus: QueueStatus,
)

typealias JobsFollowRequest = FindByStringId

@Serializable
@UCloudApiStable
data class JobsFollowResponse(
    val updates: List<JobUpdate>,
    val log: List<JobsLog>,
    val newStatus: JobStatus? = null,
)

@Serializable
@UCloudApiStable
data class JobsLog(val rank: Int, val stdout: String? = null, val stderr: String? = null)

typealias JobsExtendRequest = BulkRequest<JobsExtendRequestItem>
typealias JobsExtendResponse = BulkResponse<Unit?>

@Serializable
@UCloudApiStable
data class JobsExtendRequestItem(
    val jobId: String,
    val requestedTime: SimpleDuration,
)

typealias JobsSuspendRequest = BulkRequest<JobsSuspendRequestItem>
typealias JobsSuspendResponse = BulkResponse<Unit?>
typealias JobsSuspendRequestItem = FindByStringId

typealias JobsUnsuspendRequest = BulkRequest<JobsUnsuspendRequestItem>
typealias JobsUnsuspendResponse = BulkResponse<Unit?>
typealias JobsUnsuspendRequestItem = FindByStringId

val Job.files: List<AppParameterValue.File>
    get() {
        return (specification.resources?.filterIsInstance<AppParameterValue.File>() ?: emptyList()) +
            (specification.parameters?.values?.filterIsInstance<AppParameterValue.File>() ?: emptyList())
    }

val Job.peers: List<AppParameterValue.Peer>
    get() {
        return (specification.resources?.filterIsInstance<AppParameterValue.Peer>() ?: emptyList()) +
            (specification.parameters?.values?.filterIsInstance<AppParameterValue.Peer>() ?: emptyList())
    }

val JobSpecification.ingressPoints: List<AppParameterValue.Ingress>
    get() {
        return (resources?.filterIsInstance<AppParameterValue.Ingress>() ?: emptyList()) +
                (parameters?.values?.filterIsInstance<AppParameterValue.Ingress>() ?: emptyList())
    }

val Job.ingressPoints: List<AppParameterValue.Ingress>
    get() = specification.ingressPoints

val JobSpecification.networks: List<AppParameterValue.Network>
    get() {
        return (resources?.filterIsInstance<AppParameterValue.Network>() ?: emptyList()) +
            (parameters?.values?.filterIsInstance<AppParameterValue.Network>() ?: emptyList())
    }

val Job.networks: List<AppParameterValue.Network>
    get() = specification.networks

val Job.blockStorage: List<AppParameterValue.BlockStorage>
    get() {
        return (specification.resources?.filterIsInstance<AppParameterValue.BlockStorage>() ?: emptyList()) +
            (specification.parameters?.values?.filterIsInstance<AppParameterValue.BlockStorage>() ?: emptyList())
    }

val Job.licences: List<AppParameterValue.License>
    get() {
        return (specification.resources?.filterIsInstance<AppParameterValue.License>() ?: emptyList()) +
            (specification.parameters?.values?.filterIsInstance<AppParameterValue.License>() ?: emptyList())
    }

typealias JobsOpenInteractiveSessionRequest = BulkRequest<JobsOpenInteractiveSessionRequestItem>

@Serializable
@UCloudApiStable
data class JobsOpenInteractiveSessionRequestItem(
    val id: String,
    val rank: Int,
    val sessionType: InteractiveSessionType,
)

typealias JobsOpenInteractiveSessionResponse = BulkResponse<OpenSessionWithProvider?>

@Serializable
@UCloudApiStable
data class OpenSessionWithProvider(
    val providerDomain: String,
    val providerId: String,
    val session: OpenSession,
) : DebugSensitive {
    override fun removeSensitiveInformation(): JsonElement = JsonNull
}

@Serializable
@UCloudApiStable
sealed class OpenSession : DebugSensitive {
    abstract val jobId: String
    abstract val rank: Int

    @UCloudApiDoc("Domain override, which will be forwarded to the end-user. This overrides the domain used by UCloud/Core. Must contain scheme (e.g. https://).")
    abstract val domainOverride: String?

    override fun removeSensitiveInformation(): JsonElement = JsonNull

    @Serializable
    @SerialName("shell")
    @UCloudApiStable
    data class Shell(
        override val jobId: String,
        override val rank: Int,
        val sessionIdentifier: String,
        override val domainOverride: String? = null,
    ) : OpenSession()

    @Serializable
    @SerialName("web")
    @UCloudApiStable
    data class Web(
        override val jobId: String,
        override val rank: Int,
        val redirectClientTo: String,
        override val domainOverride: String? = null,
    ) : OpenSession()

    @Serializable
    @SerialName("vnc")
    @UCloudApiStable
    data class Vnc(
        override val jobId: String,
        override val rank: Int,
        val url: String,
        val password: String? = null,
        override val domainOverride: String? = null,
    ) : OpenSession()
}

@UCloudApiStable
object Jobs : ResourceApi<Job, JobSpecification, JobUpdate, JobIncludeFlags, JobStatus, Product.Compute,
    ComputeSupport>("jobs") {
    init {
        title = "Jobs"
        description = """
Jobs in UCloud are the core abstraction used to describe units of computation.

${Resources.readMeFirst}

The compute system allows for a variety of computational workloads to run on UCloud. All compute jobs
in UCloud run an [application](/docs/developer-guide/orchestration/compute/appstore/apps.md) on one or more
['nodes']($TYPE_REF_LINK dk.sdu.cloud.accounting.api.Product.Compute). The type of applications determine
what the job does:
 
- __Batch__ applications provide support for long running computational workloads (typically containerized)
- __Web__ applications provide support for applications which expose a graphical web-interface
- __VNC__ applications provide support for interactive remote desktop workloads
- __Virtual machine__ applications provide support for more advanced workloads which aren't easily
  containerized or require special privileges

Every $TYPE_REF Job is created from a [`specification`]($TYPE_REF_LINK JobSpecification). The specification
contains [input parameters]($TYPE_REF_LINK dk.sdu.cloud.app.store.api.ApplicationParameter), such as files
and application flags, and additional resources. Zero or more resources can be connected to an application,
and provide services such as:

- Networking between multiple $TYPE_REF Job s
- [Storage](/docs/developer-guide/orchestration/storage/files.md)
- [Public links](/docs/developer-guide/orchestration/compute/ingress.md)
- [Public IPs](/docs/developer-guide/orchestration/compute/ips.md)
- [Software licenses](/docs/developer-guide/orchestration/compute/license.md)

---

__📝 Provider Note:__ This is the API exposed to end-users. See the table below for other relevant APIs.

| End-User | Provider (Ingoing) | Control (Outgoing) |
|----------|--------------------|--------------------|
| [`Jobs`](/docs/developer-guide/orchestration/compute/jobs.md) | [`JobsProvider`](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md) | [`JobsControl`](/docs/developer-guide/orchestration/compute/providers/jobs/outgoing.md) |

---
        """.trimIndent()

        serializerLookupTable = mapOf(
            serializerEntry(OpenSession.serializer()),
            serializerEntry(JobsProviderFollowRequest.serializer()),
            serializerEntry(ShellRequest.serializer()),
            serializerEntry(ShellResponse.serializer())
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        Job.serializer(),
        typeOfIfPossible<Job>(),
        JobSpecification.serializer(),
        typeOfIfPossible<JobSpecification>(),
        JobUpdate.serializer(),
        typeOfIfPossible<JobUpdate>(),
        JobIncludeFlags.serializer(),
        typeOfIfPossible<JobIncludeFlags>(),
        JobStatus.serializer(),
        typeOfIfPossible<JobStatus>(),
        ComputeSupport.serializer(),
        typeOfIfPossible<ComputeSupport>(),
        Product.Compute.serializer(),
        typeOfIfPossible<Product.Compute>(),
    )

    private const val createUseCase = "create"
    private const val followUseCase = "follow"
    private const val terminalUseCase = "terminal"
    private const val peerUseCase = "peers"
    private const val ingressUseCase = "ingress"
    private const val softwareLicenseUseCase = "license"
    private const val vncUseCase = "vnc"
    private const val webUseCase = "web"
    private const val permissionUseCase = "permissions"
    private const val creditsUseCase = "credits"
    private const val extendAndCancelUseCase = "extendAndCancel"

    override fun documentation() {
        useCase(
            createUseCase,
            "Creating a simple batch Job",
            trigger = "User initiated",
            preConditions = listOf(
                "User has been granted credits for using the selected machine"
            ),
            postConditions = listOf(
                "A Job is started in the user's workspace"
            ),
            flow = {
                val user = basicUser()

                comment("The user finds an interesting application from the catalog")

                val metadata = ApplicationMetadata(
                    "a-batch-application",
                    version = "1.0.0",
                    authors = listOf("UCloud"),
                    title = "A Batch Application",
                    description = "This is a batch application",
                    public = true
                )
                val tags = listOf("very-scientific")
                val favorite = false
                success(
                    AppStore.listAll,
                    PaginationRequest(50, 0),
                    Page(
                        1,
                        50,
                        0,
                        listOf(
                            ApplicationSummaryWithFavorite(
                                metadata,
                                favorite,
                                tags
                            ),
                        )
                    ),
                    user,
                    "applications"
                )

                comment("The user selects the first application ('batch' in version '1.0.0')")
                comment("The user requests additional information about the application")

                success(
                    AppStore.findByNameAndVersion,
                    FindApplicationAndOptionalDependencies("a-batch-application", "1.0.0"),
                    ApplicationWithFavoriteAndTags(
                        metadata,
                        ApplicationInvocationDescription(
                            ToolReference(
                                "batch-tool", "1.0.0", Tool(
                                    "user",
                                    1632979836013,
                                    1632979836013,
                                    NormalizedToolDescription(
                                        NameAndVersion("batch-tool", "1.0.0"),
                                        null,
                                        1,
                                        SimpleDuration(1, 0, 0),
                                        emptyList(),
                                        listOf("UCloud"),
                                        "Batch tool",
                                        "Batch tool",
                                        ToolBackend.DOCKER,
                                        "None",
                                        "dreg.cloud.sdu.dk/batch/batch:1.0.0"
                                    )
                                )
                            ),
                            listOf(
                                WordInvocationParameter("batch"),
                                VariableInvocationParameter(listOf("var"))
                            ),
                            listOf(
                                ApplicationParameter.Text("var", description = "An example input variable")
                            ),
                            listOf("*")
                        ),
                        favorite,
                        tags,
                    ),
                    user,
                    "application"
                )

                comment("The user looks for a suitable machine")

                success(
                    Products.browse,
                    ProductsBrowseRequest(itemsPerPage = 50, filterArea = ProductType.COMPUTE),
                    ProductsBrowseResponse(
                        50,
                        listOf(
                            Product.Compute(
                                "example-compute",
                                1_000_000,
                                ProductCategoryId("example-compute", "example"),
                                "An example compute product",
                                cpu = 10,
                                memoryInGigs = 20,
                                gpu = 0,
                                unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE
                            ),
                        ),
                        null
                    ),
                    user,
                    "machineTypes"
                )

                comment("The user starts the Job with input based on previous requests")

                success(
                    create,
                    bulkRequestOf(
                        JobSpecification(
                            NameAndVersion(metadata.name, metadata.version),
                            ProductReference("example-compute", "example-compute", "example"),
                            parameters = mapOf(
                                "var" to AppParameterValue.Text("Example")
                            )
                        )
                    ),
                    BulkResponse(listOf(FindByStringId("48920"))),
                    user
                )
            }
        )

        useCase(
            followUseCase,
            "Following the progress of a Job",
            preConditions = listOf(
                "A running Job, with ID 123"
            ),
            flow = {
                val user = basicUser()
                subscription(follow, JobsFollowRequest("123"), user) {
                    success(
                        JobsFollowResponse(
                            emptyList(),
                            emptyList(),
                            JobStatus(JobState.IN_QUEUE)
                        )
                    )
                    success(
                        JobsFollowResponse(
                            listOf(
                                JobUpdate(
                                    JobState.RUNNING,
                                    status = "The job is now running",
                                    timestamp = 1633680152778
                                )
                            ),
                            emptyList(),
                            JobStatus(JobState.RUNNING)
                        )
                    )
                    success(
                        JobsFollowResponse(
                            emptyList(),
                            listOf(
                                JobsLog(
                                    0,
                                    """
                                        GNU bash, version 5.0.17(1)-release (x86_64-pc-linux-gnu)
                                        Copyright (C) 2019 Free Software Foundation, Inc.
                                        License GPLv3+: GNU GPL version 3 or later <http://gnu.org/licenses/gpl.html>

                                        This is free software; you are free to change and redistribute it.
                                        There is NO WARRANTY, to the extent permitted by law.
                                    """.trimIndent()
                                )
                            ),
                            JobStatus(JobState.RUNNING)
                        )
                    )
                    success(
                        JobsFollowResponse(
                            listOf(
                                JobUpdate(
                                    JobState.SUCCESS,
                                    status = "The job is no longer running",
                                    timestamp = 1633680152778
                                )
                            ),
                            emptyList(),
                            JobStatus(JobState.SUCCESS)
                        )
                    )
                }
            }
        )

        useCase(
            terminalUseCase,
            "Starting an interactive terminal session",
            trigger = "User initiated by clicking on 'Open Terminal' of a running Job",
            preConditions = listOf(
                "A running Job with ID 123",
                "The provider must support the terminal functionality"
            ),
            flow = {
                val user = basicUser()
                success(
                    retrieveProducts,
                    Unit,
                    SupportByProvider(
                        mapOf(
                            "example" to listOf(
                                ResolvedSupport(
                                    Product.Compute(
                                        "compute-example",
                                        1_000_000,
                                        ProductCategoryId("compute-example", "example"),
                                        "An example machine",
                                        cpu = 1,
                                        memoryInGigs = 2,
                                        gpu = 0
                                    ),
                                    ComputeSupport(
                                        ProductReference("compute-example", "compute-example", "example"),
                                        ComputeSupport.Docker(
                                            enabled = true,
                                            terminal = true
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    user
                )

                comment("📝 Note: The machine has support for the 'terminal' feature")

                success(
                    openInteractiveSession,
                    bulkRequestOf(
                        JobsOpenInteractiveSessionRequestItem(
                            "123",
                            1,
                            InteractiveSessionType.SHELL
                        )
                    ),
                    BulkResponse(
                        listOf(
                            OpenSessionWithProvider(
                                "provider.example.com",
                                "example",
                                OpenSession.Shell("123", 1, "a81ea644-58f5-44d9-8e94-89f81666c441")
                            )
                        )
                    ),
                    user
                )

                comment("The session is now open and we can establish a shell connection directly with " +
                    "provider.example.com")

                val shellSession = Shells("example")
                subscription(shellSession.open, ShellRequest.Initialize("a81ea644-58f5-44d9-8e94-89f81666c441"), user) {
                    success(ShellResponse.Data("user@machine:~$ "))
                    request(ShellRequest.Input("ls -1\n"))
                    success(ShellResponse.Data("ls -1\n"))
                    success(ShellResponse.Data("hello_world.txt\n"))
                    success(ShellResponse.Data("user@machine:~$ "))
                }
            }
        )

        useCase(
            peerUseCase,
            "Connecting two Jobs together",
            trigger = "User initiated",
            flow = {
                val user = basicUser()

                comment("In this example our user wish to deploy a simple web application which connects to a " +
                        "database server")

                comment("The user first provision a database server using an Application")

                success(
                    create,
                    bulkRequestOf(
                        JobSpecification(
                            NameAndVersion("acme-database", "1.0.0"),
                            ComputeProductReference("example-compute", "example-compute", "example"),
                            "my-database",
                            parameters = mapOf(
                                "dataStore" to AppParameterValue.File("/123/acme-database")
                            )
                        )
                    ),
                    BulkResponse(listOf(FindByStringId("4101"))),
                    user
                )

                comment("The database is now `RUNNING` with the persistent from `/123/acme-database`")
                comment("""
                    By default, the UCloud firewall will not allow any ingoing connections to the Job. This firewall
                    can be updated by connecting one or more Jobs together. We will now do this using the Application.
                    "Peer" feature. This feature is commonly referred to as "Connect to Job".
                """.trimIndent())

                comment("We will now start our web-application and connect it to our existing database Job")

                success(
                    create,
                    bulkRequestOf(
                        JobSpecification(
                            NameAndVersion("acme-web-app", "1.0.0"),
                            ComputeProductReference("example-compute", "example-compute", "example"),
                            "my-web-app",
                            resources = listOf(
                                AppParameterValue.Peer("database", "4101")
                            )
                        )
                    ),
                    BulkResponse(listOf(FindByStringId("4150"))),
                    user
                )

                comment("""
                    The web-application can now connect to the database using the 'database' hostname, as specified in
                    the JobSpecification.
                """.trimIndent())
            }
        )

        useCase(
            ingressUseCase,
            "Starting a Job with a public link (Ingress)",
            flow = {
                val user = basicUser()

                comment("""
                    In this example, the user will create a Job which exposes a web-interface. This web-interface will
                    become available through a publicly accessible link.
                """.trimIndent())

                comment("First, the user creates an Ingress resource (this needs to be done once per ingress)")

                val ingressSpec = IngressSpecification(
                    "app-my-application.provider.example.com",
                    ProductReference("example-ingress", "example-ingress", "example")
                )

                success(
                    Ingresses.create,
                    bulkRequestOf(ingressSpec),
                    BulkResponse(listOf(FindByStringId("41231"))),
                    user
                )

                comment("This link can now be attached to any Application which support a web-interface")

                success(
                    create,
                    bulkRequestOf(
                        JobSpecification(
                            NameAndVersion("acme-web-app", "1.0.0"),
                            ProductReference("compute-example", "compute-example", "example"),
                            resources = listOf(
                                AppParameterValue.Ingress("41231")
                            )
                        )
                    ),
                    BulkResponse(listOf(FindByStringId("41252"))),
                    user
                )

                comment("The Application is now running, and we can access it through the public link")
                comment("""
                    The Ingress will also remain exclusively bound to the Job. It will remain like this until the Job
                    terminates. You can check the status of the Ingress simply by retrieving it.
                """.trimIndent())

                success(
                    Ingresses.retrieve,
                    ResourceRetrieveRequest(IngressIncludeFlags(), "41231"),
                    Ingress(
                        "41231",
                        ingressSpec,
                        ResourceOwner("user", null),
                        1633087693694,
                        IngressStatus(
                            listOf("41231"),
                            IngressState.READY
                        )
                    ),
                    user
                )
            }
        )

        useCase(
            softwareLicenseUseCase,
            "Using licensed software",
            preConditions = listOf(
                "User has already been granted credits for the license (typically through Grants)"
            ),
            flow = {
                val user = basicUser()
                comment("In this example, the user will run a piece of licensed software.")
                comment("First, the user must activate a copy of their license, which has previously been granted to " +
                        "them through the Grant system.")

                val licenseId = "56231"
                success(
                    Licenses.create,
                    bulkRequestOf(
                        LicenseSpecification(ProductReference("example-license", "example-license", "example"))
                    ),
                    BulkResponse(listOf(FindByStringId(licenseId))),
                    user
                )

                comment("This license can now freely be used in Jobs")

                success(
                    create,
                    bulkRequestOf(
                        JobSpecification(
                            NameAndVersion("acme-licensed-software", "1.0.0"),
                            ComputeProductReference("example-compute", "example-compute", "example"),
                            parameters = mapOf(
                                "license" to AppParameterValue.License(licenseId)
                            )
                        )
                    ),
                    BulkResponse(listOf(FindByStringId("55123"))),
                    user
                )
            }
        )

        useCase(
            vncUseCase,
            "Using a remote desktop Application (VNC)",
            flow = {
                val user = basicUser()

                comment("In this example, the user will create a Job which uses an Application that exposes a VNC " +
                        "interface")

                val jobId = "51231"
                success(
                    create,
                    bulkRequestOf(
                        JobSpecification(
                            NameAndVersion("acme-remote-desktop", "1.0.0"),
                            ComputeProductReference("example-compute", "example-compute", "example")
                        )
                    ),
                    BulkResponse(listOf(FindByStringId(jobId))),
                    user
                )

                success(
                    openInteractiveSession,
                    bulkRequestOf(JobsOpenInteractiveSessionRequestItem(jobId, 0, InteractiveSessionType.VNC)),
                    BulkResponse(listOf(
                        OpenSessionWithProvider(
                            "provider.example.com",
                            "example",
                            OpenSession.Vnc(
                                jobId,
                                0,
                                "vnc-69521c85-4811-43e6-9de3-2a48614d04ab.provider.example.com",
                                "e7ccc6e0870250073286c44545e6b41820d1db7f"
                            )
                        )
                    )),
                    user
                )

                comment("The user can now connect to the remote desktop using the VNC protocol with the above details")
                comment("""
                    NOTE: UCloud expects this to support the VNC over WebSockets, as it allows for a connection to be
                    established directly from the browser.
                    
                    You can read more about the protocol here: https://novnc.com
                """.trimIndent())
            }
        )

        useCase(
            webUseCase,
            "Using a web Application",
            flow = {
                val user = basicUser()

                comment("In this example, the user will create a Job which uses an Application that exposes a web " +
                        "interface")

                val jobId = "62342"
                success(
                    create,
                    bulkRequestOf(
                        JobSpecification(
                            NameAndVersion("acme-web-application", "1.0.0"),
                            ComputeProductReference("example-compute", "example-compute", "example")
                        )
                    ),
                    BulkResponse(listOf(FindByStringId(jobId))),
                    user
                )

                success(
                    openInteractiveSession,
                    bulkRequestOf(JobsOpenInteractiveSessionRequestItem(jobId, 0, InteractiveSessionType.WEB)),
                    BulkResponse(listOf(
                        OpenSessionWithProvider(
                            "provider.example.com",
                            "example",
                            OpenSession.Web(
                                jobId,
                                0,
                                "app-gateway.provider.example.com?token=aa2dd29a-fe83-4201-b28e-fe211f94ac9d"
                            )
                        )
                    )),
                    user
                )

                comment("The user should now proceed to the link provided in the response")
            }
        )

        useCase(
            permissionUseCase,
            "Losing access to resources",
            flow = {
                val user = basicUser()
                comment("""
                    In this example, the user will create a Job using shared resources. Later in the example, the user
                    will lose access to these resources.
                """.trimIndent())

                val start = 1633329776235L
                comment("When the user starts the Job, they have access to some shared files. These are used in the" +
                        "Job (see the resources section).")

                val jobId = "62348"
                val jobSpecification = JobSpecification(
                    NameAndVersion("acme-web-application", "1.0.0"),
                    ComputeProductReference("example-compute", "example-compute", "example"),
                    resources = listOf(
                        AppParameterValue.File("/12512/shared")
                    )
                )

                success(create, bulkRequestOf(jobSpecification), BulkResponse(listOf(FindByStringId(jobId))), user)

                comment("The Job is now running")
                comment("""
                    However, a few minutes later the share is revoked. UCloud automatically kills the Job a few minutes
                    after this. The status now reflects this.
                """.trimIndent())

                success(
                    retrieve,
                    ResourceRetrieveRequest(JobIncludeFlags(), jobId),
                    Job(
                        jobId,
                        ResourceOwner("user", null),
                        listOf(
                            JobUpdate(
                                JobState.IN_QUEUE,
                                status = "Your job is now waiting in the queue!",
                                timestamp = start + (1000L * 60 * 60 * 24 * 3)
                            ),
                            JobUpdate(
                                JobState.RUNNING,
                                status = "Your job is now running!",
                                timestamp = start + (1000L * 60 * 60 * 24 * 3) + 5000
                            ),
                            JobUpdate(
                                JobState.SUCCESS,
                                status = "Your job has been terminated (Lost permissions)",
                                timestamp = start + (1000L * 60 * 60 * 24 * 3) + 120_000 + 5000
                            )
                        ),
                        jobSpecification,
                        JobStatus(JobState.SUCCESS),
                        start + (1000L * 60 * 60 * 24 * 3)
                    ),
                    user
                )
            }
        )

        useCase(
            creditsUseCase,
            "Running out of compute credits",
            flow = {
                val user = basicUser()
                comment("In this example, the user will create a Job and eventually run out of compute credits.")

                val start = 1633329776235L
                comment("When the user creates the Job, they have enough credits")
                success(
                    Wallets.browse,
                    WalletBrowseRequest(),
                    PageV2(50, listOf(
                        Wallet(
                            WalletOwner.User("user"),
                            ProductCategoryId("example-compute", "example"),
                            listOf(
                                WalletAllocation(
                                    "1254151",
                                    listOf("1254151"),
                                    500,
                                    1_000_000 * 500L,
                                    500,
                                    start,
                                    null,
                                    2
                                )
                            ),
                            AllocationSelectorPolicy.EXPIRE_FIRST,
                            ProductType.COMPUTE,
                            ChargeType.ABSOLUTE,
                            ProductPriceUnit.CREDITS_PER_MINUTE
                        )
                    ), null),
                    user
                )

                comment("""
                    📝 Note: at this point the user has a very low amount of credits remaining.
                    It will only last a couple of minutes.
                """.trimIndent())

                val jobId = "62348"
                val jobSpecification = JobSpecification(
                    NameAndVersion("acme-web-application", "1.0.0"),
                    ComputeProductReference("example-compute", "example-compute", "example")
                )

                success(create, bulkRequestOf(jobSpecification), BulkResponse(listOf(FindByStringId(jobId))), user)

                comment("The Job is now running")
                comment("However, a few minutes later the Job is automatically killed by UCloud. " +
                        "The status now reflects this.")

                success(
                    retrieve,
                    ResourceRetrieveRequest(JobIncludeFlags(), jobId),
                    Job(
                        jobId,
                        ResourceOwner("user", null),
                        listOf(
                            JobUpdate(
                                JobState.IN_QUEUE,
                                status = "Your job is now waiting in the queue!",
                                timestamp = start + (1000L * 60 * 60 * 24 * 3)
                            ),
                            JobUpdate(
                                JobState.RUNNING,
                                status = "Your job is now running!",
                                timestamp = start + (1000L * 60 * 60 * 24 * 3) + 5000
                            ),
                            JobUpdate(
                                JobState.SUCCESS,
                                status = "Your job has been terminated (No more credits)",
                                timestamp = start + (1000L * 60 * 60 * 24 * 3) + 120_000 + 5000
                            )
                        ),
                        jobSpecification,
                        JobStatus(JobState.SUCCESS),
                        start + (1000L * 60 * 60 * 24 * 3)
                    ),
                    user
                )
            }
        )

        useCase(
            extendAndCancelUseCase,
            "Extending a Job and terminating it early",
            preConditions = listOf(
                "The provider must support the extension API"
            ),
            flow = {
                val user = basicUser()

                comment("""
                    In this example we will show how a user can extend the duration of a Job. Later in the same
                    example, we show how the user can cancel it early.
                """.trimIndent())

                val start = 1633329776235L
                val jobId = "62348"
                val jobSpecification = JobSpecification(
                    NameAndVersion("acme-web-application", "1.0.0"),
                    ComputeProductReference("example-compute", "example-compute", "example"),
                    timeAllocation = SimpleDuration(5, 0, 0)
                )

                success(create, bulkRequestOf(jobSpecification), BulkResponse(listOf(FindByStringId(jobId))), user)

                comment("The Job is initially allocated with a duration of 5 hours. We can check when it expires by " +
                        "retrieving the Job")

                success(
                    retrieve,
                    ResourceRetrieveRequest(JobIncludeFlags(), jobId),
                    Job(
                        jobId,
                        ResourceOwner("user", null),
                        listOf(
                            JobUpdate(
                                JobState.IN_QUEUE,
                                status = "Your job is now waiting in the queue!",
                                timestamp = start
                            ),
                            JobUpdate(
                                JobState.RUNNING,
                                status = "Your job is now running!",
                                timestamp = start + 5000
                            )
                        ),
                        jobSpecification,
                        JobStatus(
                            JobState.RUNNING,
                            expiresAt = start + (1000L * 3600 * 5)
                        ),
                        start
                    ),
                    user
                )

                comment("We can extend the duration quite easily")

                success(
                    extend,
                    bulkRequestOf(
                        JobsExtendRequestItem(jobId, SimpleDuration(1, 0, 0))
                    ),
                    JobsExtendResponse(listOf(Unit)),
                    user
                )

                comment("The new expiration is reflected if we retrieve it again")

                success(
                    retrieve,
                    ResourceRetrieveRequest(JobIncludeFlags(), jobId),
                    Job(
                        jobId,
                        ResourceOwner("user", null),
                        listOf(
                            JobUpdate(
                                JobState.IN_QUEUE,
                                status = "Your job is now waiting in the queue!",
                                timestamp = start
                            ),
                            JobUpdate(
                                JobState.RUNNING,
                                status = "Your job is now running!",
                                timestamp = start + 5000
                            )
                        ),
                        jobSpecification,
                        JobStatus(
                            JobState.RUNNING,
                            expiresAt = start + (1000L * 3600 * 6)
                        ),
                        start
                    ),
                    user
                )

                comment("If the user decides that they are done with the Job early, then they can simply terminate it")

                success(
                    terminate,
                    bulkRequestOf(FindByStringId(jobId)),
                    BulkResponse(listOf(Unit)),
                    user
                )

                comment("This termination is reflected in the status (and updates)")

                success(
                    retrieve,
                    ResourceRetrieveRequest(JobIncludeFlags(), jobId),
                    Job(
                        jobId,
                        ResourceOwner("user", null),
                        listOf(
                            JobUpdate(
                                JobState.IN_QUEUE,
                                status = "Your job is now waiting in the queue!",
                                timestamp = start
                            ),
                            JobUpdate(
                                JobState.RUNNING,
                                status = "Your job is now running!",
                                timestamp = start + 5000
                            ),
                            JobUpdate(
                                JobState.SUCCESS,
                                status = "Your job has been cancelled!",
                                timestamp = start + 5000 + (1000L * 60 * 120)
                            )
                        ),
                        jobSpecification,
                        JobStatus(
                            JobState.SUCCESS,
                        ),
                        start
                    ),
                    user
                )
            }
        )

        document(
            browse, UCloudApiDocC(
                """
                Browses the catalog of all Jobs
                
                The catalog of all $TYPE_REF Job s works through the normal pagination and the return value can be
                adjusted through the [flags]($TYPE_REF_LINK JobIncludeFlags). This can include filtering by a specific
                application or looking at $TYPE_REF Job s of a specific state, such as
                (`RUNNING`)[$TYPE_REF_LINK JobState).
            """.trimIndent()
            )
        )
        document(retrieve, UCloudApiDocC("Retrieves a single Job"))
    }

    override val create get() = super.create!!
    override val delete: Nothing? = null
    override val search get() = super.search!!

    val terminate = call("terminate", BulkRequest.serializer(FindByStringId.serializer()), BulkResponse.serializer(Unit.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "terminate")

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

    @UCloudApiExperimental(ExperimentalLevel.BETA)
    val retrieveUtilization = call("retrieveUtilization", JobsRetrieveUtilizationRequest.serializer(), JobsRetrieveUtilizationResponse.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, "utilization")

        documentation {
            summary = "Retrieve information about how busy the provider's cluster currently is"
            description = """
                This endpoint will return information about how busy a cluster is. This endpoint is only used for
                informational purposes. UCloud does not use this information for any accounting purposes.
            """.trimIndent()
        }
    }

    val follow = call("follow", JobsFollowRequest.serializer(), JobsFollowResponse.serializer(), CommonErrorMessage.serializer()) {
        auth { access = AccessRight.READ }
        websocket(baseContext)

        documentation {
            summary = "Follow the progress of a job"
            description = """
                Opens a WebSocket subscription to receive updates about a job. These updates include:
                
                - Messages from the provider. For example an update describing state changes or future maintenance.
                - State changes from UCloud. For example transition from [`IN_QUEUE`]($TYPE_REF_LINK JobState) to
                  [`RUNNING`]($TYPE_REF_LINK JobState).
                - If supported by the provider, `stdout` and `stderr` from the $TYPE_REF Job
                
            """.trimIndent()
        }
    }

    val extend = call("extend", BulkRequest.serializer(JobsExtendRequestItem.serializer()), BulkResponse.serializer(Unit.serializer().nullable), CommonErrorMessage.serializer()) {
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

    val suspend = call("suspend", BulkRequest.serializer(JobsSuspendRequestItem.serializer()), BulkResponse.serializer(Unit.serializer().nullable), CommonErrorMessage.serializer()) {
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

    val unsuspend = call("unsuspend", BulkRequest.serializer(JobsUnsuspendRequestItem.serializer()), BulkResponse.serializer(Unit.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "unsuspend")

        documentation {
            summary = "Unsuspends a job"
            description = """
                Reverses the effects of suspending a job. The job is expected to return back to an `IN_QUEUE` or
                `RUNNING` state.
            """.trimIndent()
        }
    }


    val openInteractiveSession = call("openInteractiveSession", BulkRequest.serializer(JobsOpenInteractiveSessionRequestItem.serializer()), BulkResponse.serializer(OpenSessionWithProvider.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "interactiveSession")
        documentation {
            summary = "Opens an interactive session (e.g. terminal, web or VNC)"
        }
    }
}
