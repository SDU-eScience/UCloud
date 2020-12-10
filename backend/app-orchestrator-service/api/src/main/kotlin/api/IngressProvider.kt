package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.*

typealias IngressProviderCreateRequest = BulkRequest<Ingress>
typealias IngressProviderCreateResponse = Unit

typealias IngressProviderDeleteRequest = BulkRequest<Ingress>
typealias IngressProviderDeleteResponse = Unit

typealias IngressProviderVerifyRequest = BulkRequest<Ingress>
typealias IngressProviderVerifyResponse = Unit

data class IngressProviderRetrieveSettingsRequest(val product: ProductReference)
typealias IngressProviderRetrieveSettingsResponse = IngressSettings

open class IngressProvider(namespace: String) : CallDescriptionContainer("ingresses.provider.$namespace") {
    val baseContext = "/ucloud/$namespace/compute/ingresses"

    val create = call<IngressProviderCreateRequest, IngressProviderCreateResponse, CommonErrorMessage>("create") {
        httpCreate(baseContext, roles = Roles.PRIVILEGED)
    }

    val delete = call<IngressProviderDeleteRequest, IngressProviderDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext, roles = Roles.PRIVILEGED)
    }

    val verify = call<IngressProviderVerifyRequest, IngressProviderVerifyResponse, CommonErrorMessage>("verify") {
        httpVerify(baseContext, roles = Roles.PRIVILEGED)
    }

    val retrieveSettings = call<IngressProviderRetrieveSettingsRequest,
        IngressProviderRetrieveSettingsResponse, CommonErrorMessage>("retrieveSettings") {
            httpRetrieve(baseContext, "settings", roles = Roles.PRIVILEGED)
    }
}
