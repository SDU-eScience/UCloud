package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.calls.*

@TSNamespace("compute.ingresses.control")
object IngressControl : ResourceControlApi<
    Ingress,
    IngressSpecification,
    IngressUpdate,
    IngressIncludeFlags,
    IngressStatus,
    Product.Ingress,
    IngressSettings>("ingresses") {

    override val typeInfo =
        ResourceTypeInfo<Ingress, IngressSpecification, IngressUpdate, IngressIncludeFlags, IngressStatus,
            Product.Ingress, IngressSettings>()
}