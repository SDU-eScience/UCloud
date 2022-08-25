package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.calls.*

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
}
