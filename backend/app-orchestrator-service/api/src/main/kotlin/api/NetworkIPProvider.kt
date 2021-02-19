package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*

typealias NetworkIPProviderCreateRequest = BulkRequest<NetworkIP>
typealias NetworkIPProviderCreateResponse = Unit

typealias NetworkIPProviderDeleteRequest = BulkRequest<NetworkIP>
typealias NetworkIPProviderDeleteResponse = Unit

typealias NetworkIPProviderVerifyRequest = BulkRequest<NetworkIP>
typealias NetworkIPProviderVerifyResponse = Unit

typealias NetworkIPProviderUpdateFirewallRequest = BulkRequest<FirewallAndId>
typealias NetworkIPProviderUpdateFirewallResponse = Unit

open class NetworkIPProvider(namespace: String) : CallDescriptionContainer("networkips.provider.$namespace") {
    val baseContext = "/ucloud/$namespace/networkips"

    val create = call<NetworkIPProviderCreateRequest, NetworkIPProviderCreateResponse, CommonErrorMessage>("create") {
        httpCreate(baseContext, roles = Roles.PRIVILEGED)
    }

    val delete = call<NetworkIPProviderDeleteRequest, NetworkIPProviderDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext, roles = Roles.PRIVILEGED)
    }

    val verify = call<NetworkIPProviderVerifyRequest, NetworkIPProviderVerifyResponse, CommonErrorMessage>("verify") {
        httpVerify(baseContext, roles = Roles.PRIVILEGED)
    }

    val updateFirewall = call<NetworkIPProviderUpdateFirewallRequest,
        NetworkIPProviderUpdateFirewallResponse, CommonErrorMessage>("updateFirewall") {
        httpUpdate(baseContext, "firewall", roles = Roles.PRIVILEGED)
    }
}
