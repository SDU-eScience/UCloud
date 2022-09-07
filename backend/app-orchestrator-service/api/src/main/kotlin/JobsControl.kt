package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class JobsControlBrowseSshKeys(
    val jobId: String,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

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

    val browseSshKeys = call(
        "browseSshKeys",
        JobsControlBrowseSshKeys.serializer(),
        PageV2.serializer(SSHKey.serializer()),
        CommonErrorMessage.serializer()
    ) {
        httpUpdate(baseContext, "browseSshKeys")
    }
}
