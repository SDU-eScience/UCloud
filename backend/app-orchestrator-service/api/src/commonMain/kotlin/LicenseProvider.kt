package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.calls.*

typealias LicenseProviderCreateRequest = BulkRequest<License>
typealias LicenseProviderCreateResponse = Unit

typealias LicenseProviderDeleteRequest = BulkRequest<License>
typealias LicenseProviderDeleteResponse = Unit

typealias LicenseProviderVerifyRequest = BulkRequest<License>
typealias LicenseProviderVerifyResponse = Unit

open class LicenseProvider(provider: String) : ResourceProviderApi<
    License,
    LicenseSpecification,
    LicenseUpdate,
    LicenseIncludeFlags,
    LicenseStatus,
    Product.License,
    LicenseSettings>("licenses", provider) {
        override val typeInfo =
            ResourceTypeInfo<License, LicenseSpecification, LicenseUpdate, LicenseIncludeFlags, LicenseStatus,
                Product.License, LicenseSettings>()
    }

/*open class LicenseProvider(namespace: String) : CallDescriptionContainer("licenses.provider.$namespace") {
    val baseContext = "/ucloud/$namespace/licenses"

    val create = call<LicenseProviderCreateRequest, LicenseProviderCreateResponse, CommonErrorMessage>("create") {
        httpCreate(baseContext, roles = Roles.PRIVILEGED)
    }

    val delete = call<LicenseProviderDeleteRequest, LicenseProviderDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext, roles = Roles.PRIVILEGED)
    }

    val verify = call<LicenseProviderVerifyRequest, LicenseProviderVerifyResponse, CommonErrorMessage>("verify") {
        httpVerify(baseContext, roles = Roles.PRIVILEGED)
    }
}*/
