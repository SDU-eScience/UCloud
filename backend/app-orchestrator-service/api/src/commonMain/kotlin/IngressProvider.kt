package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.calls.*

open class IngressProvider(provider: String) : ResourceProviderApi<
    Ingress,
    IngressSpecification,
    IngressUpdate,
    IngressIncludeFlags,
    IngressStatus,
    Product.Ingress,
    IngressSettings>("ingresses", provider) {
    override val typeInfo =
        ResourceTypeInfo<Ingress, IngressSpecification, IngressUpdate, IngressIncludeFlags, IngressStatus,
            Product.Ingress, IngressSettings>()
}
