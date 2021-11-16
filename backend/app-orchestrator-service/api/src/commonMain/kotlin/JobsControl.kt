package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.calls.*
import kotlin.reflect.typeOf

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
        typeOf<Job>(),
        JobSpecification.serializer(),
        typeOf<JobSpecification>(),
        JobUpdate.serializer(),
        typeOf<JobUpdate>(),
        JobIncludeFlags.serializer(),
        typeOf<JobIncludeFlags>(),
        JobStatus.serializer(),
        typeOf<JobStatus>(),
        ComputeSupport.serializer(),
        typeOf<ComputeSupport>(),
        Product.Compute.serializer(),
        typeOf<Product.Compute>(),
    )
}
