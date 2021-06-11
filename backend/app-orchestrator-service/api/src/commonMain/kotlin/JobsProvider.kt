package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.UCloudApiExperimental
import dk.sdu.cloud.calls.title
import dk.sdu.cloud.calls.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KProperty

typealias JobsProviderExtendRequest = BulkRequest<JobsProviderExtendRequestItem>
typealias JobsProviderExtendResponse = BulkResponse<Unit?>

@Serializable
data class JobsProviderExtendRequestItem(
    val job: Job,
    val requestedTime: SimpleDuration,
)

typealias JobsProviderSuspendRequest = BulkRequest<JobsProviderSuspendRequestItem>
typealias JobsProviderSuspendResponse = Unit
typealias JobsProviderSuspendRequestItem = Job

@Serializable
sealed class JobsProviderFollowRequest {
    @Serializable
    @SerialName("init")
    data class Init(val job: Job) : JobsProviderFollowRequest()

    @Serializable
    @SerialName("cancel")
    data class CancelStream(val streamId: String) : JobsProviderFollowRequest()
}

@Serializable
data class JobsProviderFollowResponse(
    val streamId: String,
    val rank: Int,
    val stdout: String? = null,
    val stderr: String? = null,
)

typealias JobsProviderOpenInteractiveSessionRequest = BulkRequest<JobsProviderOpenInteractiveSessionRequestItem>

@Serializable
data class JobsProviderOpenInteractiveSessionRequestItem(
    val job: Job,
    val rank: Int,
    val sessionType: InteractiveSessionType,
)

typealias JobsProviderOpenInteractiveSessionResponse = BulkResponse<OpenSession?>

typealias JobsProviderUtilizationRequest = Unit

typealias JobsProviderUtilizationResponse = JobsRetrieveUtilizationResponse

@Serializable
data class CpuAndMemory(
    val cpu: Double,
    val memory: Long,
)

@Serializable
data class QueueStatus(
    val running: Int,
    val pending: Int,
)

@Serializable
data class ComputeSupport(
    override val product: ProductReference,

    @UCloudApiDoc("Support for `Tool`s using the `DOCKER` backend")
    val docker: Docker = Docker(),

    @UCloudApiDoc("Support for `Tool`s using the `VIRTUAL_MACHINE` backend")
    val virtualMachine: VirtualMachine = VirtualMachine(),
) : ProductSupport {
    @Serializable
    data class Docker(
        @UCloudApiDoc("Flag to enable/disable this feature\n\nAll other flags are ignored if this is `false`.")
        var enabled: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the interactive interface of `WEB` `Application`s")
        var web: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the interactive interface of `VNC` `Application`s")
        var vnc: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the log API")
        var logs: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the interactive terminal API")
        var terminal: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable connection between peering `Job`s")
        var peers: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable extension of jobs")
        var timeExtension: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the retrieveUtilization of jobs")
        var utilization: Boolean? = null,
    )

    @Serializable
    data class VirtualMachine(
        @UCloudApiDoc("Flag to enable/disable this feature\n\nAll other flags are ignored if this is `false`.")
        var enabled: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the log API")
        var logs: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the VNC API")
        var vnc: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the interactive terminal API")
        var terminal: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable extension of jobs")
        var timeExtension: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable suspension of jobs")
        var suspension: Boolean? = null,
        @UCloudApiDoc("Flag to enable/disable the retrieveUtilization of jobs")
        var utilization: Boolean? = null,
    )
}

