package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.Maintenance
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.accounting.api.providers.ResourceChargeCreditsResponse
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.exampleBatchApplication
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

typealias JobsProviderExtendRequest = BulkRequest<JobsProviderExtendRequestItem>
typealias JobsProviderExtendResponse = BulkResponse<Unit?>

@Serializable
@UCloudApiDoc("A request to extend the timeAllocation of a Job")
@UCloudApiStable
data class JobsProviderExtendRequestItem(
    @UCloudApiDoc("The affected Job")
    val job: Job,
    @UCloudApiDoc("The requested extension, it will be added to the current timeAllocation")
    val requestedTime: SimpleDuration,
)

@Serializable
@UCloudApiStable
data class JobsProviderSuspendRequestItem(
    val job: Job,
) 
typealias JobsProviderSuspendRequest = BulkRequest<JobsProviderSuspendRequestItem>
typealias JobsProviderSuspendResponse = BulkResponse<Unit?>

@UCloudApiStable
typealias JobsProviderUnsuspendRequestItem = JobsProviderSuspendRequestItem
typealias JobsProviderUnsuspendRequest = BulkRequest<JobsProviderUnsuspendRequestItem>
typealias JobsProviderUnsuspendResponse = BulkResponse<Unit?>

@Serializable
@UCloudApiDoc("A request to start/stop a follow session")
@UCloudApiStable
sealed class JobsProviderFollowRequest {
    @Serializable
    @SerialName("init")
    @UCloudApiDoc("Start a new follow session for a given Job")
    @UCloudApiStable
    data class Init(val job: Job) : JobsProviderFollowRequest()

    @Serializable
    @SerialName("cancel")
    @UCloudApiDoc("Stop an existing follow session for a given Job")
    @UCloudApiStable
    data class CancelStream(val streamId: String) : JobsProviderFollowRequest()
}

@Serializable
@UCloudApiDoc("A message emitted by the Provider in a follow session")
@UCloudApiStable
data class JobsProviderFollowResponse(
    @UCloudApiDoc(
        """
        A unique ID for this follow session, the same identifier should be used for the entire session
        
        We recommend that Providers generate a UUID or similar for this ID.
    """
    )
    val streamId: String,

    @UCloudApiDoc(
        """
        The rank of the node (0-indexed)
        
        Valid values range from 0 (inclusive) until [`specification.replicas`]($TYPE_REF_LINK Job) (exclusive)
    """
    )
    val rank: Int,

    @UCloudApiDoc(
        """
        New messages from stdout (if any)
        
        The bytes from stdout, of the running process, should be interpreted as UTF-8. If the stream contains invalid
        bytes then these should be ignored and skipped.
        
        See https://linux.die.net/man/3/stdout for more information.
    """
    )
    val stdout: String? = null,

    @UCloudApiDoc(
        """
        New messages from stderr (if any)
        
        The bytes from stdout, of the running process, should be interpreted as UTF-8. If the stream contains invalid
        bytes then these should be ignored and skipped.
        
        See https://linux.die.net/man/3/stderr for more information.
    """
    )
    val stderr: String? = null,
)

typealias JobsProviderOpenInteractiveSessionRequest = BulkRequest<JobsProviderOpenInteractiveSessionRequestItem>

@Serializable
@UCloudApiDoc("A request for opening a new interactive session (e.g. terminal)")
@UCloudApiStable
data class JobsProviderOpenInteractiveSessionRequestItem(
    @UCloudApiDoc("The fully resolved Job")
    val job: Job,
    @UCloudApiDoc(
        """
        The rank of the node (0-indexed)
        
        Valid values range from 0 (inclusive) until [`specification.replicas`]($TYPE_REF_LINK Job) (exclusive)
    """
    )
    val rank: Int,
    @UCloudApiDoc("The type of session")
    val sessionType: InteractiveSessionType,
)

typealias JobsProviderOpenInteractiveSessionResponse = BulkResponse<OpenSession?>

@Serializable
@UCloudApiStable
data class JobsProviderUtilizationRequest(
    val categoryId: String
)

typealias JobsProviderUtilizationResponse = JobsRetrieveUtilizationResponse

