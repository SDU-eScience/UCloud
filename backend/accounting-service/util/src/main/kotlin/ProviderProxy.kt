package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.safeUsername
import kotlinx.coroutines.delay

sealed class ProductRefOrResource<Res : Resource<*, *>> {
    abstract val reference: ProductReference

    data class SomeResource<Res : Resource<*, *>>(val resource: Res) : ProductRefOrResource<Res>() {
        override val reference = resource.specification.product ?: error("Not handled")
    }

    data class SomeRef<Res : Resource<*, *>>(val ref: ProductReference) : ProductRefOrResource<Res>() {
        override val reference: ProductReference = ref
    }
}

typealias RequestWithRefOrResource<Req, Res> = Pair<Req, ProductRefOrResource<Res>>

abstract class BulkProxyInstructions<Comms : ProviderComms, Support : ProductSupport, Res : Resource<*, *>,
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

    open suspend fun afterCall(
        provider: String,
        resources: List<RequestWithRefOrResource<ApiRequest, Res>>,
        response: BulkResponse<ProviderResponse?>
    ) {}

    open suspend fun onFailure(
        provider: String,
        resources: List<RequestWithRefOrResource<ApiRequest, Res>>,
        cause: Throwable,
        mappedRequestIfAny: ProviderRequest?
    ) {}

    companion object {
        fun <Comms : ProviderComms, Support : ProductSupport, Res : Resource<*, Support>,
            ApiRequest : Any, ProviderRequest : Any, ProviderResponse : Any> pureProcedure(
            service: ResourceService<Res, *, *, *, *, *, Support, Comms>,
            retrieveCall: (comms: Comms) -> CallDescription<BulkRequest<ProviderRequest>, BulkResponse<ProviderResponse?>, CommonErrorMessage>,
            requestToId: (ApiRequest) -> String,
            resourceToRequest: (request: ApiRequest, resource: Res) -> ProviderRequest,
            verifyRequest: suspend (request: ApiRequest, res: ProductRefOrResource<Res>, support: Support) -> Unit = { _, _, _ -> },
            isUserRequest: Boolean = true
        ): BulkProxyInstructions<Comms, Support, Res, ApiRequest, BulkRequest<ProviderRequest>, ProviderResponse> {
            return object :
                BulkProxyInstructions<Comms, Support, Res, ApiRequest, BulkRequest<ProviderRequest>, ProviderResponse>() {
                override val isUserRequest: Boolean = isUserRequest

                override fun retrieveCall(comms: Comms) = retrieveCall(comms)

                override suspend fun verifyAndFetchResources(
                    actorAndProject: ActorAndProject,
                    request: BulkRequest<ApiRequest>
                ): List<RequestWithRefOrResource<ApiRequest, Res>> {
                    return request.items.zip(
                        service.retrieveBulk(
                            actorAndProject,
                            request.items.map { requestToId(it) },
                            listOf(Permission.EDIT)
                        ).map { ProductRefOrResource.SomeResource(it) }
                    )
                }

                override suspend fun verifyRequest(
                    request: ApiRequest,
                    res: ProductRefOrResource<Res>,
                    support: Support
                ) = verifyRequest(request, res, support)

                override suspend fun beforeCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<ApiRequest, Res>>
                ): BulkRequest<ProviderRequest> {
                    return BulkRequest(
                        resources.map {
                            resourceToRequest(
                                it.first,
                                (it.second as ProductRefOrResource.SomeResource).resource
                            )
                        }
                    )
                }

                override suspend fun afterCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<ApiRequest, Res>>,
                    response: BulkResponse<ProviderResponse?>
                ) {
                    // Do nothing
                }

                override suspend fun onFailure(
                    provider: String,
                    resources: List<RequestWithRefOrResource<ApiRequest, Res>>,
                    cause: Throwable,
                    mappedRequestIfAny: BulkRequest<ProviderRequest>?
                ) {
                    // Do nothing
                }
            }
        }
    }
}