@Serializable
data class ComputeProductSupport(
    val product: ProductReference,
    val support: ComputeSupport,
)

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
open class JobsProvider(provider: String) : ResourceProviderApi<Job, JobSpecification, JobUpdate, JobIncludeFlags,
    JobStatus, Product.Compute, ComputeSupport>("jobs", provider) {
    override fun toString() = "JobsProvider($baseContext)"

    override val typeInfo = ResourceTypeInfo<Job, JobSpecification, JobUpdate, JobIncludeFlags, JobStatus,
        Product.Compute, ComputeSupport>()

    init {
        title = "Provider API: Compute"

        serializerLookupTable = mapOf(
            serializerEntry(OpenSession.serializer()),
            serializerEntry(JobsProviderFollowRequest.serializer()),
            serializerEntry(ShellRequest.serializer()),
            serializerEntry(ShellResponse.serializer())
        )

        val left = "←"
        val right = "→"

        fun req(
            id: String,
            ucloudSender: Boolean,
            call: KProperty<CallDescription<*, *, *>>,
            requestMessage: String,
            responseMessage: String? = null,
        ): String {
            return buildString {
                append(
                    "| [$id] Request | UCloud | ${if (ucloudSender) right else left} | Provider | " +
                        "${docCallRef(call)} | $requestMessage |"
                )

                if (responseMessage != null) {
                    append(
                        "\n| [$id] Response | UCloud | ${if (ucloudSender) left else right} | Provider | " +
                            "${docCallRef(call)} | $responseMessage |"
                    )
                }
            }
        }

        fun comment(id: String, message: String): String {
            return "| [$id] Comment | | | | | $message |"
        }

        //language=markdown
        description = """
The calls described in this section covers the API that providers of compute must implement. Not all
features of the compute API must be implemented. The individual calls and types will describe how the manifest
affects them.
            
Compute providers must answer to the calls listed below. Providers should take care to verify the bearer
token according to the TODO documentation.
            
The provider and UCloud works in tandem by sending pushing information to each other when new information
becomes available. Compute providers can push information to UCloud by using the
${docNamespaceRef("jobs.control")} API.

### What information does `Job` include?

The UCloud API will communicate with the provider and include a reference of the `Job` which the request is about. The
`Job` model has several optional fields which are not always included. You can see which flags are set by UCloud when
retrieving the `Job`. If you need additional data you may use ${docCallRef(JobsControl::retrieve)} to fetch additional
information about the job. The flags selected below should give the provider enough information that the rest can
easily be cached locally. For example, providers can with great benefit choose to cache product and application
information.

| Flag | Included | Comment |
|------|----------|---------|
| `includeParameters` | `true` | Specifies how the user invoked the application. |
| `includeApplication` | `false` | Application information specifies the tool and application running. Can safely be cached indefinitely by name and version. |
| `includeProduct` | `false` | Product information specifies dimensions of the machine. Can safely be cached for 24 hours by name. |
| `includeUpdates` | `false` | You, the provider, will have supplied all updates but they are stored by UCloud. |
| `includeWeb` | `false` | You, the provider, will supply this information. Asking would cause UCloud to ask you back. |
| `includeVnc` | `false` | You, the provider, will supply this information. Asking would cause UCloud to ask you back. |
| `includeShell` | `false` |  You, the provider, will supply this information. Asking would cause UCloud to ask you back. |
            
### Accounting
            
It is up to the provider how accounting is done and if they wish to push accounting information to UCloud. 
A provider might, for example, choose to do all of the accounting on their own (including tracking who has
access). This would allow a provider to use UCloud just as an interface.
           
If a provider wishes to use UCloud for accounting then this is possible. UCloud provides an API which 
allows the provider to charge for a running compute job. The provider may call this API repeatedly to 
charge the user for their job. UCloud will respond with a payment required if the user's wallet
is out of credits. This indicates to the compute provider that the job should be terminated (since they 
no longer have credit for the job).
 
### Example: Complete example with accounting
            
| ID | UCloud | - | Provider | Call | Message |
|----|--------|---|----------|------|---------|
${req("1", true, ::create, "Start application with ID `FOO123`", "OK")}
${req("2", false, JobsControl::update, "Proceed to `RUNNING`")}
${req("3", false, JobsControl::chargeCredits, "Charge for 15 minutes of use")}
${req("4", false, JobsControl::chargeCredits, "Charge for 15 minutes of use")}
${req("5", false, JobsControl::chargeCredits, "Charge for 15 minutes of use")}
${req("6", true, ::terminate, "Delete `FOO123`")}
${req("7", false, JobsControl::chargeCredits, "Charge for 3 minutes of use")}
${req("8", false, JobsControl::update, "Proceed to `SUCCESS`")}

### Example: Missing credits
            
| ID | UCloud | - | Provider | Call | Message |
|----|--------|---|----------|------|---------|
${req("1", true, ::create, "Start application with ID `FOO123`", "OK")}
${req("2", false, JobsControl::update, "Proceed to `RUNNING`")}
${req("3", false, JobsControl::chargeCredits, "Charge for 15 minutes of use", "${HttpStatusCode.PaymentRequired}")}
${req("4", false, JobsControl::update, "Proceed to `SUCCESS` with message 'Insufficient funds'")}

### Example: UCloud and provider out-of-sync

| ID | UCloud | - | Provider | Call | Message |
|----|--------|---|----------|------|---------|
${req("1", true, ::create, "Start application with ID `FOO123`", "OK")}
${req("2", false, JobsControl::update, "Proceed to `RUNNING`")}
${
            req(
                "3",
                false,
                JobsControl::chargeCredits,
                "Charge for 15 minutes of use",
                "${HttpStatusCode.PaymentRequired}"
            )
        }           
${
            req(
                "3",
                false,
                JobsControl::chargeCredits,
                "Charge for 15 minutes of use",
                "${HttpStatusCode.PaymentRequired}"
            )
        }           
${comment("4", "`FOO123` disappears/crashes at provider - Provider did not notice and notify UCloud automatically")}
${req("5", true, ::verify, "Verify that `FOO123` is running", "OK")}
${req("6", true, JobsControl::update, "Proceed `FOO123` to `FAILURE`")}
"""
    }

    val extend = call<JobsProviderExtendRequest, JobsProviderExtendResponse, CommonErrorMessage>("extend") {
        httpUpdate(baseContext, "extend", roles = Roles.PRIVILEGED)

        documentation {
            summary = "Extend the duration of a job"
        }
    }

    val terminate = call<BulkRequest<Job>, BulkResponse<Unit?>, CommonErrorMessage>("terminate") {
        httpUpdate(baseContext, "terminate", roles = Roles.PRIVILEGED)
    }

    val suspend = call<JobsProviderSuspendRequest, JobsProviderSuspendResponse, CommonErrorMessage>("suspend") {
        httpUpdate(baseContext, "suspend", roles = Roles.PRIVILEGED)

        documentation {
            summary = "Suspend a job"
        }
    }

    val follow = call<JobsProviderFollowRequest, JobsProviderFollowResponse, CommonErrorMessage>("follow") {
        auth {
            access = AccessRight.READ
            roles = Roles.PRIVILEGED
        }

        websocket("/ucloud/$namespace/websocket")
    }

    val openInteractiveSession =
        call<JobsProviderOpenInteractiveSessionRequest, JobsProviderOpenInteractiveSessionResponse,
            CommonErrorMessage>("openInteractiveSession") {
            httpUpdate(baseContext, "interactiveSession", roles = Roles.PRIVILEGED)
        }

    val retrieveUtilization = call<JobsProviderUtilizationRequest, JobsProviderUtilizationResponse,
        CommonErrorMessage>("retrieveUtilization") {
        httpRetrieve(baseContext, "utilization", roles = Roles.PRIVILEGED)
    }

}
