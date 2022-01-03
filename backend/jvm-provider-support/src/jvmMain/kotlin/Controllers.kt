@file:Suppress("RemoveRedundantQualifierName", "RedundantUnitReturnType", "unused", "UNREACHABLE_CODE", "UNCHECKED_CAST")
package dk.sdu.cloud.providers

/* AUTO GENERATED CODE - DO NOT MODIFY */
/* Generated at: Mon Dec 06 10:49:27 CET 2021 */


import dk.sdu.cloud.providers.UCloudRpcDispatcher
import dk.sdu.cloud.providers.UCloudWsDispatcher
import dk.sdu.cloud.providers.UCloudWsContext
import dk.sdu.cloud.calls.CallDescription
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping(
    "/ucloud/*/jobs/retrieveProducts",
    "/ucloud/*/jobs/verify",
    "/ucloud/*/jobs/suspend",
    "/ucloud/*/jobs/extend",
    "/ucloud/*/jobs/interactiveSession",
    "/ucloud/*/jobs/retrieveUtilization",
    "/ucloud/*/jobs/terminate",
    "/ucloud/*/jobs/updateAcl",
    "/ucloud/*/jobs",
)
abstract class JobsController(
    private val providerId: String,
    wsDispatcher: UCloudWsDispatcher,
): UCloudRpcDispatcher(dk.sdu.cloud.app.orchestrator.api.JobsProvider(providerId), wsDispatcher) {
    /**
     * Extend the duration of one or more jobs
     *
     * __Implementation requirements:__ 
     *  - [`docker.timeExtension = true`](#TYPEREFLINK#= ComputeSupport.Docker) or 
     *  - [`virtualMachine.timeExtension = true`](#TYPEREFLINK#= ComputeSupport.VirtualMachine)
     * 
     * For more information, see the end-user API (#CALLREF#= jobs.extend)
     * 
     */
    abstract fun extend(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.JobsProviderExtendRequestItem>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    /**
     * Request job cancellation and destruction
     *
     * __Implementation requirements:__ Mandatory
     * 
     * For more information, see the end-user API (#CALLREF#= jobs.terminate)
     * 
     */
    abstract fun terminate(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    /**
     * Suspend a job
     *
     * __Implementation requirements:__ 
     *  - [`virtualMachine.suspension = true`](#TYPEREFLINK#= ComputeSupport.VirtualMachine)
     * 
     * For more information, see the end-user API (#CALLREF#= jobs.suspend)
     * 
     */
    abstract fun suspend(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>,
    ): kotlin.Unit
    
    
    /**
     * Follow the progress of a job
     *
     * __Implementation requirements:__ 
     *  - [`docker.logs = true`](#TYPEREFLINK#= ComputeSupport.Docker) or
     *  - [`virtualMachine.logs = true`](#TYPEREFLINK#= ComputeSupport.VirtualMachine)
     * 
     * For more information, see the end-user API (#CALLREF#= jobs.follow)
     * 
     */
    abstract fun follow(
        request: dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowRequest,
        wsContext: UCloudWsContext<dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowRequest, dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowResponse, dk.sdu.cloud.CommonErrorMessage>,
    )
    
    
    /**
     * Opens an interactive session (e.g. terminal, web or VNC)
     *
     * __Implementation requirements:__ 
     *  - [`docker.vnc = true`](#TYPEREFLINK#= ComputeSupport.Docker) or
     *  - [`docker.terminal = true`](#TYPEREFLINK#= ComputeSupport.Docker) or
     *  - [`docker.web = true`](#TYPEREFLINK#= ComputeSupport.Docker) or
     *  - [`virtualMachine.vnc = true`](#TYPEREFLINK#= ComputeSupport.VirtualMachine) or
     *  - [`virtualMachine.terminal = true`](#TYPEREFLINK#= ComputeSupport.VirtualMachine)
     * 
     * For more information, see the end-user API (#CALLREF#= jobs.openInteractiveSession)
     * 
     */
    abstract fun openInteractiveSession(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.JobsProviderOpenInteractiveSessionRequestItem>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.app.orchestrator.api.OpenSession>
    
    
    /**
     * Retrieve information about how busy the provider's cluster currently is
     *
     * __Implementation requirements:__ 
     *  - [`docker.utilization = true`](#TYPEREFLINK#= ComputeSupport.Docker) or
     *  - [`virtualMachine.utilization = true`](#TYPEREFLINK#= ComputeSupport.VirtualMachine)
     * 
     * For more information, see the end-user API (#CALLREF#= jobs.retrieveUtilization)
     * 
     */
    abstract fun retrieveUtilization(
        request: kotlin.Unit,
    ): dk.sdu.cloud.app.orchestrator.api.JobsRetrieveUtilizationResponse
    
    
    /**
     * Retrieve product support for this providers
     *
     * This endpoint responds with the #TYPEREF#= dk.sdu.cloud.accounting.api.Product s supported by
     * this provider along with details for how #TYPEREF#= dk.sdu.cloud.accounting.api.Product is
     * supported. The #TYPEREF#= dk.sdu.cloud.accounting.api.Product s must be registered with
     * UCloud/Core already.
     */
    abstract fun retrieveProducts(
        request: kotlin.Unit,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.app.orchestrator.api.ComputeSupport>
    
    
    abstract fun create(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.FindByStringId>
    
    
    /**
     * Invoked by UCloud/Core to trigger verification of a single batch
     *
     * This endpoint is periodically invoked by UCloud/Core for resources which are deemed active. The
     * Provider should immediately determine if these are still valid and recognized by the Provider.
     * If any of the resources are not valid, then the Provider should notify UCloud/Core by issuing
     * an update for each affected resource.
     */
    abstract fun verify(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>,
    ): kotlin.Unit
    
    
    /**
     * Callback received by the Provider when permissions are updated
     *
     * This endpoint is mandatory for Providers to implement. If the Provider does not need to keep
     * internal state, then they may simply ignore this request by responding with `200 OK`. The
     * Provider _MUST_ reply with an OK status. UCloud/Core will fail the request if the Provider does
     * not acknowledge the request.
     */
    abstract fun updateAcl(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.app.orchestrator.api.Job>>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, S : Any, E : Any> dispatchToHandler(
        call: CallDescription<R, S, E>,
        request: R,
        rawRequest: HttpServletRequest,
        rawResponse: HttpServletResponse,
    ): S {
        return when (call.fullName.replace(providerId, "*")) {
            "jobs.provider.*.extend" -> extend(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.JobsProviderExtendRequestItem>) as S
            "jobs.provider.*.terminate" -> terminate(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>) as S
            "jobs.provider.*.suspend" -> suspend(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>) as S
            "jobs.provider.*.openInteractiveSession" -> openInteractiveSession(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.JobsProviderOpenInteractiveSessionRequestItem>) as S
            "jobs.provider.*.retrieveUtilization" -> retrieveUtilization(request as kotlin.Unit) as S
            "jobs.provider.*.retrieveProducts" -> retrieveProducts(request as kotlin.Unit) as S
            "jobs.provider.*.create" -> create(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>) as S
            "jobs.provider.*.verify" -> verify(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>) as S
            "jobs.provider.*.updateAcl" -> updateAcl(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.app.orchestrator.api.Job>>) as S
            else -> error("Unhandled call")
        }
    }
    
    override fun canHandleWebSocketCall(call: CallDescription<*, *, *>): Boolean {
        if (call.fullName.replace(providerId, "*") == "jobs.provider.*.follow") return true
        return false
    }
    
    override fun <R : Any, S : Any, E : Any> dispatchToWebSocketHandler(
        ctx: UCloudWsContext<R, S, E>,
        request: R,
    ) {
        when (ctx.call.fullName.replace(providerId, "*")) {
            "jobs.provider.*.follow" -> follow(request as dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowRequest, ctx as UCloudWsContext<dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowRequest, dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowResponse, dk.sdu.cloud.CommonErrorMessage>)
            else -> error("Unhandled call")
        }
    }
    
}

@RequestMapping(
    "/ucloud/*/networkips/updateAcl",
    "/ucloud/*/networkips",
    "/ucloud/*/networkips/retrieveProducts",
    "/ucloud/*/networkips/verify",
    "/ucloud/*/networkips/firewall",
)
abstract class NetworkIPController(
    private val providerId: String,
    wsDispatcher: UCloudWsDispatcher,
): UCloudRpcDispatcher(dk.sdu.cloud.app.orchestrator.api.NetworkIPProvider(providerId), wsDispatcher) {
    abstract fun updateFirewall(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.FirewallAndIP>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    abstract fun delete(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.NetworkIP>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    abstract fun create(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.NetworkIP>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.FindByStringId>
    
    
    /**
     * Retrieve product support for this providers
     *
     * This endpoint responds with the #TYPEREF#= dk.sdu.cloud.accounting.api.Product s supported by
     * this provider along with details for how #TYPEREF#= dk.sdu.cloud.accounting.api.Product is
     * supported. The #TYPEREF#= dk.sdu.cloud.accounting.api.Product s must be registered with
     * UCloud/Core already.
     */
    abstract fun retrieveProducts(
        request: kotlin.Unit,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.app.orchestrator.api.NetworkIPSupport>
    
    
    /**
     * Callback received by the Provider when permissions are updated
     *
     * This endpoint is mandatory for Providers to implement. If the Provider does not need to keep
     * internal state, then they may simply ignore this request by responding with `200 OK`. The
     * Provider _MUST_ reply with an OK status. UCloud/Core will fail the request if the Provider does
     * not acknowledge the request.
     */
    abstract fun updateAcl(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.app.orchestrator.api.NetworkIP>>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    /**
     * Invoked by UCloud/Core to trigger verification of a single batch
     *
     * This endpoint is periodically invoked by UCloud/Core for resources which are deemed active. The
     * Provider should immediately determine if these are still valid and recognized by the Provider.
     * If any of the resources are not valid, then the Provider should notify UCloud/Core by issuing
     * an update for each affected resource.
     */
    abstract fun verify(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.NetworkIP>,
    ): kotlin.Unit
    
    
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, S : Any, E : Any> dispatchToHandler(
        call: CallDescription<R, S, E>,
        request: R,
        rawRequest: HttpServletRequest,
        rawResponse: HttpServletResponse,
    ): S {
        return when (call.fullName.replace(providerId, "*")) {
            "networkips.provider.*.updateFirewall" -> updateFirewall(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.FirewallAndIP>) as S
            "networkips.provider.*.delete" -> delete(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.NetworkIP>) as S
            "networkips.provider.*.create" -> create(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.NetworkIP>) as S
            "networkips.provider.*.retrieveProducts" -> retrieveProducts(request as kotlin.Unit) as S
            "networkips.provider.*.updateAcl" -> updateAcl(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.app.orchestrator.api.NetworkIP>>) as S
            "networkips.provider.*.verify" -> verify(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.NetworkIP>) as S
            else -> error("Unhandled call")
        }
    }
    
    override fun canHandleWebSocketCall(call: CallDescription<*, *, *>): Boolean {
        return false
    }
    
    override fun <R : Any, S : Any, E : Any> dispatchToWebSocketHandler(
        ctx: UCloudWsContext<R, S, E>,
        request: R,
    ) {
        when (ctx.call.fullName.replace(providerId, "*")) {
            else -> error("Unhandled call")
        }
    }
    
}

@RequestMapping(
    "/ucloud/*/ingresses",
    "/ucloud/*/ingresses/verify",
    "/ucloud/*/ingresses/updateAcl",
    "/ucloud/*/ingresses/retrieveProducts",
)
abstract class IngressController(
    private val providerId: String,
    wsDispatcher: UCloudWsDispatcher,
): UCloudRpcDispatcher(dk.sdu.cloud.app.orchestrator.api.IngressProvider(providerId), wsDispatcher) {
    abstract fun delete(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Ingress>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    abstract fun create(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Ingress>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.FindByStringId>
    
    
    /**
     * Retrieve product support for this providers
     *
     * This endpoint responds with the #TYPEREF#= dk.sdu.cloud.accounting.api.Product s supported by
     * this provider along with details for how #TYPEREF#= dk.sdu.cloud.accounting.api.Product is
     * supported. The #TYPEREF#= dk.sdu.cloud.accounting.api.Product s must be registered with
     * UCloud/Core already.
     */
    abstract fun retrieveProducts(
        request: kotlin.Unit,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.app.orchestrator.api.IngressSupport>
    
    
    /**
     * Callback received by the Provider when permissions are updated
     *
     * This endpoint is mandatory for Providers to implement. If the Provider does not need to keep
     * internal state, then they may simply ignore this request by responding with `200 OK`. The
     * Provider _MUST_ reply with an OK status. UCloud/Core will fail the request if the Provider does
     * not acknowledge the request.
     */
    abstract fun updateAcl(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.app.orchestrator.api.Ingress>>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    /**
     * Invoked by UCloud/Core to trigger verification of a single batch
     *
     * This endpoint is periodically invoked by UCloud/Core for resources which are deemed active. The
     * Provider should immediately determine if these are still valid and recognized by the Provider.
     * If any of the resources are not valid, then the Provider should notify UCloud/Core by issuing
     * an update for each affected resource.
     */
    abstract fun verify(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Ingress>,
    ): kotlin.Unit
    
    
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, S : Any, E : Any> dispatchToHandler(
        call: CallDescription<R, S, E>,
        request: R,
        rawRequest: HttpServletRequest,
        rawResponse: HttpServletResponse,
    ): S {
        return when (call.fullName.replace(providerId, "*")) {
            "ingresses.provider.*.delete" -> delete(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Ingress>) as S
            "ingresses.provider.*.create" -> create(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Ingress>) as S
            "ingresses.provider.*.retrieveProducts" -> retrieveProducts(request as kotlin.Unit) as S
            "ingresses.provider.*.updateAcl" -> updateAcl(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.app.orchestrator.api.Ingress>>) as S
            "ingresses.provider.*.verify" -> verify(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Ingress>) as S
            else -> error("Unhandled call")
        }
    }
    
    override fun canHandleWebSocketCall(call: CallDescription<*, *, *>): Boolean {
        return false
    }
    
    override fun <R : Any, S : Any, E : Any> dispatchToWebSocketHandler(
        ctx: UCloudWsContext<R, S, E>,
        request: R,
    ) {
        when (ctx.call.fullName.replace(providerId, "*")) {
            else -> error("Unhandled call")
        }
    }
    
}

@RequestMapping(
)
abstract class ShellsController(
    private val providerId: String,
    wsDispatcher: UCloudWsDispatcher,
): UCloudRpcDispatcher(dk.sdu.cloud.app.orchestrator.api.Shells(providerId), wsDispatcher) {
    abstract fun open(
        request: dk.sdu.cloud.app.orchestrator.api.ShellRequest,
        wsContext: UCloudWsContext<dk.sdu.cloud.app.orchestrator.api.ShellRequest, dk.sdu.cloud.app.orchestrator.api.ShellResponse, dk.sdu.cloud.CommonErrorMessage>,
    )
    
    
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, S : Any, E : Any> dispatchToHandler(
        call: CallDescription<R, S, E>,
        request: R,
        rawRequest: HttpServletRequest,
        rawResponse: HttpServletResponse,
    ): S {
        return when (call.fullName.replace(providerId, "*")) {
            else -> error("Unhandled call")
        }
    }
    
    override fun canHandleWebSocketCall(call: CallDescription<*, *, *>): Boolean {
        if (call.fullName.replace(providerId, "*") == "jobs.compute.*.shell.open") return true
        return false
    }
    
    override fun <R : Any, S : Any, E : Any> dispatchToWebSocketHandler(
        ctx: UCloudWsContext<R, S, E>,
        request: R,
    ) {
        when (ctx.call.fullName.replace(providerId, "*")) {
            "jobs.compute.*.shell.open" -> open(request as dk.sdu.cloud.app.orchestrator.api.ShellRequest, ctx as UCloudWsContext<dk.sdu.cloud.app.orchestrator.api.ShellRequest, dk.sdu.cloud.app.orchestrator.api.ShellResponse, dk.sdu.cloud.CommonErrorMessage>)
            else -> error("Unhandled call")
        }
    }
    
}

@RequestMapping(
    "/ucloud/*/licenses",
    "/ucloud/*/licenses/updateAcl",
    "/ucloud/*/licenses/retrieveProducts",
    "/ucloud/*/licenses/verify",
)
abstract class LicenseController(
    private val providerId: String,
    wsDispatcher: UCloudWsDispatcher,
): UCloudRpcDispatcher(dk.sdu.cloud.app.orchestrator.api.LicenseProvider(providerId), wsDispatcher) {
    abstract fun delete(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.License>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    abstract fun create(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.License>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.FindByStringId>
    
    
    /**
     * Retrieve product support for this providers
     *
     * This endpoint responds with the #TYPEREF#= dk.sdu.cloud.accounting.api.Product s supported by
     * this provider along with details for how #TYPEREF#= dk.sdu.cloud.accounting.api.Product is
     * supported. The #TYPEREF#= dk.sdu.cloud.accounting.api.Product s must be registered with
     * UCloud/Core already.
     */
    abstract fun retrieveProducts(
        request: kotlin.Unit,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.app.orchestrator.api.LicenseSupport>
    
    
    /**
     * Callback received by the Provider when permissions are updated
     *
     * This endpoint is mandatory for Providers to implement. If the Provider does not need to keep
     * internal state, then they may simply ignore this request by responding with `200 OK`. The
     * Provider _MUST_ reply with an OK status. UCloud/Core will fail the request if the Provider does
     * not acknowledge the request.
     */
    abstract fun updateAcl(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.app.orchestrator.api.License>>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    /**
     * Invoked by UCloud/Core to trigger verification of a single batch
     *
     * This endpoint is periodically invoked by UCloud/Core for resources which are deemed active. The
     * Provider should immediately determine if these are still valid and recognized by the Provider.
     * If any of the resources are not valid, then the Provider should notify UCloud/Core by issuing
     * an update for each affected resource.
     */
    abstract fun verify(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.License>,
    ): kotlin.Unit
    
    
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, S : Any, E : Any> dispatchToHandler(
        call: CallDescription<R, S, E>,
        request: R,
        rawRequest: HttpServletRequest,
        rawResponse: HttpServletResponse,
    ): S {
        return when (call.fullName.replace(providerId, "*")) {
            "licenses.provider.*.delete" -> delete(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.License>) as S
            "licenses.provider.*.create" -> create(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.License>) as S
            "licenses.provider.*.retrieveProducts" -> retrieveProducts(request as kotlin.Unit) as S
            "licenses.provider.*.updateAcl" -> updateAcl(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.app.orchestrator.api.License>>) as S
            "licenses.provider.*.verify" -> verify(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.License>) as S
            else -> error("Unhandled call")
        }
    }
    
    override fun canHandleWebSocketCall(call: CallDescription<*, *, *>): Boolean {
        return false
    }
    
    override fun <R : Any, S : Any, E : Any> dispatchToWebSocketHandler(
        ctx: UCloudWsContext<R, S, E>,
        request: R,
    ) {
        when (ctx.call.fullName.replace(providerId, "*")) {
            else -> error("Unhandled call")
        }
    }
    
}

@RequestMapping(
    "/ucloud/*/files/collections",
    "/ucloud/*/files/collections/verify",
    "/ucloud/*/files/collections/retrieveProducts",
    "/ucloud/*/files/collections/rename",
    "/ucloud/*/files/collections/updateAcl",
)
abstract class FileCollectionsController(
    private val providerId: String,
    wsDispatcher: UCloudWsDispatcher,
): UCloudRpcDispatcher(dk.sdu.cloud.file.orchestrator.api.FileCollectionsProvider(providerId), wsDispatcher) {
    abstract fun rename(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FileCollectionsProviderRenameRequestItem>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    abstract fun delete(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FileCollection>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    abstract fun create(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FileCollection>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.FindByStringId>
    
    
    /**
     * Retrieve product support for this providers
     *
     * This endpoint responds with the #TYPEREF#= dk.sdu.cloud.accounting.api.Product s supported by
     * this provider along with details for how #TYPEREF#= dk.sdu.cloud.accounting.api.Product is
     * supported. The #TYPEREF#= dk.sdu.cloud.accounting.api.Product s must be registered with
     * UCloud/Core already.
     */
    abstract fun retrieveProducts(
        request: kotlin.Unit,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.file.orchestrator.api.FSSupport>
    
    
    /**
     * Callback received by the Provider when permissions are updated
     *
     * This endpoint is mandatory for Providers to implement. If the Provider does not need to keep
     * internal state, then they may simply ignore this request by responding with `200 OK`. The
     * Provider _MUST_ reply with an OK status. UCloud/Core will fail the request if the Provider does
     * not acknowledge the request.
     */
    abstract fun updateAcl(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.file.orchestrator.api.FileCollection>>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    /**
     * Invoked by UCloud/Core to trigger verification of a single batch
     *
     * This endpoint is periodically invoked by UCloud/Core for resources which are deemed active. The
     * Provider should immediately determine if these are still valid and recognized by the Provider.
     * If any of the resources are not valid, then the Provider should notify UCloud/Core by issuing
     * an update for each affected resource.
     */
    abstract fun verify(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FileCollection>,
    ): kotlin.Unit
    
    
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, S : Any, E : Any> dispatchToHandler(
        call: CallDescription<R, S, E>,
        request: R,
        rawRequest: HttpServletRequest,
        rawResponse: HttpServletResponse,
    ): S {
        return when (call.fullName.replace(providerId, "*")) {
            "files.collections.provider.*.rename" -> rename(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FileCollectionsProviderRenameRequestItem>) as S
            "files.collections.provider.*.delete" -> delete(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FileCollection>) as S
            "files.collections.provider.*.create" -> create(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FileCollection>) as S
            "files.collections.provider.*.retrieveProducts" -> retrieveProducts(request as kotlin.Unit) as S
            "files.collections.provider.*.updateAcl" -> updateAcl(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.file.orchestrator.api.FileCollection>>) as S
            "files.collections.provider.*.verify" -> verify(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FileCollection>) as S
            else -> error("Unhandled call")
        }
    }
    
    override fun canHandleWebSocketCall(call: CallDescription<*, *, *>): Boolean {
        return false
    }
    
    override fun <R : Any, S : Any, E : Any> dispatchToWebSocketHandler(
        ctx: UCloudWsContext<R, S, E>,
        request: R,
    ) {
        when (ctx.call.fullName.replace(providerId, "*")) {
            else -> error("Unhandled call")
        }
    }
    
}

@RequestMapping(
    "/ucloud/*/files/retrieveProducts",
    "/ucloud/*/files/retrieve",
    "/ucloud/*/files",
    "/ucloud/*/files/emptyTrash",
    "/ucloud/*/files/browse",
    "/ucloud/*/files/download",
    "/ucloud/*/files/folder",
    "/ucloud/*/files/verify",
    "/ucloud/*/files/trash",
    "/ucloud/*/files/updateAcl",
    "/ucloud/*/files/upload",
    "/ucloud/*/files/move",
    "/ucloud/*/files/copy",
    "/ucloud/*/files/search",
)
abstract class FilesController(
    private val providerId: String,
    wsDispatcher: UCloudWsDispatcher,
): UCloudRpcDispatcher(dk.sdu.cloud.file.orchestrator.api.FilesProvider(providerId), wsDispatcher) {
    abstract fun browse(
        request: dk.sdu.cloud.file.orchestrator.api.FilesProviderBrowseRequest,
    ): dk.sdu.cloud.PageV2<dk.sdu.cloud.file.orchestrator.api.PartialUFile>
    
    
    abstract fun retrieve(
        request: dk.sdu.cloud.file.orchestrator.api.FilesProviderRetrieveRequest,
    ): dk.sdu.cloud.file.orchestrator.api.PartialUFile
    
    
    abstract fun move(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderMoveRequestItem>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.file.orchestrator.api.LongRunningTask>
    
    
    abstract fun copy(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderCopyRequestItem>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.file.orchestrator.api.LongRunningTask>
    
    
    abstract fun createFolder(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderCreateFolderRequestItem>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.file.orchestrator.api.LongRunningTask>
    
    
    abstract fun trash(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderTrashRequestItem>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.file.orchestrator.api.LongRunningTask>
    
    
    abstract fun emptyTrash(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderEmptyTrashRequestItem>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.file.orchestrator.api.LongRunningTask>
    
    
    abstract fun createUpload(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderCreateUploadRequestItem>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.file.orchestrator.api.FilesCreateUploadResponseItem>
    
    
    abstract fun createDownload(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderCreateDownloadRequestItem>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.file.orchestrator.api.FilesCreateDownloadResponseItem>
    
    
    abstract fun search(
        request: dk.sdu.cloud.file.orchestrator.api.FilesProviderSearchRequest,
    ): dk.sdu.cloud.PageV2<dk.sdu.cloud.file.orchestrator.api.PartialUFile>
    
    
    abstract fun delete(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.UFile>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    abstract fun create(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.UFile>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.FindByStringId>
    
    
    /**
     * Retrieve product support for this providers
     *
     * This endpoint responds with the #TYPEREF#= dk.sdu.cloud.accounting.api.Product s supported by
     * this provider along with details for how #TYPEREF#= dk.sdu.cloud.accounting.api.Product is
     * supported. The #TYPEREF#= dk.sdu.cloud.accounting.api.Product s must be registered with
     * UCloud/Core already.
     */
    abstract fun retrieveProducts(
        request: kotlin.Unit,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.file.orchestrator.api.FSSupport>
    
    
    /**
     * Callback received by the Provider when permissions are updated
     *
     * This endpoint is mandatory for Providers to implement. If the Provider does not need to keep
     * internal state, then they may simply ignore this request by responding with `200 OK`. The
     * Provider _MUST_ reply with an OK status. UCloud/Core will fail the request if the Provider does
     * not acknowledge the request.
     */
    abstract fun updateAcl(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.file.orchestrator.api.UFile>>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    /**
     * Invoked by UCloud/Core to trigger verification of a single batch
     *
     * This endpoint is periodically invoked by UCloud/Core for resources which are deemed active. The
     * Provider should immediately determine if these are still valid and recognized by the Provider.
     * If any of the resources are not valid, then the Provider should notify UCloud/Core by issuing
     * an update for each affected resource.
     */
    abstract fun verify(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.UFile>,
    ): kotlin.Unit
    
    
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, S : Any, E : Any> dispatchToHandler(
        call: CallDescription<R, S, E>,
        request: R,
        rawRequest: HttpServletRequest,
        rawResponse: HttpServletResponse,
    ): S {
        return when (call.fullName.replace(providerId, "*")) {
            "files.provider.*.browse" -> browse(request as dk.sdu.cloud.file.orchestrator.api.FilesProviderBrowseRequest) as S
            "files.provider.*.retrieve" -> retrieve(request as dk.sdu.cloud.file.orchestrator.api.FilesProviderRetrieveRequest) as S
            "files.provider.*.move" -> move(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderMoveRequestItem>) as S
            "files.provider.*.copy" -> copy(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderCopyRequestItem>) as S
            "files.provider.*.createFolder" -> createFolder(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderCreateFolderRequestItem>) as S
            "files.provider.*.trash" -> trash(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderTrashRequestItem>) as S
            "files.provider.*.emptyTrash" -> emptyTrash(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderEmptyTrashRequestItem>) as S
            "files.provider.*.createUpload" -> createUpload(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderCreateUploadRequestItem>) as S
            "files.provider.*.createDownload" -> createDownload(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.FilesProviderCreateDownloadRequestItem>) as S
            "files.provider.*.search" -> search(request as dk.sdu.cloud.file.orchestrator.api.FilesProviderSearchRequest) as S
            "files.provider.*.delete" -> delete(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.UFile>) as S
            "files.provider.*.create" -> create(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.UFile>) as S
            "files.provider.*.retrieveProducts" -> retrieveProducts(request as kotlin.Unit) as S
            "files.provider.*.updateAcl" -> updateAcl(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.file.orchestrator.api.UFile>>) as S
            "files.provider.*.verify" -> verify(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.UFile>) as S
            else -> error("Unhandled call")
        }
    }
    
    override fun canHandleWebSocketCall(call: CallDescription<*, *, *>): Boolean {
        return false
    }
    
    override fun <R : Any, S : Any, E : Any> dispatchToWebSocketHandler(
        ctx: UCloudWsContext<R, S, E>,
        request: R,
    ) {
        when (ctx.call.fullName.replace(providerId, "*")) {
            else -> error("Unhandled call")
        }
    }
    
}

@RequestMapping(
    "/ucloud/chunked",
)
abstract class ChunkedUploadProtocolController(
    private val providerId: String,
    wsDispatcher: UCloudWsDispatcher,
): UCloudRpcDispatcher(dk.sdu.cloud.file.orchestrator.api.ChunkedUploadProtocol(providerId, "/ucloud/chunked"), wsDispatcher) {
    /**
     * Uploads a new chunk to the file at a given offset
     *
     * Uploads a new chunk to a file, specified by an upload session token. An upload session token can be
     * created using the #CALLREF#= files.createUpload call.
     * 
     * A session MUST be live for at least 30 minutes after the last `uploadChunk`
     * call was active. That is, since the last byte was transferred to this session or processed by the
     * provider. It is recommended that a provider keep a session for up to 48 hours. A session SHOULD NOT be
     * kept alive for longer than 48 hours.
     * 
     * This call MUST add the HTTP request body to the file, backed by the session, at the specified offset.
     * Clients may use the special offset '-1' to indicate that the payload SHOULD be appended to the file.
     * Providers MUST NOT interpret the request body in any way, the payload is binary and SHOULD be written
     * to the file as is. Providers SHOULD reject offset values that don't fulfill one of the following
     * criteria:
     * 
     * - Is equal to -1
     * - Is a valid offset in the file
     * - Is equal to the file size + 1
     * 
     * Clients MUST send a chunk which is at most 32MB large (32,000,000 bytes). Clients MUST declare the size
     * of chunk by specifying the `Content-Length` header. Providers MUST reject values that are not valid or
     * are too large. Providers SHOULD assume that the `Content-Length` header is valid.
     * However, the providers MUST NOT wait indefinitely for all bytes to be delivered. A provider SHOULD
     * terminate a connection which has been idle for too long to avoid trivial DoS by specifying a large
     * `Content-Length` without sending any bytes.
     * 
     * If a chunk upload is terminated before it is finished then a provider SHOULD NOT delete the data
     * already written to the file. Clients SHOULD assume that the entire chunk has failed and SHOULD re-upload
     * the entire chunk.
     * 
     * Providers SHOULD NOT cache a chunk before writing the data to the FS. Data SHOULD be streamed
     * directly into the file.
     * 
     * Providers MUST NOT respond to this call before the data has been written to disk.
     * 
     * Clients SHOULD avoid sending multiple chunks at the same time. Providers are allowed to reject parallel
     * calls to this endpoint.
     */
    abstract fun uploadChunk(
        request: dk.sdu.cloud.file.orchestrator.api.ChunkedUploadProtocolUploadChunkRequest,
    ): kotlin.Unit
    
    
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, S : Any, E : Any> dispatchToHandler(
        call: CallDescription<R, S, E>,
        request: R,
        rawRequest: HttpServletRequest,
        rawResponse: HttpServletResponse,
    ): S {
        return when (call.fullName.replace(providerId, "*")) {
            "*.uploadChunk" -> uploadChunk(request as dk.sdu.cloud.file.orchestrator.api.ChunkedUploadProtocolUploadChunkRequest) as S
            else -> error("Unhandled call")
        }
    }
    
    override fun canHandleWebSocketCall(call: CallDescription<*, *, *>): Boolean {
        return false
    }
    
    override fun <R : Any, S : Any, E : Any> dispatchToWebSocketHandler(
        ctx: UCloudWsContext<R, S, E>,
        request: R,
    ) {
        when (ctx.call.fullName.replace(providerId, "*")) {
            else -> error("Unhandled call")
        }
    }
    
}

@RequestMapping(
    "/ucloud/*/shares/verify",
    "/ucloud/*/shares/retrieveProducts",
    "/ucloud/*/shares/updateAcl",
    "/ucloud/*/shares",
)
abstract class SharesController(
    private val providerId: String,
    wsDispatcher: UCloudWsDispatcher,
): UCloudRpcDispatcher(dk.sdu.cloud.file.orchestrator.api.SharesProvider(providerId), wsDispatcher) {
    abstract fun delete(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.Share>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    abstract fun create(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.Share>,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.FindByStringId>
    
    
    /**
     * Retrieve product support for this providers
     *
     * This endpoint responds with the #TYPEREF#= dk.sdu.cloud.accounting.api.Product s supported by
     * this provider along with details for how #TYPEREF#= dk.sdu.cloud.accounting.api.Product is
     * supported. The #TYPEREF#= dk.sdu.cloud.accounting.api.Product s must be registered with
     * UCloud/Core already.
     */
    abstract fun retrieveProducts(
        request: kotlin.Unit,
    ): dk.sdu.cloud.calls.BulkResponse<dk.sdu.cloud.file.orchestrator.api.ShareSupport>
    
    
    /**
     * Callback received by the Provider when permissions are updated
     *
     * This endpoint is mandatory for Providers to implement. If the Provider does not need to keep
     * internal state, then they may simply ignore this request by responding with `200 OK`. The
     * Provider _MUST_ reply with an OK status. UCloud/Core will fail the request if the Provider does
     * not acknowledge the request.
     */
    abstract fun updateAcl(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.file.orchestrator.api.Share>>,
    ): dk.sdu.cloud.calls.BulkResponse<kotlin.Unit>
    
    
    /**
     * Invoked by UCloud/Core to trigger verification of a single batch
     *
     * This endpoint is periodically invoked by UCloud/Core for resources which are deemed active. The
     * Provider should immediately determine if these are still valid and recognized by the Provider.
     * If any of the resources are not valid, then the Provider should notify UCloud/Core by issuing
     * an update for each affected resource.
     */
    abstract fun verify(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.Share>,
    ): kotlin.Unit
    
    
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, S : Any, E : Any> dispatchToHandler(
        call: CallDescription<R, S, E>,
        request: R,
        rawRequest: HttpServletRequest,
        rawResponse: HttpServletResponse,
    ): S {
        return when (call.fullName.replace(providerId, "*")) {
            "shares.provider.*.delete" -> delete(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.Share>) as S
            "shares.provider.*.create" -> create(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.Share>) as S
            "shares.provider.*.retrieveProducts" -> retrieveProducts(request as kotlin.Unit) as S
            "shares.provider.*.updateAcl" -> updateAcl(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.provider.api.UpdatedAclWithResource<dk.sdu.cloud.file.orchestrator.api.Share>>) as S
            "shares.provider.*.verify" -> verify(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.file.orchestrator.api.Share>) as S
            else -> error("Unhandled call")
        }
    }
    
    override fun canHandleWebSocketCall(call: CallDescription<*, *, *>): Boolean {
        return false
    }
    
    override fun <R : Any, S : Any, E : Any> dispatchToWebSocketHandler(
        ctx: UCloudWsContext<R, S, E>,
        request: R,
    ) {
        when (ctx.call.fullName.replace(providerId, "*")) {
            else -> error("Unhandled call")
        }
    }
    
}


