package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.accounting.util.SimpleProviderCommunication
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import java.util.ArrayList
import java.util.HashMap

typealias UnknownResourceApi<Support> = ResourceApi<*, *, *, *, *, *, Support>

class ProviderCommunications(
    private val backgroundScope: BackgroundScope,
    private val serviceClient: AuthenticatedClient,
    private val productCache: ProductCache,
) {
    private val providerSpec = AsyncCache<String, ProviderSpecification>(
        backgroundScope,
        timeToLiveMs = AsyncCache.DONT_EXPIRE,
        timeoutException = {
            throw RPCException("Failed to establish communication with $it", HttpStatusCode.BadGateway)
        },
        retrieve = { providerId ->
            Providers.retrieveSpecification.call(
                ProvidersRetrieveSpecificationRequest(providerId),
                serviceClient
            ).orThrow()
        }
    )

    private val communicationCache = AsyncCache<String, SimpleProviderCommunication>(
        backgroundScope,
        timeToLiveMs = AsyncCache.DONT_EXPIRE,
        timeoutException = {
            throw RPCException("Failed to establish communication with $it", HttpStatusCode.BadGateway)
        },
        retrieve = { providerId ->
            val auth = RefreshingJWTAuthenticator(
                rpcClient.client,
                JwtRefresher.ProviderOrchestrator(serviceClient, providerId)
            )

            val providerSpec = Providers.retrieveSpecification.call(
                ProvidersRetrieveSpecificationRequest(providerId),
                serviceClient
            ).orThrow()

            val hostInfo = providerSpec.toHostInfo()
            val httpClient = auth.authenticateClient(OutgoingHttpCall).withFixedHost(hostInfo)
            val wsClient = auth.authenticateClient(OutgoingWSCall).withFixedHost(hostInfo)

            SimpleProviderCommunication(httpClient, wsClient, providerSpec)
        }
    )

    data class SupportCacheKey(
        val api: UnknownResourceApi<*>,
        val providerId: String,
    )

    private val supportCache = AsyncCache<SupportCacheKey, List<ProductSupport>>(
        backgroundScope,
        timeToLiveMs = 1000L * 60 * 5,
        timeoutException = {
            throw RPCException("Timeout while waiting for ${it.providerId}", HttpStatusCode.BadGateway)
        },
        retrieve = { key ->
            val call = retrieveSupportCall(key.providerId, key.api.namespace, key.api.typeInfo.supportSerializer)

            invokeCall(
                key.providerId,
                ActorAndProject(Actor.System, null),
                { call },
                Unit,
                isUserRequest = false,
            ).responses
        }
    )

    suspend fun <Support : ProductSupport> fetchSupport(
        api: UnknownResourceApi<Support>,
        providerId: String,
    ): List<Support> {
        val cacheKey = SupportCacheKey(api, providerId)
        @Suppress("UNCHECKED_CAST")
        return supportCache.retrieve(cacheKey) as List<Support>
    }

    private val rpcClient = serviceClient.withoutAuthentication()

    suspend fun <Support : ProductSupport> requireSupportAndCheckAllocations(
        actorAndProject: ActorAndProject,
        api: UnknownResourceApi<Support>,
        request: BulkRequest<ResourceSpecification>,
        actionDescription: String,
        validator: suspend (Support) -> Unit,
    ) {
        requireSupportAndCheckAllocations(
            actorAndProject,
            api,
            request.items.map { it.product },
            actionDescription,
            validator
        )
    }

    suspend fun <Support : ProductSupport> requireSupportAndCheckAllocations(
        actorAndProject: ActorAndProject,
        api: UnknownResourceApi<Support>,
        productReferences: Collection<ProductReference>,
        actionDescription: String,
        validator: suspend (Support) -> Unit,
    ) {
        requireAllocation(actorAndProject, productReferences, actionDescription)
        requireSupport(api, productReferences, actionDescription, validator)
    }

    suspend fun <Support : ProductSupport> requireSupportProductIds(
        api: UnknownResourceApi<Support>,
        productIds: Collection<Int>,
        actionDescription: String,
        validator: suspend (Support) -> Unit
    ) {
        val productReferences = productIds.map {
            productCache.productIdToReference(it)
                ?: throw RPCException("Unknown product supplied", HttpStatusCode.InternalServerError)
        }

        requireSupport(api, productReferences, actionDescription, validator)
    }

    suspend fun <Support : ProductSupport> requireSupport(
        api: UnknownResourceApi<Support>,
        productReferences: Collection<ProductReference>,
        actionDescription: String,
        validator: suspend (Support) -> Unit
    ) {
        val providerIds = productReferences.map { it.provider }.toSet()

        for (provider in providerIds) {
            val support = fetchSupport(api, provider)
            var found = false
            for (ref in productReferences) {
                if (ref.provider != provider) continue

                found = true
                val supportForProduct = support.find { it.product == ref } ?: throw RPCException(
                    "$ref is currently offline. UCloud received no response from ${ref.provider}. Try again later.",
                    HttpStatusCode.BadGateway
                )

                if (runCatching { validator(supportForProduct) }.isFailure) {
                    throw RPCException(
                        "Failure while $actionDescription. This operation is not supported by the provider.",
                        HttpStatusCode.BadRequest
                    )
                }
            }

            if (!found) {
                throw RPCException(
                    "${provider} is currently offline. UCloud received no response. Try again later.",
                    HttpStatusCode.BadGateway
                )
            }
        }
    }

    private val allocationCache = AsyncCache<Pair<WalletOwner, ProductCategoryId>, Boolean>(
        backgroundScope,
        timeToLiveMs = 1000L * 60 * 30,
        timeoutException = { throw RPCException("Failed to fetch information about allocations", HttpStatusCode.BadGateway) },
        retrieve = { (owner, category) ->
            Wallets.retrieveAllocationsInternal
                .call(
                    WalletAllocationsInternalRetrieveRequest(owner, category),
                    serviceClient
                )
                .orThrow()
                .allocations
                .any { Time.now() in (it.startDate..(it.endDate ?: Long.MAX_VALUE)) }
        }
    )

    suspend fun requireAllocationProductIds(
        actorAndProject: ActorAndProject,
        products: Set<Int>,
        actionDescription: String
    ) {
        if (actorAndProject.actor == Actor.System) return

        val resolvedProducts = products.map {
            productCache.productIdToProduct(it)
                ?: throw RPCException("Unknown product supplied", HttpStatusCode.InternalServerError)
        }.toSet()

        requireAllocationProducts(actorAndProject, resolvedProducts, actionDescription)
    }

    suspend fun requireAllocation(
        actorAndProject: ActorAndProject,
        products: Collection<ProductReference>,
        actionDescription: String,
    ) {
        if (actorAndProject.actor == Actor.System) return

        val resolvedProducts = products.toSet().map {
            productCache.referenceToProductId(it)?.let { id ->
                productCache.productIdToProduct(id)
            } ?: throw RPCException("Unknown product supplied", HttpStatusCode.InternalServerError)
        }.toSet()

        requireAllocationProducts(actorAndProject, resolvedProducts, actionDescription)
    }

    suspend fun requireAllocationProducts(
        actorAndProject: ActorAndProject,
        resolvedProducts: Collection<Product>,
        actionDescription: String,
    ) {
        if (actorAndProject.actor == Actor.System) return

        val walletOwner = if (actorAndProject.project != null) {
            WalletOwner.Project(actorAndProject.project!!)
        } else {
            WalletOwner.User(actorAndProject.actor.safeUsername())
        }

        for (product in resolvedProducts) {
            val category = product.category
            if (product.freeToUse) continue

            if (!allocationCache.checkOrRefresh(Pair(walletOwner, category))) {
                throw RPCException(
                    "Failure while $actionDescription. You do not have a valid allocation for $category.",
                    HttpStatusCode.PaymentRequired
                )
            }
        }
    }

    suspend fun retrieveProviderHostInfo(providerId: String): HostInfo {
        return providerSpec.retrieve(providerId).toHostInfo()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun forEachRelevantProvider(
        actorAndProject: ActorAndProject,
        deadlineMs: Long = 10_000L,
        fn: suspend (providerId: String) -> Unit,
    ) {
        val providers = Accounting.findRelevantProviders.call(
            bulkRequestOf(
                FindRelevantProvidersRequestItem(
                    actorAndProject.actor.safeUsername(),
                    useProject = false
                )
            ),
            serviceClient
        ).orRethrowAs {
            throw RPCException(
                "Could not find relevant providers (${it.statusCode} ${it.error?.why ?: ""})",
                HttpStatusCode.BadGateway
            )
        }.responses.single().providers

        coroutineScope {
            val jobs = providers.map { providerId ->
                launch {
                    try {
                        fn(providerId)
                    } catch (ex: Throwable) {
                        log.warn("Caught exception while broadcasting message to providers: " +
                                "${ex.toReadableStacktrace()}")
                    }
                }
            }

            val joinJob = launch { jobs.joinAll() }

            select {
                joinJob.onJoin { Unit }

                onTimeout(deadlineMs) {
                    log.warn("Deadline reached while broadcasting message to providers! ${providers}")
                    jobs.forEach { runCatching { it.cancel() } }
                    runCatching { joinJob.cancel() }
                }
            }
        }
    }

    suspend fun <P : Product, Support : ProductSupport> retrieveProducts(
        actorAndProject: ActorAndProject,
        api: UnknownResourceApi<Support>,
    ): SupportByProvider<P, Support> {
        val support = HashMap<String, List<Support>>()
        val mutex = Mutex()

        forEachRelevantProvider(actorAndProject) { providerId ->
            val element = fetchSupport(api, providerId)
            mutex.withLock { support[providerId] = element }
        }

        mutex.lock() // Lock, just in case any of the coroutines haven't stopped yet

        val result = HashMap<String, ArrayList<ResolvedSupport<P, Support>>>()
        for ((provider, elements) in support) {
            val list = result.getOrPut(provider) { ArrayList() }
            for (elem in elements) {
                @Suppress("UNCHECKED_CAST")
                val product = productCache.referenceToProductId(elem.product)?.let {
                    productCache.productIdToProduct(it) as? P?
                }

                if (product == null) {
                    log.info("Could not resolve product: ${elem.product}")
                } else {
                    list.add(ResolvedSupport(product, elem))
                }
            }
        }

        return SupportByProvider(result)
    }

    suspend fun <R : Any, S : Any> invokeCall(
        provider: String,
        actorAndProject: ActorAndProject,
        call: (providerId: String) -> CallDescription<R, S, *>,

        requestForProvider: R,

        isUserRequest: Boolean = true,
        useHttpClient: Boolean = true,
    ): S {
        val comms = communicationCache.retrieve(provider)
        val im = IntegrationProvider(provider)
        val actualCall = call(provider)

        val signedIntentFromEndUser = actorAndProject.signedIntentFromUser?.takeIf { isUserRequest }

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

    suspend fun <R : Any, S : Any> invokeSubscription(
        provider: String,
        actorAndProject: ActorAndProject,
        call: (providerId: String) -> CallDescription<R, S, *>,

        requestForProvider: R,

        isUserRequest: Boolean = true,

        handler: suspend (result: S) -> Unit
    ): S {
        val comms = communicationCache.retrieve(provider)
        val im = IntegrationProvider(provider)
        val actualCall = call(provider)

        val baseClient = comms.wsClient
        val signedIntentFromEndUser = actorAndProject.signedIntentFromUser?.takeIf { isUserRequest }

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

    // NOTE(Dan): This is copy & pasted from the ResourceProviderApi to avoid the mess of generics we currently have
    // in that part of the code. The idea at the time wasn't bad, but we just ended up having _too many_ generic types.
    private fun <Support : ProductSupport> retrieveSupportCall(
        providerId: String,
        namespace: String,
        serializer: KSerializer<Support>,
    ): CallDescription<Unit, BulkResponse<Support>, CommonErrorMessage> {
        val baseContext = baseContextForResourceProvider(providerId, namespace)
        val instance = object : CallDescriptionContainer("$namespace.provider.$providerId") {
            val retrieveProducts: CallDescription<Unit, BulkResponse<Support>, CommonErrorMessage>
                get() = call(
                    name = "retrieveProducts",
                    handler = { httpRetrieve(baseContext, "products", roles = Roles.PRIVILEGED) },
                    requestType = Unit.serializer(),
                    successType = BulkResponse.serializer(serializer),
                    errorType = CommonErrorMessage.serializer(),
                    requestClass = typeOfIfPossible<Unit>(),
                    successClass = typeOfIfPossible<BulkResponse<Support>>(),
                    errorClass = typeOfIfPossible<CommonErrorMessage>()
                )
        }

        return instance.retrieveProducts
    }

    companion object : Loggable {
        override val log = logger()
    }
}

private val HttpStatusCode.Companion.RetryWith get() = HttpStatusCode(449, "Retry With")
