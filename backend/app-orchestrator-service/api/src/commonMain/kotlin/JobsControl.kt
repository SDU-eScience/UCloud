package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.calls.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class JobsControlSubmitFileRequest(
    val jobId: String,
    val filePath: String,
)
typealias JobsControlSubmitFileResponse = Unit

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object JobsControl : ResourceControlApi<Job, JobSpecification, JobUpdate, JobIncludeFlags, JobStatus,
    Product.Compute, ComputeSupport>("jobs") {
    init {
        title = "Job control"
        description = """
            Internal API between UCloud and compute providers. This API allows compute providers to push state changes
            to UCloud.
        """.trimIndent()
    }

    override val typeInfo = ResourceTypeInfo<Job, JobSpecification, JobUpdate, JobIncludeFlags, JobStatus,
        Product.Compute, ComputeSupport>()

    @Deprecated("Will be going away soon")
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