@Serializable
@UCloudApiExperimental(ExperimentalLevel.BETA)
data class CpuAndMemory(
    @UCloudApiDoc(
        """
            Number of virtual cores
            
            Implement as a floating to represent fractions of a virtual core. This is for example useful for Kubernetes
            (and other container systems) which will allocate milli-cpus. 
        """
    )
    val cpu: Double,
    @UCloudApiDoc(
        """
            Memory available in bytes
        """
    )
    val memory: Long,
)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.BETA)
data class QueueStatus(
    @UCloudApiDoc("The number of jobs running in the system")
    val running: Int,
    @UCloudApiDoc("The number of jobs waiting in the queue")
    val pending: Int,
)

interface FeatureBlock {
    var enabled: Boolean?
}

fun <Block : FeatureBlock> Block.checkEnabled() {
    if (enabled != true) error("Missing feature")
}

fun <Block : FeatureBlock> Block.checkFeature(flag: Boolean?) {
    if (enabled != true || flag != true) {
        error("Missing feature")
    }
}

@Serializable
@UCloudApiStable
data class ComputeSupport(
    override val product: ProductReference,

    @UCloudApiDoc("Support for `Tool`s using the `DOCKER` backend")
    val docker: Docker = Docker(),

    @UCloudApiDoc("Support for `Tool`s using the `VIRTUAL_MACHINE` backend")
    val virtualMachine: VirtualMachine = VirtualMachine(),

    @UCloudApiDoc("Support for `Tool`s using the `NATIVE` backend")
    val native: Native = Native(),

    override var maintenance: Maintenance? = null,
) : ProductSupport {
    interface UniversalBackendSupport : FeatureBlock {
        override var enabled: Boolean?
        var vnc: Boolean?
        var logs: Boolean?
        var terminal: Boolean?
        var timeExtension: Boolean?
        var utilization: Boolean?
    }

    interface WithPeers {
        var peers: Boolean?
    }

    interface WithWeb {
        var web: Boolean?
    }

    interface WithSuspension {
        var suspension: Boolean?
    }

    @Serializable
    @UCloudApiStable
    data class Docker(
        @UCloudApiDoc("Flag to enable/disable this feature\n\nAll other flags are ignored if this is `false`.")
        override var enabled: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the interactive interface of `WEB` `Application`s")
        override var web: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the interactive interface of `VNC` `Application`s")
        override var vnc: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the log API")
        override var logs: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the interactive terminal API")
        override var terminal: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable connection between peering `Job`s")
        override var peers: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable extension of jobs")
        override var timeExtension: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the retrieveUtilization of jobs")
        override var utilization: Boolean? = null,
    ) : UniversalBackendSupport, WithPeers, WithWeb

    @Serializable
    @UCloudApiStable
    data class VirtualMachine(
        @UCloudApiDoc("Flag to enable/disable this feature\n\nAll other flags are ignored if this is `false`.")
        override var enabled: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the log API")
        override var logs: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the VNC API")
        override var vnc: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the interactive terminal API")
        override var terminal: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable extension of jobs")
        override var timeExtension: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable suspension of jobs")
        override var suspension: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the retrieveUtilization of jobs")
        override var utilization: Boolean? = null,
    ) : UniversalBackendSupport, WithSuspension

    @Serializable
    @UCloudApiStable
    data class Native(
        @UCloudApiDoc("Flag to enable/disable this feature\n\nAll other flags are ignored if this is `false`.")
        override var enabled: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the log API")
        override var logs: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the VNC API")
        override var vnc: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the interactive terminal API")
        override var terminal: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable extension of jobs")
        override var timeExtension: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the retrieveUtilization of jobs")
        override var utilization: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the interactive interface of `WEB` `Application`s")
        override var web: Boolean? = null,
    ) : UniversalBackendSupport, WithWeb
}

