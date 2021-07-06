package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.provider.api.ResourceStatus
import dk.sdu.cloud.provider.api.ResourceUpdate

interface JobBoundUpdate<State : Enum<State>> : ResourceUpdate {
    val didBind: Boolean
    val newBinding: String?
    val state: State?
}

interface JobBoundStatus<P : Product, Support : ProductSupport> : ResourceStatus<P, Support> {
    @UCloudApiDoc("The ID of the `Job` that this `Resource` is currently bound to")
    val boundTo: String?
}