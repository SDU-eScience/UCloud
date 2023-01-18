package dk.sdu.cloud.controllers

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.ipc.IpcServer
import dk.sdu.cloud.plugins.ResourcePlugin
import dk.sdu.cloud.provider.api.Resource
import dk.sdu.cloud.service.Controller
import kotlinx.coroutines.runBlocking
import dk.sdu.cloud.service.Loggable

abstract class BaseResourceController<
    P : Product,
    Sup : ProductSupport,
    Res : Resource<P, Sup>,
    Plugin : ResourcePlugin<P, Sup, Res, *>,
    Api : ResourceProviderApi<Res, *, *, *, *, P, Sup>>(
    protected val controllerContext: ControllerContext,
) : Controller, IpcController {
    protected abstract fun retrievePlugins(): Collection<Plugin>?
    protected abstract fun retrieveApi(providerId: String): Api
    override fun configureIpc(server: IpcServer) {}

    protected fun lookupPluginByProduct(productId: String): Plugin? {
        return retrievePlugins()?.find { plugin ->
            plugin.productAllocation.any { it.id == productId }
        }
    }

    protected fun lookupPluginOrNull(product: ProductReference): Plugin? {
        return retrievePlugins()?.find { plugin ->
            plugin.productAllocation.any { it.id == product.id && it.category == product.category }
        }
    }

    protected fun lookupPlugin(product: ProductReference): Plugin {
        return lookupPluginOrNull(product) ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
    }

    protected suspend fun <Request : Any, Response> CallHandler<*, *, *>.dispatchToPlugin(
        plugins: Collection<Plugin>,
        items: List<Request>,
        selector: suspend (Request) -> Resource<*, *>,
        dispatcher: suspend RequestContext.(plugin: Plugin, request: BulkRequest<Request>) -> BulkResponse<Response>
    ): BulkResponse<Response> {
        val response = ArrayList<Response?>()
        repeat(items.size) { response.add(null) }

        groupResources(plugins, items, selector).forEach { (plugin, group) ->
            with(requestContext(controllerContext)) {
                val pluginResponse = runBlocking { dispatcher(plugin, BulkRequest(group.map { it.item })) }
                pluginResponse.responses.forEachIndexed { index, resp ->
                    response[group[index].originalIndex] = resp
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        return BulkResponse(response as List<Response>)
    }

    protected data class ReorderedItem<T>(val originalIndex: Int, val item: T)

    protected suspend fun <T> groupResources(
        plugins: Collection<Plugin>,
        items: List<T>,
        selector: suspend (T) -> Resource<*, *>,
    ): Map<Plugin, List<ReorderedItem<T>>> {
        val result = HashMap<Plugin, ArrayList<ReorderedItem<T>>>()
        for ((index, item) in items.withIndex()) {
            val job = selector(item)
            val product = job.specification.product
            val plugin = lookupPluginOrNull(product) ?: continue
            val existing = result[plugin] ?: ArrayList()
            existing.add(ReorderedItem(index, item))
            result[plugin] = existing
        }
        return result
    }

    protected abstract fun RpcServer.configureCustomEndpoints(plugins: Collection<Plugin>, api: Api)

    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        val plugins = retrievePlugins()
        if (plugins == null) {
            return
        }
        val api = retrieveApi(controllerContext.configuration.core.providerId)
        val config = controllerContext.configuration

        implement(api.create) {
            if (!config.shouldRunUserCode()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            ok(
                dispatchToPlugin(plugins, request.items, { it }) { plugin, request ->
                    with(plugin) { createBulk(request) }
                }
            )
        }

        implement(api.retrieveProducts) {
            if (!config.shouldRunServerCode()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            runBlocking {
                ok(
                    BulkResponse(
                        plugins
                            .flatMap { plugin ->
                                val products = plugin.productAllocation
                                with(requestContext(controllerContext)) {
                                    with(plugin) {
                                        val providerId = controllerContext.configuration.core.providerId
                                        retrieveProducts(
                                            products.map { product ->
                                                ProductReference(
                                                    product.id,
                                                    product.category,
                                                    providerId
                                                )
                                            }
                                        ).responses
                                    }
                                }
                            }
                    )
                )
            }
        }

        api.delete?.let { delete ->
            implement(delete) {
                if (!config.shouldRunUserCode()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

                ok(
                    dispatchToPlugin(plugins, request.items, { it }) { plugin, request ->
                        with(plugin) { deleteBulk(request) }
                    }
                )
            }
        }

        implement(api.updateAcl) {
            if (!config.shouldRunUserCode()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            ok(
                dispatchToPlugin(plugins, request.items, { it.resource }) { plugin, request ->
                    with(plugin) {
                        updateAcl(request)
                        BulkResponse(request.items.map { Unit })
                    }
                }
            )
        }

        implement(api.verify) {
            if (!config.shouldRunServerCode()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            dispatchToPlugin(plugins, request.items, { it }) { plugin, request ->
                with(plugin) {
                    verify(request)
                    BulkResponse(emptyList<Unit>())
                }
            }

            ok(Unit)
        }

        implement(api.init) {
            if (!config.shouldRunUserCode()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            plugins.forEach { plugin ->
                with(requestContext(controllerContext)) {
                    with(plugin) {
                        initInUserMode(request.principal)
                    }
                }
            }

            ok(Unit)
        }

        configureCustomEndpoints(plugins, api)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
