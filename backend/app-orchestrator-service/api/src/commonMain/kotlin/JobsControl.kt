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

    override val typeInfo = ResourceTypeInfo<Job, JobSpecification, JobUpdate, JobIncludeFlags, JobStatus,
        Product.Compute, ComputeSupport>()
}
