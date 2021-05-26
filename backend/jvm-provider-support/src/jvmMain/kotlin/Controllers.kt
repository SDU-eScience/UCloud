@file:Suppress("RemoveRedundantQualifierName", "RedundantUnitReturnType", "unused", "UNREACHABLE_CODE", "UNCHECKED_CAST")
package dk.sdu.cloud.providers

/* AUTO GENERATED CODE - DO NOT MODIFY */
/* Generated at: Tue May 25 13:07:01 CEST 2021 */


import dk.sdu.cloud.providers.UCloudRpcDispatcher
import dk.sdu.cloud.providers.UCloudWsDispatcher
import dk.sdu.cloud.providers.UCloudWsContext
import dk.sdu.cloud.calls.CallDescription
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 
 * The calls described in this section covers the API that providers of compute must implement. Not all
 * features of the compute API must be implemented. The individual calls and types will describe how the manifest
 * affects them.
 *             
 * Compute providers must answer to the calls listed below. Providers should take care to verify the bearer
 * token according to the TODO documentation.
 *             
 * The provider and UCloud works in tandem by sending pushing information to each other when new information
 * becomes available. Compute providers can push information to UCloud by using the
 * [`jobs.control`](#tag/jobs.control) API.
 * 
 * ### What information does `Job` include?
 * 
 * The UCloud API will communicate with the provider and include a reference of the `Job` which the request is about. The
 * `Job` model has several optional fields which are not always included. You can see which flags are set by UCloud when
 * retrieving the `Job`. If you need additional data you may use [`jobs.control.retrieve`](#operation/jobs.control.retrieve) to fetch additional
 * information about the job. The flags selected below should give the provider enough information that the rest can
 * easily be cached locally. For example, providers can with great benefit choose to cache product and application
 * information.
 * 
 * | Flag | Included | Comment |
 * |------|----------|---------|
 * | `includeParameters` | `true` | Specifies how the user invoked the application. |
 * | `includeApplication` | `false` | Application information specifies the tool and application running. Can safely be cached indefinitely by name and version. |
 * | `includeProduct` | `false` | Product information specifies dimensions of the machine. Can safely be cached for 24 hours by name. |
 * | `includeUpdates` | `false` | You, the provider, will have supplied all updates but they are stored by UCloud. |
 * | `includeWeb` | `false` | You, the provider, will supply this information. Asking would cause UCloud to ask you back. |
 * | `includeVnc` | `false` | You, the provider, will supply this information. Asking would cause UCloud to ask you back. |
 * | `includeShell` | `false` |  You, the provider, will supply this information. Asking would cause UCloud to ask you back. |
 *             
 * ### Accounting
 *             
 * It is up to the provider how accounting is done and if they wish to push accounting information to UCloud. 
 * A provider might, for example, choose to do all of the accounting on their own (including tracking who has
 * access). This would allow a provider to use UCloud just as an interface.
 *            
 * If a provider wishes to use UCloud for accounting then this is possible. UCloud provides an API which 
 * allows the provider to charge for a running compute job. The provider may call this API repeatedly to 
 * charge the user for their job. UCloud will respond with a payment required if the user's wallet
 * is out of credits. This indicates to the compute provider that the job should be terminated (since they 
 * no longer have credit for the job).
 *  
 * ### Example: Complete example with accounting
 *             
 * | ID | UCloud | - | Provider | Call | Message |
 * |----|--------|---|----------|------|---------|
 * | [1] Request | UCloud | → | Provider | [`create`](#operation/jobs.compute.PROVIDERID.create) | Start application with ID `FOO123` |
 * | [1] Response | UCloud | ← | Provider | [`create`](#operation/jobs.compute.PROVIDERID.create) | OK |
 * | [2] Request | UCloud | ← | Provider | [`jobs.control.update`](#operation/jobs.control.update) | Proceed to `RUNNING` |
 * | [3] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](#operation/jobs.control.chargeCredits) | Charge for 15 minutes of use |
 * | [4] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](#operation/jobs.control.chargeCredits) | Charge for 15 minutes of use |
 * | [5] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](#operation/jobs.control.chargeCredits) | Charge for 15 minutes of use |
 * | [6] Request | UCloud | → | Provider | [`delete`](#operation/jobs.compute.PROVIDERID.delete) | Delete `FOO123` |
 * | [7] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](#operation/jobs.control.chargeCredits) | Charge for 3 minutes of use |
 * | [8] Request | UCloud | ← | Provider | [`jobs.control.update`](#operation/jobs.control.update) | Proceed to `SUCCESS` |
 * 
 * ### Example: Missing credits
 *             
 * | ID | UCloud | - | Provider | Call | Message |
 * |----|--------|---|----------|------|---------|
 * | [1] Request | UCloud | → | Provider | [`create`](#operation/jobs.compute.PROVIDERID.create) | Start application with ID `FOO123` |
 * | [1] Response | UCloud | ← | Provider | [`create`](#operation/jobs.compute.PROVIDERID.create) | OK |
 * | [2] Request | UCloud | ← | Provider | [`jobs.control.update`](#operation/jobs.control.update) | Proceed to `RUNNING` |
 * | [3] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](#operation/jobs.control.chargeCredits) | Charge for 15 minutes of use |
 * | [3] Response | UCloud | → | Provider | [`jobs.control.chargeCredits`](#operation/jobs.control.chargeCredits) | 402 Payment Required |
 * | [4] Request | UCloud | ← | Provider | [`jobs.control.update`](#operation/jobs.control.update) | Proceed to `SUCCESS` with message 'Insufficient funds' |
 * 
 * ### Example: UCloud and provider out-of-sync
 * 
 * | ID | UCloud | - | Provider | Call | Message |
 * |----|--------|---|----------|------|---------|
 * | [1] Request | UCloud | → | Provider | [`create`](#operation/jobs.compute.PROVIDERID.create) | Start application with ID `FOO123` |
 * | [1] Response | UCloud | ← | Provider | [`create`](#operation/jobs.compute.PROVIDERID.create) | OK |
 * | [2] Request | UCloud | ← | Provider | [`jobs.control.update`](#operation/jobs.control.update) | Proceed to `RUNNING` |
 * | [3] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](#operation/jobs.control.chargeCredits) | Charge for 15 minutes of use |
 * | [3] Response | UCloud | → | Provider | [`jobs.control.chargeCredits`](#operation/jobs.control.chargeCredits) | 402 Payment Required |           
 * | [3] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](#operation/jobs.control.chargeCredits) | Charge for 15 minutes of use |
 * | [3] Response | UCloud | → | Provider | [`jobs.control.chargeCredits`](#operation/jobs.control.chargeCredits) | 402 Payment Required |           
 * | [4] Comment | | | | | `FOO123` disappears/crashes at provider - Provider did not notice and notify UCloud automatically |
 * | [5] Request | UCloud | → | Provider | [`verify`](#operation/jobs.compute.PROVIDERID.verify) | Verify that `FOO123` is running |
 * | [5] Response | UCloud | ← | Provider | [`verify`](#operation/jobs.compute.PROVIDERID.verify) | OK |
 * | [6] Request | UCloud | → | Provider | [`jobs.control.update`](#operation/jobs.control.update) | Proceed `FOO123` to `FAILURE` |
 * 
 */
@RequestMapping(
    "/ucloud/*/jobs/retrieveProducts",
    "/ucloud/*/jobs/verify",
    "/ucloud/*/jobs/suspend",
    "/ucloud/*/jobs/extend",
    "/ucloud/*/jobs/interactiveSession",
    "/ucloud/*/jobs/retrieveUtilization",
    "/ucloud/*/jobs",
)
abstract class JobsController(
    private val providerId: String,
    wsDispatcher: UCloudWsDispatcher,
): UCloudRpcDispatcher(dk.sdu.cloud.app.orchestrator.api.JobsProvider(providerId), wsDispatcher) {
    /**
     * Start a compute job
     *
     * Starts one or more compute jobs. The jobs have already been verified by UCloud and it is assumed to be
     * ready for the provider. The provider can choose to reject the entire batch by responding with a 4XX or
     * 5XX status code. Note that the batch must be handled as a single transaction.
     * 
     * The provider should respond to this request as soon as the jobs have been scheduled. The provider should
     * then switch to [`jobs.control.update`](#operation/jobs.control.update) in order to provide updates about the progress.
     */
    abstract fun create(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>,
    ): kotlin.Unit
    
    /**
     * Request job cancellation and destruction
     *
     * Deletes one or more compute jobs. The provider should not only stop the compute job but also delete
     * _compute_ related resources. For example, if the job is a virtual machine job, the underlying machine
     * should also be deleted. None of the resources attached to the job, however, should be deleted.
     */
    abstract fun delete(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>,
    ): kotlin.Unit
    
    /**
     * Extend the duration of a job
     *
     */
    abstract fun extend(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.JobsProviderExtendRequestItem>,
    ): kotlin.Unit
    
    /**
     * Suspend a job
     *
     */
    abstract fun suspend(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>,
    ): kotlin.Unit
    
    /**
     * Verify UCloud data is synchronized with provider
     *
     * This call is periodically executed by UCloud against all active providers. It is the job of the
     * provider to ensure that the jobs listed in the request are in its local database. If some of the
     * jobs are not in the provider's database then this should be treated as a job which is no longer valid.
     * The compute backend should trigger normal cleanup code and notify UCloud about the job's termination.
     * 
     * The backend should _not_ attempt to start the job.
     */
    abstract fun verify(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>,
    ): kotlin.Unit
    
    abstract fun follow(
        request: dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowRequest,
        wsContext: UCloudWsContext<dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowRequest, dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowResponse, dk.sdu.cloud.CommonErrorMessage>,
    )
    
    abstract fun openInteractiveSession(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.JobsProviderOpenInteractiveSessionRequestItem>,
    ): dk.sdu.cloud.app.orchestrator.api.JobsProviderOpenInteractiveSessionResponse
    
    abstract fun retrieveUtilization(
        request: kotlin.Unit,
    ): dk.sdu.cloud.app.orchestrator.api.JobsProviderUtilizationResponse
    
    /**
     * Retrieve products
     *
     * An API for retrieving the products and the support from a provider.
     */
    abstract fun retrieveProducts(
        request: kotlin.Unit,
    ): dk.sdu.cloud.app.orchestrator.api.JobsProviderRetrieveProductsResponse
    
    
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, S : Any, E : Any> dispatchToHandler(
        call: CallDescription<R, S, E>,
        request: R,
        rawRequest: HttpServletRequest,
        rawResponse: HttpServletResponse,
    ): S {
        return when (call.fullName.replace(providerId, "*")) {
            "jobs.compute.*.create" -> create(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>) as S
            "jobs.compute.*.delete" -> delete(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>) as S
            "jobs.compute.*.extend" -> extend(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.JobsProviderExtendRequestItem>) as S
            "jobs.compute.*.suspend" -> suspend(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>) as S
            "jobs.compute.*.verify" -> verify(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Job>) as S
            "jobs.compute.*.openInteractiveSession" -> openInteractiveSession(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.JobsProviderOpenInteractiveSessionRequestItem>) as S
            "jobs.compute.*.retrieveUtilization" -> retrieveUtilization(request as kotlin.Unit) as S
            "jobs.compute.*.retrieveProducts" -> retrieveProducts(request as kotlin.Unit) as S
            else -> error("Unhandled call")
        }
    }
    
    override fun canHandleWebSocketCall(call: CallDescription<*, *, *>): Boolean {
        if (call.fullName.replace(providerId, "*") == "jobs.compute.*.follow") return true
        return false
    }
    
    override fun <R : Any, S : Any, E : Any> dispatchToWebSocketHandler(
        ctx: UCloudWsContext<R, S, E>,
        request: R,
    ) {
        when (ctx.call.fullName.replace(providerId, "*")) {
            "jobs.compute.*.follow" -> follow(request as dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowRequest, ctx as UCloudWsContext<dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowRequest, dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowResponse, dk.sdu.cloud.CommonErrorMessage>)
            else -> error("Unhandled call")
        }
    }
    
}


@RequestMapping(
    "/ucloud/*/networkips",
    "/ucloud/*/networkips/verify",
    "/ucloud/*/networkips/firewall",
)
abstract class NetworkIPController(
    private val providerId: String,
    wsDispatcher: UCloudWsDispatcher,
): UCloudRpcDispatcher(dk.sdu.cloud.app.orchestrator.api.NetworkIPProvider(providerId), wsDispatcher) {
    abstract fun create(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.NetworkIP>,
    ): kotlin.Unit
    
    abstract fun delete(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.NetworkIP>,
    ): kotlin.Unit
    
    abstract fun verify(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.NetworkIP>,
    ): kotlin.Unit
    
    abstract fun updateFirewall(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.FirewallAndId>,
    ): kotlin.Unit
    
    
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, S : Any, E : Any> dispatchToHandler(
        call: CallDescription<R, S, E>,
        request: R,
        rawRequest: HttpServletRequest,
        rawResponse: HttpServletResponse,
    ): S {
        return when (call.fullName.replace(providerId, "*")) {
            "networkips.provider.*.create" -> create(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.NetworkIP>) as S
            "networkips.provider.*.delete" -> delete(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.NetworkIP>) as S
            "networkips.provider.*.verify" -> verify(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.NetworkIP>) as S
            "networkips.provider.*.updateFirewall" -> updateFirewall(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.FirewallAndId>) as S
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
    "/ucloud/*/ingresses/retrieveSettings",
    "/ucloud/*/ingresses/verify",
)
abstract class IngressController(
    private val providerId: String,
    wsDispatcher: UCloudWsDispatcher,
): UCloudRpcDispatcher(dk.sdu.cloud.app.orchestrator.api.IngressProvider(providerId), wsDispatcher) {
    abstract fun create(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Ingress>,
    ): kotlin.Unit
    
    abstract fun delete(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Ingress>,
    ): kotlin.Unit
    
    abstract fun verify(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Ingress>,
    ): kotlin.Unit
    
    abstract fun retrieveSettings(
        request: dk.sdu.cloud.accounting.api.ProductReference,
    ): dk.sdu.cloud.app.orchestrator.api.IngressSettings
    
    
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, S : Any, E : Any> dispatchToHandler(
        call: CallDescription<R, S, E>,
        request: R,
        rawRequest: HttpServletRequest,
        rawResponse: HttpServletResponse,
    ): S {
        return when (call.fullName.replace(providerId, "*")) {
            "ingresses.provider.*.create" -> create(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Ingress>) as S
            "ingresses.provider.*.delete" -> delete(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Ingress>) as S
            "ingresses.provider.*.verify" -> verify(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.Ingress>) as S
            "ingresses.provider.*.retrieveSettings" -> retrieveSettings(request as dk.sdu.cloud.accounting.api.ProductReference) as S
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


/**
 * Provides an API for providers to give shell access to their running compute jobs.
 */
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
    "/ucloud/*/licenses/verify",
)
abstract class LicenseController(
    private val providerId: String,
    wsDispatcher: UCloudWsDispatcher,
): UCloudRpcDispatcher(dk.sdu.cloud.app.orchestrator.api.LicenseProvider(providerId), wsDispatcher) {
    abstract fun create(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.License>,
    ): kotlin.Unit
    
    abstract fun delete(
        request: dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.License>,
    ): kotlin.Unit
    
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
            "licenses.provider.*.create" -> create(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.License>) as S
            "licenses.provider.*.delete" -> delete(request as dk.sdu.cloud.calls.BulkRequest<dk.sdu.cloud.app.orchestrator.api.License>) as S
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


