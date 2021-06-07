package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.throwError
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.safeUsername
import io.ktor.http.*
import kotlinx.coroutines.delay

sealed class ProductRefOrResource<Res : Resource<*>> {
    abstract val reference: ProductReference

    data class SomeResource<Res : Resource<*>>(val resource: Res) : ProductRefOrResource<Res>() {
        override val reference = resource.specification.product ?: error("Not handled")
    }

    data class SomeRef<Res : Resource<*>>(val ref: ProductReference) : ProductRefOrResource<Res>() {
        override val reference: ProductReference = ref
    }
}

typealias RequestWithRefOrResource<Req, Res> = Pair<Req, ProductRefOrResource<Res>>

abstract class BulkProxyInstructions<Comms : ProviderComms, Support : ProductSupport, Res : Resource<*>,
    ApiRequest : Any, ProviderRequest : Any, ProviderResponse : Any> {
    abstract val isUserRequest: Boolean

    abstract fun retrieveCall(
        comms: Comms
    ): CallDescription<ProviderRequest, BulkResponse<ProviderResponse?>, CommonErrorMessage>

    abstract suspend fun verifyAndFetchResources(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ApiRequest>
    ): List<RequestWithRefOrResource<ApiRequest, Res>>

    abstract suspend fun verifyRequest(request: ApiRequest, res: ProductRefOrResource<Res>, support: Support)

    abstract suspend fun beforeCall(
        provider: String,
        resources: List<RequestWithRefOrResource<ApiRequest, Res>>
    ): ProviderRequest

    abstract suspend fun afterCall(
        provider: String,
        resources: List<RequestWithRefOrResource<ApiRequest, Res>>,
        response: BulkResponse<ProviderResponse?>
    )

    abstract suspend fun onFailure(
        provider: String,
        resources: List<RequestWithRefOrResource<ApiRequest, Res>>,
        cause: Throwable,
        mappedRequestIfAny: ProviderRequest?
    )
}

