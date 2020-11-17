package dk.sdu.cloud.app.orchestrator.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.UCloudApiExperimental
import dk.sdu.cloud.calls.title
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.TYPE_PROPERTY
import io.ktor.http.*
import kotlin.reflect.KProperty

typealias ComputeCreateRequest = BulkRequest<ComputeCreateRequestItem>
typealias ComputeCreateResponse = Unit
typealias ComputeCreateRequestItem = Job

typealias ComputeDeleteRequest = BulkRequest<ComputeDeleteRequestItem>
typealias ComputeDeleteResponse = Unit
typealias ComputeDeleteRequestItem = FindByStringId

typealias ComputeExtendRequest = JobsExtendRequest
typealias ComputeExtendResponse = Unit

typealias ComputeSuspendRequest = BulkRequest<ComputeSuspendRequestItem>
typealias ComputeSuspendResponse = Unit
typealias ComputeSuspendRequestItem = FindByStringId

typealias ComputeVerifyRequest = BulkRequest<ComputeVerifyRequestItem>
typealias ComputeVerifyResponse = Unit
typealias ComputeVerifyRequestItem = Job

typealias ComputeRetrieveManifestRequest = Unit
typealias ComputeRetrieveManifestResponse = ComputeProviderManifestBody

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ComputeFollowRequest.Init::class, name = "init"),
    JsonSubTypes.Type(value = ComputeFollowRequest.CancelStream::class, name = "cancel"),
)
sealed class ComputeFollowRequest {
    class Init(val job: Job) : ComputeFollowRequest()
    class CancelStream : ComputeFollowRequest()
}

data class ComputeFollowResponse(
    val rank: Int,
    val stdout: String?,
    val stderr: String?,
)

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
open class Compute(namespace: String) : CallDescriptionContainer("jobs.compute.$namespace") {
    val baseContext = "/ucloud/$namespace/compute/jobs"

    init {
        title = "Provider API: Compute"

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
                append("| [$id] Request | UCloud | ${if (ucloudSender) right else left} | Provider | " +
                    "${docCallRef(call)} | $requestMessage |")

                if (responseMessage != null) {
                    append("\n| [$id] Response | UCloud | ${if (ucloudSender) left else right} | Provider | " +
                        "${docCallRef(call)} | $responseMessage |")
                }
            }
        }

        fun comment(id: String, message: String): String {
            return "| [$id] Comment | | | | | $message |"
        }

        //language=markdown
        description = """
The calls described in this section covers the API that providers of compute must implement. Not all
features of the compute API must be implemented, these can be configured by providing a different manifest
see ${docCallRef(::retrieveManifest)}). The individual calls and types will describe how the manifest
affects them.
            
Compute providers must answer to the calls listed below. Providers should take care to verify the bearer
token according to the TODO documentation.
            
The provider and UCloud works in tandem by sending pushing information to each other when new information
becomes available. Compute providers can push information to UCloud by using the
${docNamespaceRef("jobs.control")} API.
            
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
${req("6", true, ::delete, "Delete `FOO123`")}
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
${req("3", false, JobsControl::chargeCredits, "Charge for 15 minutes of use", "${HttpStatusCode.PaymentRequired}")}           
${req("3", false, JobsControl::chargeCredits, "Charge for 15 minutes of use", "${HttpStatusCode.PaymentRequired}")}           
${comment("4", "`FOO123` disappears/crashes at provider - Provider did not notice and notify UCloud automatically")}
${req("5", true, ::verify, "Verify that `FOO123` is running", "OK")}
${req("6", true, JobsControl::update, "Proceed `FOO123` to `FAILURE`")}
"""
    }

    val create = call<ComputeCreateRequest, ComputeCreateResponse, CommonErrorMessage>("create") {
        httpCreate(baseContext, roles = Roles.PRIVILEGED)

        documentation {
            summary = "Start a compute job"
            description = """
                Starts one or more compute jobs. The jobs have already been verified by UCloud and it is assumed to be
                ready for the provider. The provider can choose to reject the entire batch by responding with a 4XX or
                5XX status code. Note that the batch must be handled as a single transaction.
                
                The provider should respond to this request as soon as the jobs have been scheduled. The provider should
                then switch to ${docCallRef(JobsControl::update)} in order to provide updates about the progress.
            """.trimIndent()
        }
    }

    val delete = call<ComputeDeleteRequest, ComputeDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext, roles = Roles.PRIVILEGED)

        documentation {
            summary = "Request job cancellation and destruction"
            description = """
                Deletes one or more compute jobs. The provider should not only stop the compute job but also delete
                _compute_ related resources. For example, if the job is a virtual machine job, the underlying machine
                should also be deleted. None of the resources attached to the job, however, should be deleted.
            """.trimIndent()
        }
    }

    val extend = call<ComputeExtendRequest, ComputeExtendResponse, CommonErrorMessage>("extend") {
        httpUpdate(baseContext, "extend", roles = Roles.PRIVILEGED)

        documentation {
            summary = "Extend the duration of a job"
        }
    }

    val suspend = call<ComputeSuspendRequest, ComputeSuspendResponse, CommonErrorMessage>("suspend") {
        httpUpdate(baseContext, "suspend", roles = Roles.PRIVILEGED)

        documentation {
            summary = "Suspend a job"
        }
    }

    val verify = call<ComputeVerifyRequest, ComputeVerifyResponse, CommonErrorMessage>("verify") {
        httpVerify(baseContext, roles = Roles.PRIVILEGED)

        documentation {
            summary = "Verify UCloud data is synchronized with provider"
            description = """
                This call is periodically executed by UCloud against all active providers. It is the job of the
                provider to ensure that the jobs listed in the request are in its local database. If some of the
                jobs are not in the provider's database then this should be treated as a job which is no longer valid.
                The compute backend should trigger normal cleanup code and notify UCloud about the job's termination.
                
                The backend should _not_ attempt to start the job.
            """.trimIndent()
        }
    }

    val retrieveManifest = call<ComputeRetrieveManifestRequest, ComputeRetrieveManifestResponse, CommonErrorMessage>(
        "retrieveManifest"
    ) {
        httpRetrieve(baseContext, "manifest", roles = Roles.PRIVILEGED)

        documentation {
            summary = "Retrieves the compute provider manifest"
        }
    }

    val follow = call<ComputeFollowRequest, ComputeFollowResponse, CommonErrorMessage>("follow") {
        auth {
            access = AccessRight.READ
            roles = Roles.PRIVILEGED
        }

        websocket(baseContext)
    }
}