abstract class ProxyInstructions<Comms : ProviderComms, Support : ProductSupport, Res : Resource<*, *>,
    ApiRequest : Any, ProviderRequest : Any, ProviderResponse : Any> {
    abstract val isUserRequest: Boolean

    abstract fun retrieveCall(comms: Comms): CallDescription<ProviderRequest, ProviderResponse, CommonErrorMessage>

    abstract suspend fun verifyAndFetchResources(
        actorAndProject: ActorAndProject,
        request: ApiRequest
    ): RequestWithRefOrResource<ApiRequest, Res>

    open suspend fun verifyRequest(request: ApiRequest, res: ProductRefOrResource<Res>, support: Support) {}

    abstract suspend fun beforeCall(
        provider: String,
        resource: RequestWithRefOrResource<ApiRequest, Res>
    ): ProviderRequest

    open suspend fun afterCall(
        provider: String,
        resource: RequestWithRefOrResource<ApiRequest, Res>,
        response: ProviderResponse?
    ) {}

    open suspend fun onFailure(
        provider: String,
        resources: RequestWithRefOrResource<ApiRequest, Res>,
        cause: Throwable,
        mappedRequestIfAny: ProviderRequest?
    ) {}
}

abstract class SubscriptionProxyInstructions<Comms : ProviderComms, Support : ProductSupport, Res : Resource<*, *>,
    ApiRequest : Any, ProviderRequest : Any, ProviderResponse : Any> {
    abstract val isUserRequest: Boolean

    abstract fun retrieveCall(comms: Comms): CallDescription<ProviderRequest, ProviderResponse, CommonErrorMessage>

    abstract suspend fun verifyAndFetchResources(
        actorAndProject: ActorAndProject,
        request: ApiRequest
    ): RequestWithRefOrResource<ApiRequest, Res>

    open suspend fun verifyRequest(request: ApiRequest, res: ProductRefOrResource<Res>, support: Support) {}

    abstract suspend fun beforeCall(
        provider: String,
        resource: RequestWithRefOrResource<ApiRequest, Res>
    ): ProviderRequest

    abstract suspend fun onMessage(
        provider: String,
        resource: RequestWithRefOrResource<ApiRequest, Res>,
        message: ProviderResponse
    )

    open suspend fun afterCall(
        provider: String,
        resource: RequestWithRefOrResource<ApiRequest, Res>,
        response: ProviderResponse?
    ) {}

    open suspend fun onFailure(
        provider: String,
        resources: RequestWithRefOrResource<ApiRequest, Res>,
        cause: Throwable,
        mappedRequestIfAny: ProviderRequest?
    ) {}
}