class ProviderProxy<
    Comms : ProviderComms,
    Prod : Product,
    Support : ProductSupport,
    Res : Resource<*>>(
    private val providers: Providers<Comms>,
    private val support: ProviderSupport<Comms, Prod, Support>
) {
    suspend fun <R : Any, S : Any, R2 : Any> bulkProxy(
        actorAndProject: ActorAndProject,
        request: BulkRequest<R>,
        instructions: BulkProxyInstructions<Comms, Support, Res, R, R2, S>
    ): BulkResponse<S?> {
        with(instructions) {
            val call = retrieveCall(providers.placeholderCommunication)
            if (request.items.isEmpty()) throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

            val allResources = verifyAndFetchResources(actorAndProject, request)
            val groupedByProvider = allResources.groupBy {
                it.second.reference.provider
            }

            for ((_, requestsAndResources) in groupedByProvider) {
                for (req in requestsAndResources) {
                    val (_, support) = support.retrieveProductSupport(req.second.reference)
                    verifyRequest(req.first, req.second, support)
                }
            }

            val responses = ArrayList<S?>()
            request.items.forEach { _ -> responses.add(null) }
            var lastError: Throwable? = null
            var anySuccess = false

            for ((provider, requestsAndResources) in groupedByProvider) {
                var mappedRequest: R2? = null
                try {
                    val comms = providers.prepareCommunication(provider)
                    val providerCall = retrieveCall(comms)
                    val im = IntegrationProvider(provider)
                    val requestForProvider = beforeCall(provider, requestsAndResources)
                    mappedRequest = requestForProvider

                    for (attempt in 0 until 5) {
                        if (provider == Provider.UCLOUD_CORE_PROVIDER) {
                            anySuccess = true
                            afterCall(provider, requestsAndResources, BulkResponse(requestsAndResources.map { null }))
                            break
                        }

                        val response = providerCall.call(
                            requestForProvider,
                            if (isUserRequest) {
                                comms.client.withProxyInfo(actorAndProject.actor.safeUsername())
                            } else {
                                comms.client
                            }
                        )

                        if (response.statusCode == HttpStatusCode.RetryWith ||
                            response.statusCode == HttpStatusCode.ServiceUnavailable
                        ) {
                            if (isUserRequest) {
                                im.init.call(
                                    IntegrationProviderInitRequest(actorAndProject.actor.safeUsername()),
                                    comms.client
                                ).orThrow()

                                delay(200)
                                continue
                            } else {
                                response.throwError()
                            }
                        }

                        val providerResponses = response.orThrow()
                        val indexes = requestsAndResources.map { request.items.indexOf(it.first) }
                        for ((i, resp) in indexes.zip(providerResponses.responses)) {
                            responses[i] = resp
                        }
                        afterCall(provider, requestsAndResources, providerResponses)
                        anySuccess = true
                        break
                    }
                } catch (ex: Throwable) {
                    lastError = ex
                    onFailure(provider, requestsAndResources, ex, mappedRequest)
                }
            }

            if (!anySuccess) {
                if (lastError != null) {
                    throw lastError
                } else {
                    throw IllegalStateException("No success but also no error: ${call.fullName}")
                }
            }

            return BulkResponse(responses)
        }
    }

    /*
    suspend fun <R : Any, S : Any, E : Any> proxy(
        callFn: (Comms) -> CallDescription<R, S, E>,
        actorAndProject: ActorAndProject,
        request: R,
    ): S {
        val call = callFn(providers.placeholderCommunication)

        @Suppress("UNCHECKED_CAST")
        val proxy = proxies[call.fullName] as ProxyInstructions<R, S, E, Support, Res>?
            ?: throw IllegalStateException("Unknown call: ${call.fullName}")

        val resource = proxy.verifyAndFetchResources(actorAndProject, request)
        val (product, support) = support.retrieveProductSupport(
            resource.specification.product ?: throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
        )

        proxy.verifyRequest(request, support)

        val comms = providers.prepareCommunication(product.category.provider)
        val im = IntegrationProvider(product.category.provider)

        for (attempt in 0 until 5) {
            val response = callFn(comms).call(
                request,
                if (proxy.isUserRequest) {
                    comms.client.withProxyInfo(actorAndProject.actor.safeUsername())
                } else {
                    comms.client
                }
            )

            if (response.statusCode == HttpStatusCode.RetryWith ||
                response.statusCode == HttpStatusCode.ServiceUnavailable
            ) {
                if (proxy.isUserRequest) {
                    im.init.call(
                        IntegrationProviderInitRequest(actorAndProject.actor.safeUsername()),
                        comms.client
                    ).orThrow()

                    delay(200)
                    continue
                } else {
                    response.throwError()
                }
            }
            return response.orThrow()
        }

        throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
    }

    suspend fun <R : Any, S : Any, E : Any> proxySubscription(
        callFn: (Comms) -> CallDescription<R, S, E>,
        actorAndProject: ActorAndProject,
        request: R,
        handler: suspend (S) -> Unit
    ): S {
        val call = callFn(providers.placeholderCommunication)

        @Suppress("UNCHECKED_CAST")
        val proxy = proxies[call.fullName] as ProxyInstructions<R, S, E, Support, Res>?
            ?: throw IllegalStateException("Unknown call: ${call.fullName}")

        val resource = proxy.verifyAndFetchResources(actorAndProject, request)
        val (product, support) = support.retrieveProductSupport(
            resource.specification.product ?: throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
        )

        proxy.verifyRequest(request, support)

        val comms = providers.prepareCommunication(product.category.provider)
        val im = IntegrationProvider(product.category.provider)

        for (attempt in 0 until 5) {
            val response = callFn(comms).subscribe(
                request,
                if (proxy.isUserRequest) {
                    comms.client.withProxyInfo(actorAndProject.actor.safeUsername())
                } else {
                    comms.client
                },
                handler
            )

            if (response.statusCode == HttpStatusCode.RetryWith ||
                response.statusCode == HttpStatusCode.ServiceUnavailable
            ) {
                if (proxy.isUserRequest) {
                    im.init.call(
                        IntegrationProviderInitRequest(actorAndProject.actor.safeUsername()),
                        comms.client
                    ).orThrow()

                    delay(200)
                    continue
                } else {
                    response.throwError()
                }
            }
            return response.orThrow()
        }

        throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
    }

     */
}

private val HttpStatusCode.Companion.RetryWith get() = HttpStatusCode(449, "Retry With")
