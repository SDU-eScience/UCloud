package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiOwnedBy
import dk.sdu.cloud.calls.UCloudApiStable
import dk.sdu.cloud.provider.api.ResourceStatus
import dk.sdu.cloud.provider.api.ResourceUpdate
import kotlinx.serialization.Serializable

@UCloudApiStable
enum class JobBindKind {
    BIND,
    UNBIND
}

@Serializable
@UCloudApiOwnedBy(Jobs::class)
@UCloudApiStable
data class JobBinding(val kind: JobBindKind, val job: String)

interface JobBoundUpdate<State : Enum<State>> : ResourceUpdate {
    val binding: JobBinding?
    val state: State?
}

interface JobBoundStatus<P : Product, Support : ProductSupport> : ResourceStatus<P, Support> {
    @UCloudApiDoc("The IDs of the `Job`s that this `Resource` is currently bound to")
    val boundTo: List<String>
}