@UCloudApiStable
open class JobsProvider(provider: String) : ResourceProviderApi<Job, JobSpecification, JobUpdate, JobIncludeFlags,
        JobStatus, Product.Compute, ComputeSupport>("jobs", provider) {
    override fun toString() = "JobsProvider($baseContext)"

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

    init {
        title = "Provider API: Compute"

        description = """
            The ingoing provider API for Jobs

            $TYPE_REF Job s in UCloud are the core abstraction used to describe units of computation.
            
            This document describes the API which providers receive to implement Jobs. We recommend that you read the
            documentation for the end-user API first. Most of this API is a natural extension of the end-user APIs. 
            Almost all RPCs in this API have a direct match in the end-user API. Most endpoints in the provider API
            receives a Job along with extra call details. This is the main difference from the end-user API. In the
            end-user API the request is mainly a reference or a specification plus call details.
            
            It is not required that you, as a provider, implement all calls. However, you must implement all the calls
            which you support. This level of support is controlled by your response to the $CALL_REF retrieveProducts
            call (See below for an example).
            
            ---

            __📝 Provider Note:__ This is the API exposed to providers. See the table below for other relevant APIs.

            | End-User | Provider (Ingoing) | Control (Outgoing) |
            |----------|--------------------|--------------------|
            | [`Jobs`](/docs/developer-guide/orchestration/compute/jobs.md) | [`JobsProvider`](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md) | [`JobsControl`](/docs/developer-guide/orchestration/compute/providers/jobs/outgoing.md) |
            
            ---

            ## Multi-replica Jobs (Container backend)
            
            
            A `Job` can be scheduled on more than one replica. The orchestrator requires that backends execute the exact same
            command on all the nodes. Information about other nodes will be mounted at `/etc/ucloud`. This information allows jobs
            to configure themselves accordingly.

            Each node is given a rank. The rank is 0-indexed. By convention index 0 is used as a primary point of contact.

            The table below summarizes the files mounted at `/etc/ucloud` and their contents:

            | **Name**              | **Description**                                           |
            |-----------------------|-----------------------------------------------------------|
            | `node-${"$"}rank.txt`      | Single line containing hostname/ip address of the 'node'. |
            | `rank.txt`            | Single line containing the rank of this node.             |
            | `cores.txt`           | Single line containing the amount of cores allocated.     |
            | `number_of_nodes.txt` | Single line containing the number of nodes allocated.     |
            | `job_id.txt`          | Single line containing the id of this job.                |
            
            ---

            __📝 NOTE:__ We expect that the mount location will become more flexible in a future release. See
            issue [#2124](https://github.com/SDU-eScience/UCloud/issues/2124).

            ---

            ## Networking and Peering with Other Applications

            `Job`s are, by default, only allowed to perform networking with other nodes in the same `Job`. A user can override this
            by requesting, at `Job` startup, networking with an existing job. This will configure the firewall accordingly and allow
            networking between the two `Job`s. This will also automatically provide user-friendly hostnames for the `Job`.

            ## The `/work`ing directory (Container backend)
            
            UCloud assumes that the `/work` directory is available for data which needs to be persisted. It is expected
            that files left directly in this directory is placed in the `output` folder of the `Job`. 

            ## Ephemeral Resources

            Every `Job` has some resources which exist only as long as the `Job` is `RUNNING`. These types of resources are said to
            be ephemeral resources. Examples of this includes temporary working storage included as part of the `Job`. Such
            storage is _not_ guaranteed to be persisted across `Job` runs and `Application`s should not rely on this behavior.
            
            ## Job Scheduler

            The job scheduler is responsible for running `Job`s on behalf of users. The provider can tweak which features the
            scheduler is able to support using the provider manifest.

            UCloud puts no strict requirements on how the job scheduler runs job and leaves this to the provider. For example, this
            means that there are no strict requirements on how jobs are queued. Jobs can be run in any order which the provider sees
            fit.

        """.trimIndent()

        serializerLookupTable = mapOf(
            serializerEntry(OpenSession.serializer()),
            serializerEntry(JobsProviderFollowRequest.serializer()),
            serializerEntry(ShellRequest.serializer()),
            serializerEntry(ShellResponse.serializer())
        )
    }

    private val supportUseCase = "support"
    private val minimalSupportUseCase = "minimalSupport"
    private val createSimpleUseCase = "createSimple"
    private val accountingUseCase = "accounting"
    private val verificationUseCase = "verify"

    @OptIn(UCloudApiExampleValue::class)
    override fun documentation() {
        useCase(
            supportUseCase,
            "Declaring support full support for containerized applications",
            flow = {
                val ucloud = ucloudCore()

                comment(
                    """
                    In this example we will show how you, as a provider, can declare full support for containerized
                    applications. This example assumes that you have already registered two compute products with
                    UCloud/Core.
                """.trimIndent()
                )

                comment(
                    """
                    The retrieveProducts call will be invoked by the UCloud/Core service account. UCloud will generally
                    cache this response for a period of time before re-querying for information. As a result, changes
                    in your response might not be immediately visible in UCloud.
                """.trimIndent()
                )

                val support = ComputeSupport.Docker(
                    enabled = true,
                    web = true,
                    vnc = true,
                    logs = true,
                    terminal = true,
                    peers = true,
                    timeExtension = true,
                    utilization = true
                )

                success(
                    retrieveProducts,
                    Unit,
                    BulkResponse(
                        listOf(
                            ComputeSupport(
                                ProductReference("example-compute-1", "example-compute", "example"),
                                support
                            ),
                            ComputeSupport(
                                ProductReference("example-compute-2", "example-compute", "example"),
                                support
                            )
                        )
                    ),
                    ucloud
                )

                comment("📝 Note: The support information must be repeated for every Product you support.")
                comment("📝 Note: The Products mentioned in this response must already be registered with UCloud.")
            }
        )

        val computeProduct = Product.Compute(
            "example-compute-1",
            1_000_000L,
            ProductCategoryId("example-compute", "example"),
            "An example machine",
            cpu = 1,
            memoryInGigs = 2,
            gpu = 0
        )

        val support = ComputeSupport(
            ProductReference(
                computeProduct.name,
                computeProduct.category.name,
                computeProduct.category.provider
            ),
            ComputeSupport.Docker(enabled = true)
        )

        val jobId = "54112"
        val sampleJob = Job(
            jobId,
            ResourceOwner("user", null),
            emptyList(),
            JobSpecification(
                NameAndVersion("acme-batch", "1.0.0"),
                ComputeProductReference("example-compute-1", "example-compute", "example"),
                parameters = mapOf(
                    "debug" to AppParameterValue.Bool(true),
                    "value" to AppParameterValue.Text("Hello, World!")
                ),
                timeAllocation = SimpleDuration(1, 0, 0)
            ),
            JobStatus(
                JobState.IN_QUEUE,
                resolvedApplication = exampleBatchApplication,
                resolvedSupport = ResolvedSupport(
                    computeProduct,
                    support
                ),
                resolvedProduct = computeProduct
            ),
            1633329776235L,
        )

        useCase(
            minimalSupportUseCase,
            "Declaring minimal support for virtual machines",
            flow = {
                val ucloud = ucloudCore()

                comment(
                    """
                    In this example we will show how you, as a provider, can declare minimal support for virtual
                    machines. This example assumes that you have already registered two compute products with
                    UCloud/Core.
                """.trimIndent()
                )

                comment(
                    """
                    The retrieveProducts call will be invoked by the UCloud/Core service account. UCloud will generally
                    cache this response for a period of time before re-querying for information. As a result, changes
                    in your response might not be immediately visible in UCloud.
                """.trimIndent()
                )

                val support = ComputeSupport.VirtualMachine(
                    enabled = true,
                )

                success(
                    retrieveProducts,
                    Unit,
                    BulkResponse(
                        listOf(
                            ComputeSupport(
                                ProductReference("example-compute-1", "example-compute", "example"),
                                virtualMachine = support
                            ),
                            ComputeSupport(
                                ProductReference("example-compute-2", "example-compute", "example"),
                                virtualMachine = support
                            )
                        )
                    ),
                    ucloud
                )

                comment("📝 Note: If a support feature is not explicitly mentioned, then no support is assumed.")
                comment("📝 Note: The support information must be repeated for every Product you support.")
                comment("📝 Note: The Products mentioned in this response must already be registered with UCloud.")
            }
        )

        useCase(
            createSimpleUseCase,
            "Simple batch Job with life-cycle events",
            preConditions = listOf(
                "You should understand Products, Applications and Jobs before reading this",
                "The provider must support containerized Applications",
                "The provider must implement the retrieveProducts call"
            ),
            flow = {
                val ucloud = ucloudCore()
                val provider = provider()

                comment(
                    """
                    In this example we will show the creation of a simple batch Job. The procedure starts with the
                    Provider receives a create request from UCloud/Core
                """.trimIndent()
                )


                comment(
                    """
                    The request below contains a lot of information. We recommend that you read about and understand
                    Products, Applications and Jobs before you continue. We will attempt to summarize the information
                    below:
                    
                    - The request contains one or more Jobs. The Provider should schedule each of them on their
                      infrastructure.
                    - The `id` of a Job is unique, globally, in UCloud.
                    - The `owner` references the UCloud identity and workspace of the creator
                    - The `specification` contains the user's request
                    - The `status` contains UCloud's view of the Job _AND_ resolved resources required for the Job
                    
                    In this example:
                    
                    - Exactly one Job will be created. 
                      - `items` contains only one Job
                      
                    - This Job will run a `BATCH` application
                      - See `status.resolvedApplication.invocation.applicationType`
                      
                    - It will run on the `example-compute-1` machine-type
                      - See `specification.product` and `status.resolvedProduct`
                      
                    - The application should launch the `acme/batch:1.0.0` container
                      - `status.resolvedApplication.invocation.tool.tool.description.backend`
                      - `status.resolvedApplication.invocation.tool.tool.description.image`
                      
                    - It will be invoked with `acme-batch --debug "Hello, World!"`. 
                      - The invocation is created from `status.resolvedApplication.invocation.invocation`
                      - With parameters defined in `status.resolvedApplication.invocation.parameters`
                      - And values defined in `specification.parameters`
                      
                    - The Job should be scheduled with a max wall-time of 1 hour 
                      - See `specification.timeAllocation`
                      
                    - ...on exactly 1 node.
                      - See `specification.replicas`
                """.trimIndent()
                )

                success(
                    create,
                    bulkRequestOf(sampleJob),
                    BulkResponse(listOf(null)),
                    ucloud
                )

                comment(
                    """
                    📝 Note: The response in this case indicates that the Provider chose not to generate an internal ID
                    for this Job. If an ID was provided, then on subsequent requests the `providerGeneratedId` of this
                    Job would be set accordingly. This feature can help providers keep track of their internal state
                    without having to actively maintain a mapping.
                """.trimIndent()
                )

                comment(
                    """
                    The Provider will use this information to schedule the Job on their infrastructure. Through
                    background processing, the Provider will keep track of this Job. The Provider notifies UCloud of
                    state changes as they occur. This happens through the outgoing Control API.
                """.trimIndent()
                )

                success(
                    JobsControl.update,
                    bulkRequestOf(
                        ResourceUpdateAndId(
                            jobId, JobUpdate(
                                JobState.RUNNING,
                                status = "The job is now running!"
                            )
                        )
                    ),
                    Unit,
                    provider
                )

                comment("📝 Note: The timestamp field will be filled out by UCloud/Core")

                comment("~ Some time later ~")

                success(
                    JobsControl.update,
                    bulkRequestOf(
                        ResourceUpdateAndId(
                            jobId, JobUpdate(
                                JobState.SUCCESS,
                                status = "The job has finished processing!"
                            )
                        )
                    ),
                    Unit,
                    provider
                )
            }
        )

        useCase(
            accountingUseCase,
            "Accounting",
            preConditions = listOf(
                "One or more active Jobs running at the Provider"
            ),
            flow = {
                val ucloud = ucloudCore()
                val provider = provider()

                comment(
                    """
                    In this example, we show how a Provider can implement accounting. Accounting is done, periodically,
                    by the provider in a background process. We recommend that Providers combine this with the same
                    background processing required for state changes.
                """.trimIndent()
                )

                comment(
                    """
                    You should read understand how Products work in UCloud. UCloud supports multiple ways of accounting
                    for usage. The most normal one, which we show here, is the `CREDITS_PER_MINUTE` policy. This policy
                    requires that a Provider charges credits (1 credit = 1/1_000_000 DKK) for every minute of usage.
                """.trimIndent()
                )

                comment(
                    """
                    We assume that the Provider has just determined that Jobs "51231" (single replica) and "63489"
                    (23 replicas) each have used 15 minutes of compute time since last accounting iteration.
                """.trimIndent()
                )

                success(
                    JobsControl.chargeCredits,
                    bulkRequestOf(
                        ResourceChargeCredits(
                            "51231",
                            "51231-charge-04-oct-2021-12:30",
                            15,
                        ),
                        ResourceChargeCredits(
                            "63489",
                            "63489-charge-04-oct-2021-12:30",
                            15,
                            periods = 23
                        )
                    ),
                    ResourceChargeCreditsResponse(
                        emptyList(),
                        emptyList()
                    ),
                    provider
                )

                comment(
                    """
                    📝 Note: Because the ProductPriceUnit, of the Product associated with the Job, is
                    `CREDITS_PER_MINUTE` each unit corresponds to minutes of usage. A different ProductPriceUnit, for
                    example `CREDITS_PER_HOUR` would alter the definition of this unit.
                """.trimIndent()
                )

                comment(
                    """
                    📝 Note: The chargeId is an identifier which must be unique for any charge made by the Provider.
                    If the Provider makes a different charge request with this ID then the request will be ignored. We
                    recommend that Providers use this to their advantage and include, for example, a timestamp from
                    the last iteration. This means that you, as a Provider, cannot accidentally charge twice for the
                    same usage.
                """.trimIndent()
                )

                comment(
                    "In the next iteration, the Provider also determines that 15 minutes has passed for these " +
                            "Jobs."
                )

                success(
                    JobsControl.chargeCredits,
                    bulkRequestOf(
                        ResourceChargeCredits(
                            "51231",
                            "51231-charge-04-oct-2021-12:45",
                            15,
                        ),
                        ResourceChargeCredits(
                            "63489",
                            "63489-charge-04-oct-2021-12:45",
                            15,
                            periods = 23
                        )
                    ),
                    ResourceChargeCreditsResponse(
                        listOf(FindByStringId("63489")),
                        emptyList()
                    ),
                    provider
                )

                comment(
                    """
                    However, this time UCloud has told us that 63489 no longer has enough credits to pay for this.
                    The Provider should respond to this by immediately cancelling the Job, UCloud/Core does not perform
                    this step for you!
                """.trimIndent()
                )

                comment("📝 Note: This request should be triggered by the normal life-cycle handler.")

                success(
                    JobsControl.update,
                    bulkRequestOf(
                        ResourceUpdateAndId(
                            "63489",
                            JobUpdate(
                                JobState.SUCCESS,
                                status = "The job was terminated (No credits)"
                            )
                        )
                    ),
                    Unit,
                    provider
                )
            }
        )

        useCase(
            verificationUseCase,
            "Ensuring UCloud/Core and Provider are in-sync",
            preConditions = listOf(
                "One or more active Jobs for this Provider"
            ),
            flow = {
                val ucloud = ucloudCore()
                val provider = provider()

                comment(
                    """
                    In this example, we will explore the mechanism that UCloud/Core uses to ensure that the Provider
                    is synchronized with the core.
                """.trimIndent()
                )

                comment(
                    """
                    UCloud/Core will periodically send the Provider a batch of active Jobs. If the Provider is unable
                    to recognize one or more of these Jobs, it should respond by updating the state of the affected
                    Job(s).
                """.trimIndent()
                )

                success(
                    verify,
                    bulkRequestOf(sampleJob.copy(status = JobStatus(JobState.RUNNING))),
                    Unit,
                    ucloud
                )

                comment("In this case, the Provider does not recognize ${sampleJob.id}")

                success(
                    JobsControl.update,
                    bulkRequestOf(
                        ResourceUpdateAndId(
                            sampleJob.id,
                            JobUpdate(
                                JobState.FAILURE,
                                status = "Your job is no longer available"
                            )
                        )
                    ),
                    Unit,
                    provider
                )
            }
        )

        documentProviderCall(
            create, Jobs.create,
            ProviderApiRequirements.List(
                listOf(
                    "[`docker.enabled = true`]($TYPE_REF_LINK ComputeSupport.Docker) or",
                    "[`virtualMachine.enabled = true`]($TYPE_REF_LINK ComputeSupport.VirtualMachine)",
                )
            ),

            )
    }

    val extend = call("extend", BulkRequest.serializer(JobsProviderExtendRequestItem.serializer()), BulkResponse.serializer(Unit.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "extend", roles = Roles.PRIVILEGED)

        documentation {
            providerDescription(
                Jobs.extend,
                ProviderApiRequirements.List(
                    listOf(
                        "[`docker.timeExtension = true`]($TYPE_REF_LINK ComputeSupport.Docker) or ",
                        "[`virtualMachine.timeExtension = true`]($TYPE_REF_LINK ComputeSupport.VirtualMachine)"
                    )
                )
            )
        }
    }

    val terminate = call("terminate", BulkRequest.serializer(Job.serializer()), BulkResponse.serializer(Unit.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "terminate", roles = Roles.PRIVILEGED)

        documentation {
            providerDescription(
                Jobs.terminate,
                ProviderApiRequirements.Mandatory
            )
        }
    }

    val suspend = call("suspend", BulkRequest.serializer(JobsProviderSuspendRequestItem.serializer()), BulkResponse.serializer(Unit.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "suspend", roles = Roles.PRIVILEGED)

        documentation {
            providerDescription(
                Jobs.suspend,
                ProviderApiRequirements.List(
                    listOf(
                        "[`virtualMachine.suspension = true`]($TYPE_REF_LINK ComputeSupport.VirtualMachine)"
                    )
                )
            )
        }
    }

    val unsuspend = call("unsuspend", BulkRequest.serializer(JobsProviderUnsuspendRequestItem.serializer()), BulkResponse.serializer(Unit.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "unsuspend", roles = Roles.PRIVILEGED)

        documentation {
            providerDescription(
                Jobs.unsuspend,
                ProviderApiRequirements.List(
                    listOf(
                        "[`virtualMachine.suspension = true`]($TYPE_REF_LINK ComputeSupport.VirtualMachine)"
                    )
                )
            )
        }
    }

    val follow = call(
        name = "follow",
        handler = {
            auth {
                access = AccessRight.READ
                roles = Roles.PRIVILEGED
            }

            documentation {
                providerDescription(
                    Jobs.follow,
                    ProviderApiRequirements.List(
                        listOf(
                            "[`docker.logs = true`]($TYPE_REF_LINK ComputeSupport.Docker) or",
                            "[`virtualMachine.logs = true`]($TYPE_REF_LINK ComputeSupport.VirtualMachine)"
                        )
                    )
                )
            }

            websocket("/ucloud/$namespace/websocket")
        },
        JobsProviderFollowRequest.serializer(),
        JobsProviderFollowResponse.serializer(),
        CommonErrorMessage.serializer(),
        typeOfIfPossible<JobsProviderFollowRequest>(),
        typeOfIfPossible<JobsProviderFollowResponse>(),
        typeOfIfPossible<CommonErrorMessage>(),
    )

    val openInteractiveSession = call(
        name = "openInteractiveSession",
        handler = {
            httpUpdate(baseContext, "interactiveSession", roles = Roles.PRIVILEGED)

            documentation {
                providerDescription(
                    Jobs.openInteractiveSession,
                    ProviderApiRequirements.List(
                        listOf(
                            "[`docker.vnc = true`]($TYPE_REF_LINK ComputeSupport.Docker) or",
                            "[`docker.terminal = true`]($TYPE_REF_LINK ComputeSupport.Docker) or",
                            "[`docker.web = true`]($TYPE_REF_LINK ComputeSupport.Docker) or",
                            "[`virtualMachine.vnc = true`]($TYPE_REF_LINK ComputeSupport.VirtualMachine) or",
                            "[`virtualMachine.terminal = true`]($TYPE_REF_LINK ComputeSupport.VirtualMachine)",
                        )
                    )
                )
            }
        },
        BulkRequest.serializer(JobsProviderOpenInteractiveSessionRequestItem.serializer()),
        BulkResponse.serializer(OpenSession.serializer().nullable),
        CommonErrorMessage.serializer(),
        typeOfIfPossible<JobsProviderOpenInteractiveSessionRequest>(),
        typeOfIfPossible<JobsProviderOpenInteractiveSessionResponse>(),
        typeOfIfPossible<CommonErrorMessage>(),
    )

    @UCloudApiExperimental(ExperimentalLevel.BETA)
    val retrieveUtilization = call("retrieveUtilization", JobsProviderUtilizationRequest.serializer(), JobsProviderUtilizationResponse.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, "utilization", roles = Roles.PRIVILEGED)

        documentation {
            providerDescription(
                Jobs.retrieveUtilization,
                ProviderApiRequirements.List(
                    listOf(
                        "[`docker.utilization = true`]($TYPE_REF_LINK ComputeSupport.Docker) or",
                        "[`virtualMachine.utilization = true`]($TYPE_REF_LINK ComputeSupport.VirtualMachine)",
                    )
                )
            )
        }
    }
}
