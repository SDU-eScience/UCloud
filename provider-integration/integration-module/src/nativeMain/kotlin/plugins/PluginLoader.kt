package dk.sdu.cloud.plugins

import dk.sdu.cloud.PartialProductReferenceWithoutProvider
import dk.sdu.cloud.ProductBasedConfiguration
import dk.sdu.cloud.ProductReferenceWithoutProvider
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.plugins.compute.SampleComputePlugin
import dk.sdu.cloud.plugins.connection.TicketBasedConnectionPlugin
import dk.sdu.cloud.plugins.identities.DirectIdentityMapperPlugin
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject

class PluginLoaderException(message: String) : RuntimeException(message)

class PluginLoader(private val pluginContext: PluginContext) {
    private val computePlugins = mapOf<String, () -> ComputePlugin>(
        "sample" to { SampleComputePlugin() }
    )

    private val connectionPlugins = mapOf<String, () -> ConnectionPlugin>(
        "ticket" to { TicketBasedConnectionPlugin() }
    )

    private val identityMapperPlugins = mapOf<String, () -> IdentityMapperPlugin>(
        "direct" to { DirectIdentityMapperPlugin() }
    )

    private fun <T : Plugin> loadPlugin(lookupTable: Map<String, () -> T>, jsonObject: JsonObject): T? {
        return jsonObject.entries.firstOrNull()?.let {
            val pluginFactory = lookupTable[it.key]
            if (pluginFactory != null) {
                val plugin = pluginFactory()
                with(pluginContext) {
                    with(plugin) {
                        initialize()
                    }
                }
                return plugin
            } else {
                null
            }
        }
    }

    private fun <T : Plugin> loadProductBasedPlugin(
        lookupTable: Map<String, () -> T>,
        config: ProductBasedConfiguration
    ): ProductBasedPlugins<T> {
        for (product in config.products) {
            val isCovered = config.plugins.any { p -> p.activeFor.any { it.matches(product) } }
            if (!isCovered) {
                throw PluginLoaderException(
                    "Invalid configuration. Product: ${product.category} / ${product.id} is not covered by any plugin."
                )
            }
        }

        for (plugin in config.plugins) {
            val isCovered = config.products.any { p -> plugin.activeFor.any { it.matches(p) } }
            if (!isCovered) {
                throw PluginLoaderException(
                    "Invalid configuration. Plugin: ${plugin.name ?: plugin.id} covers no products"
                )
            }
        }

        val plugins = HashMap<String, T>()
        val criteria = ArrayList<Pair<String, PartialProductReferenceWithoutProvider>>()
        for (plugin in config.plugins) {
            val key = plugin.name ?: plugin.id
            for (c in plugin.activeFor) {
                criteria.add(Pair(key, c))
            }

            val loaded = (lookupTable[plugin.id]?.invoke() ?: throw PluginLoaderException("Unknown plugin: ${plugin.id}"))
            plugins[key] = loaded

            with(pluginContext) {
                with(loaded) {
                    initialize()
                }
            }
        }

        return ProductBasedPlugins(config.products, plugins, criteria)
    }

    fun load(): LoadedPlugins {
        val config = pluginContext.config

        val compute = config.plugins.compute?.let { loadProductBasedPlugin(computePlugins, it) }
        val connection = config.plugins.connection?.let { loadPlugin(connectionPlugins, it) }
        val identityMapper = config.plugins.identityMapper?.let { loadPlugin(identityMapperPlugins, it) }

        return LoadedPlugins(compute, connection, identityMapper)
    }
}

data class ProductBasedPlugins<T : Plugin>(
    val allProducts: List<ProductReferenceWithoutProvider>,
    val plugins: Map<String, T>,
    val criteria: List<Pair<String, PartialProductReferenceWithoutProvider>>
) {
    fun lookup(reference: ProductReferenceWithoutProvider): T {
        val id = criteria.find { (_, pluginCriteria) -> pluginCriteria.matches(reference) }?.first
            ?: throw RPCException(
                "Unsupported product: ${reference.category} / ${reference.id}",
                HttpStatusCode.BadRequest
            )

        return plugins.getValue(id)
    }

    fun lookup(reference: ProductReference): T {
        return lookup(ProductReferenceWithoutProvider(reference.id, reference.category))
    }
}

data class LoadedPlugins(
    val compute: ProductBasedPlugins<ComputePlugin>?,
    val connection: ConnectionPlugin?,
    val identityMapper: IdentityMapperPlugin?,
)