class ProviderProxy<
    Comms : ProviderComms,
    Prod : Product,
    Support : ProductSupport,
    Res : Resource<Prod, Support>>(
    private val providers: Providers<Comms>,
    private val support: ProviderSupport<Comms, Prod, Support>
) {
    suspend fun <R : Any, S : Any, R2 : Any, Res2 : Resource<*, *>> bulkProxy(
        actorAndProject: ActorAndProject,
        request: BulkRequest<R>,
        instructions: BulkProxyInstructions<Comms, Support, Res2, R, R2, S>
    ): BulkResponse<S?> {
        with(instructions) {
            if (request.items.isEmpty()) throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

            val allResources = verifyAndFetchResources(actorAndProject, request)
            if (allResources.isEmpty()) throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

            val groupedByProvider = allResources.groupBy {
                it.second.reference.provider
            }

            for ((provider, requestsAndResources) in groupedByProvider) {
                if (provider == Provider.UCLOUD_CORE_PROVIDER) continue
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
                val requestForProvider = beforeCall(provider, requestsAndResources)
                try {
                    if (provider == Provider.UCLOUD_CORE_PROVIDER) {
                        anySuccess = true
                        afterCall(provider, requestsAndResources, BulkResponse(requestsAndResources.map { null }))
                        continue
                    }

                    val comms = providers.prepareCommunication(provider)
                    val providerCall = retrieveCall(comms)
                    val im = IntegrationProvider(provider)
                    mappedRequest = requestForProvider
                    for (attempt in 0 until 5) {
                        val response = providerCall.call(
                            requestForProvider,
                            if (isUserRequest) {
                                comms.client.withProxyInfo(
                                    actorAndProject.actor.safeUsername(),
                                    actorAndProject.signedIntentFromUser
                                )
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

                                delay(200L + (attempt * 500))
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
                    val call = retrieveCall(providers.placeholderCommunication)
                    throw IllegalStateException("No success but also no error: ${call.fullName}")
                }
            }

            return BulkResponse(responses)
        }
    }

    suspend fun <R : Any, S : Any, E : Any> invokeCall(
        actorAndProject: ActorAndProject,
        isUserRequest: Boolean,
        requestForProvider: R,
        provider: String,
        useHttpClient: Boolean = true,
        call: (comms: Comms) -> CallDescription<R, S, E>,
    ): S {
        val comms = providers.prepareCommunication(provider)
        val im = IntegrationProvider(provider)
        val actualCall = call(comms)

        val baseClient = if (useHttpClient) {
            comms.client
        } else {
            comms.wsClient
        }

        for (attempt in 0 until 5) {
            val response = actualCall.call(
                requestForProvider,
                if (isUserRequest) {
                    baseClient.withProxyInfo(actorAndProject.actor.safeUsername(), actorAndProject.signedIntentFromUser)
                } else {
                    baseClient
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

                    delay(200L + (attempt * 500))
                    continue
                } else {
                    response.throwError()
                }
            }

            return response.orThrow()
        }

        throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
    }

    suspend fun <R : Any, S : Any, R2 : Any> proxy(
        actorAndProject: ActorAndProject,
        request: R,
        instructions: ProxyInstructions<Comms, Support, Res, R, R2, S>,
        useHttpClient: Boolean = true,
    ): S {
        var mappedRequest: R2? = null
        with(instructions) {
            val reqWithResource = verifyAndFetchResources(actorAndProject, request)
            val (_, support) = support.retrieveProductSupport(reqWithResource.second.reference)
            verifyRequest(reqWithResource.first, reqWithResource.second, support)
            val provider = reqWithResource.second.reference.provider

            try {
                val comms = providers.prepareCommunication(provider)
                val providerCall = retrieveCall(comms)
                val requestForProvider = beforeCall(provider, reqWithResource)
                mappedRequest = requestForProvider

                if (provider == Provider.UCLOUD_CORE_PROVIDER) {
                    afterCall(provider, reqWithResource, null)
                } else {
                    val resp = invokeCall(actorAndProject, isUserRequest, requestForProvider, provider, useHttpClient) {
                        retrieveCall(it)
                    }
                    afterCall(provider, reqWithResource, resp)
                    return resp
                }
            } catch (ex: Throwable) {
                onFailure(provider, reqWithResource, ex, mappedRequest)
                throw ex
            }
        }

        throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
    }

    suspend fun <R : Any, S : Any, R2 : Any> proxySubscription(
        actorAndProject: ActorAndProject,
        request: R,
        instructions: SubscriptionProxyInstructions<Comms, Support, Res, R, R2, S>
    ): S {
        var mappedRequest: R2? = null
        with(instructions) {
            val reqWithResource = verifyAndFetchResources(actorAndProject, request)
            val (_, support) = support.retrieveProductSupport(reqWithResource.second.reference)
            verifyRequest(reqWithResource.first, reqWithResource.second, support)
            val provider = reqWithResource.second.reference.provider

            try {
                val comms = providers.prepareCommunication(provider)
                val providerCall = retrieveCall(comms)
                val im = IntegrationProvider(provider)
                val requestForProvider = beforeCall(provider, reqWithResource)
                mappedRequest = requestForProvider

                for (attempt in 0 until 5) {
                    if (provider == Provider.UCLOUD_CORE_PROVIDER) {
                        afterCall(provider, reqWithResource, null)
                        break
                    }

                    val response = providerCall.subscribe(
                        requestForProvider,
                        if (isUserRequest) {
                            comms.wsClient.withProxyInfo(
                                actorAndProject.actor.safeUsername(),
                                actorAndProject.signedIntentFromUser
                            )
                        } else {
                            comms.wsClient
                        },
                        handler = { message ->
                            onMessage(provider, reqWithResource, message)
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

                            delay(200L + (attempt * 500))
                            continue
                        } else {
                            response.throwError()
                        }
                    }

                    val capturedResponse = response.orThrow()
                    afterCall(provider, reqWithResource, capturedResponse)
                    return capturedResponse
                }
            } catch (ex: Throwable) {
                onFailure(provider, reqWithResource, ex, mappedRequest)
                throw ex
            }
        }

        throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
    }

    suspend fun <R : Any, S : Any> pureProxy(
        actorAndProject: ActorAndProject,
        productRef: ProductReference,
        call: (comms: Comms) -> CallDescription<R, S, CommonErrorMessage>,
        request: R,
        isUserRequest: Boolean = true
    ): S {
        return proxy(
            actorAndProject,
            Unit,
            object : ProxyInstructions<Comms, Support, Res, Unit, R, S>() {
                override val isUserRequest: Boolean = isUserRequest

                override suspend fun beforeCall(provider: String, resource: RequestWithRefOrResource<Unit, Res>): R {
                    return request
                }

                override fun retrieveCall(comms: Comms): CallDescription<R, S, CommonErrorMessage> = call(comms)

                override suspend fun verifyAndFetchResources(
                    actorAndProject: ActorAndProject,
                    request: Unit
                ): RequestWithRefOrResource<Unit, Res> {
                    return request to ProductRefOrResource.SomeRef(productRef)
                }
            }
        )
    }
}

private val HttpStatusCode.Companion.RetryWith get() = HttpStatusCode(449, "Retry With")

suspend fun <R : Any, S : Any, E : Any, C : ProviderComms> Providers<C>.invokeCall(
    provider: String,
    actorAndProject: ActorAndProject,
    call: (comms: C) -> CallDescription<R, S, E>,

    requestForProvider: R,

    // NOTE(Dan): See ctx.signedIntent from the end-user request. This should only be passed if `isUserRequest = true`.
    signedIntentFromEndUser: String?,
    isUserRequest: Boolean = true,
    useHttpClient: Boolean = true,
): S {
    val comms = prepareCommunication(provider)
    val im = IntegrationProvider(provider)
    val actualCall = call(comms)

    val baseClient = if (useHttpClient) {
        comms.client
    } else {
        comms.wsClient
    }

    for (attempt in 0 until 5) {
        val response = actualCall.call(
            requestForProvider,
            if (isUserRequest) {
                baseClient.withProxyInfo(actorAndProject.actor.safeUsername(), signedIntentFromEndUser)
            } else {
                baseClient
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

                delay(200L + (attempt * 500))
                continue
            } else {
                response.throwError()
            }
        }

        return response.orThrow()
    }

    throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
}

// TODO(Dan): Too much copy&pasting going on in this file
suspend fun <R : Any, S : Any, E : Any, C : ProviderComms> Providers<C>.invokeSubscription(
    provider: String,
    actorAndProject: ActorAndProject,
    call: (comms: C) -> CallDescription<R, S, E>,

    requestForProvider: R,

    // NOTE(Dan): See ctx.signedIntent from the end-user request. This should only be passed if `isUserRequest = true`.
    signedIntentFromEndUser: String?,
    isUserRequest: Boolean = true,

    handler: suspend (result: S) -> Unit
): S {
    val comms = prepareCommunication(provider)
    val im = IntegrationProvider(provider)
    val actualCall = call(comms)

    val baseClient = comms.wsClient

    for (attempt in 0 until 5) {
        val response = actualCall.subscribe(
            requestForProvider,
            if (isUserRequest) {
                baseClient.withProxyInfo(actorAndProject.actor.safeUsername(), signedIntentFromEndUser)
            } else {
                baseClient
            },
            handler
        )

        if (response.statusCode == HttpStatusCode.RetryWith ||
            response.statusCode == HttpStatusCode.ServiceUnavailable
        ) {
            if (isUserRequest) {
                im.init.call(
                    IntegrationProviderInitRequest(actorAndProject.actor.safeUsername()),
                    comms.client
                ).orThrow()

                delay(200L + (attempt * 500))
                continue
            } else {
                response.throwError()
            }
        }

        return response.orThrow()
    }

    throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
}
