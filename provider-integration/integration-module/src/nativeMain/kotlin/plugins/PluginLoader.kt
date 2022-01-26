package dk.sdu.cloud.plugins

import dk.sdu.cloud.PartialProductReferenceWithoutProvider
import dk.sdu.cloud.ProductBasedConfiguration
import dk.sdu.cloud.ProductReferenceWithoutProvider
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.plugins.compute.slurm.SlurmPlugin
import dk.sdu.cloud.plugins.connection.TicketBasedConnectionPlugin
import dk.sdu.cloud.plugins.identities.DirectIdentityMapperPlugin
import dk.sdu.cloud.plugins.projects.DirectProjectMapperPlugin
import dk.sdu.cloud.plugins.storage.posix.PosixCollectionPlugin
import dk.sdu.cloud.plugins.storage.posix.PosixFilesPlugin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class PluginLoaderException(message: String) : RuntimeException(message)

class PluginLoader(private val pluginContext: PluginContext) {
    private val filesPlugin = mapOf<String, () -> FilePlugin>(
        "posix" to { PosixFilesPlugin() }
    )

    private val fileCollectionsPlugin = mapOf<String, () -> FileCollectionPlugin>(
        "posix" to { PosixCollectionPlugin() }
    )

    private val computePlugins = mapOf<String, () -> ComputePlugin>(
        "slurm" to { SlurmPlugin() }
    )

    private val connectionPlugins = mapOf<String, () -> ConnectionPlugin>(
        "ticket" to { TicketBasedConnectionPlugin() }
    )

    private val identityMapperPlugins = mapOf<String, () -> IdentityMapperPlugin>(
        "direct" to { DirectIdentityMapperPlugin() }
    )

    private val projectPlugins = mapOf<String, () -> ProjectMapperPlugin>(
        "direct" to { DirectProjectMapperPlugin() }
    )

    private fun <T : Plugin<Unit>> loadPlugin(lookupTable: Map<String, () -> T>, jsonObject: JsonObject): T? {
        return jsonObject.entries.firstOrNull()?.let {
            val pluginFactory = lookupTable[it.key]
            if (pluginFactory != null) {
                val plugin = pluginFactory()
                with(pluginContext) {
                    with(plugin) {
                        runBlocking { initialize(Unit) }
                    }
                }
                return plugin
            } else {
                null
            }
        }
    }

    private fun <T : Plugin<ProductBasedConfiguration>> loadProductBasedPlugin(
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
                    runBlocking { initialize(config) }
                }
            }
        }

        return ProductBasedPlugins(config.products, plugins, criteria)
    }

    fun load(): LoadedPlugins {
        val config = pluginContext.config

        val fileCollection = config.plugins.fileCollection?.let { loadProductBasedPlugin(fileCollectionsPlugin, it) }
        val files = config.plugins.files?.let { loadProductBasedPlugin(filesPlugin, it) }
        val compute = config.plugins.compute?.let { loadProductBasedPlugin(computePlugins, it) }
        val connection = config.plugins.connection?.let { loadPlugin(connectionPlugins, it) }
        val identityMapper = config.plugins.identityMapper?.let { loadPlugin(identityMapperPlugins, it) }
        val projects = config.plugins.projects?.let { loadPlugin(projectPlugins, it) }

        return LoadedPlugins(files, fileCollection, compute, connection, identityMapper, projects)
    }
}

data class ProductBasedPlugins<T : Plugin<ProductBasedConfiguration>>(
    val allProducts: List<ProductReferenceWithoutProvider>,
    val plugins: Map<String, T>,
    val criteria: List<Pair<String, PartialProductReferenceWithoutProvider>>
) {
    fun lookup(reference: ProductReferenceWithoutProvider): T {
        return lookupWithName(reference).second
    }

    fun lookupWithName(reference: ProductReferenceWithoutProvider): Pair<String, T> {
        val id = criteria.find { (_, pluginCriteria) -> pluginCriteria.matches(reference) }?.first
            ?: throw RPCException(
                "Unsupported product: ${reference.category} / ${reference.id}",
                HttpStatusCode.BadRequest
            )

        return Pair(id, plugins.getValue(id))
    }

    fun lookupWithName(reference: ProductReference): Pair<String, T> {
        return lookupWithName(ProductReferenceWithoutProvider(reference.id, reference.category))
    }

    fun lookup(reference: ProductReference): T {
        return lookup(ProductReferenceWithoutProvider(reference.id, reference.category))
    }
}

data class LoadedPlugins(
    val files: ProductBasedPlugins<FilePlugin>?,
    val fileCollection: ProductBasedPlugins<FileCollectionPlugin>?,
    val compute: ProductBasedPlugins<ComputePlugin>?,
    val connection: ConnectionPlugin?,
    val identityMapper: IdentityMapperPlugin?,
    val projects: ProjectMapperPlugin?,
)
