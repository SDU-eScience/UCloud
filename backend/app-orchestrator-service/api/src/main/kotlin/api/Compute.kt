package dk.sdu.cloud.app.orchestrator.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.store.api.BooleanFlagParameter
import dk.sdu.cloud.app.store.api.EnvironmentVariableParameter
import dk.sdu.cloud.app.store.api.VariableInvocationParameter
import dk.sdu.cloud.app.store.api.WordInvocationParameter
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.UCloudApiExperimental
import dk.sdu.cloud.calls.title
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.TYPE_PROPERTY

typealias ComputeCreateRequest = BulkRequest<ComputeCreateRequestItem>
typealias ComputeCreateResponse = Unit
typealias ComputeCreateRequestItem = Job

typealias ComputeDeleteRequest = BulkRequest<ComputeDeleteRequestItem>
typealias ComputeDeleteResponse = Unit
typealias ComputeDeleteRequestItem = FindByStringId

typealias ComputeExtendRequest = BulkRequest<ComputeExtendRequestItem>
typealias ComputeExtendResponse = Unit
typealias ComputeExtendRequestItem = JobsExtendRequest

typealias ComputeSuspendRequest = BulkRequest<ComputeSuspendRequestItem>
typealias ComputeSuspendResponse = Unit
typealias ComputeSuspendRequestItem = FindByStringId

typealias ComputeVerifyRequest = BulkRequest<ComputeVerifyRequestItem>
typealias ComputeVerifyResponse = Unit
typealias ComputeVerifyRequestItem = Job

typealias ComputeRetrieveManifestRequest = Unit
data class ComputeRetrieveManifestResponse(
    // Provided directly to UCloud by provider:
    //  - Provider ID (Namespace)
    //  - Hostname

    val features: ManifestFeatureSupport,
)

data class ManifestFeatureSupport(
    val web: Boolean = false,
    val vnc: Boolean = false,
    val batch: Boolean = false,
    val docker: Boolean = false,
    val virtualMachine: Boolean = false,
    val logs: Boolean = false,
)

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
    val stderr: String?
)

@UCloudApiExperimental(UCloudApiExperimental.Level.ALPHA)
abstract class Compute(namespace: String) : CallDescriptionContainer("jobs.compute.$namespace") {
    val baseContext = "/api/jobs/compute/$namespace"

    init {
        title = "Compute backend ($namespace)"
    }

    val create = call<ComputeCreateRequest, ComputeCreateResponse, CommonErrorMessage>("create") {
        httpCreate(baseContext)

        documentation {
            summary = "Start a compute job"
        }
    }

    val delete = call<ComputeDeleteRequest, ComputeDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext)

        documentation {
            summary = "Request job cancellation and destruction"
        }
    }

    val extend = call<ComputeExtendRequest, ComputeExtendResponse, CommonErrorMessage>("extend") {
        httpUpdate(baseContext, "extend")

        documentation {
            summary = "Extend the duration of a job"
        }
    }

    val suspend = call<ComputeSuspendRequest, ComputeSuspendResponse, CommonErrorMessage>("suspend") {
        httpUpdate(baseContext, "suspend")

        documentation {
            summary = "Suspend a job"
        }
    }

    val verify = call<ComputeVerifyRequest, ComputeVerifyResponse, CommonErrorMessage>("verify") {
        httpVerify(baseContext)

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
        httpRetrieve(baseContext, "manifest")

        documentation {
            summary = "Retrieves the compute provider manifest"
        }
    }

    val follow = call<ComputeFollowRequest, ComputeFollowResponse, CommonErrorMessage>("follow") {
        auth {
            access = AccessRight.READ
        }

        websocket(baseContext)
    }
}
