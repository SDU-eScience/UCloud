package dk.sdu.cloud.plugins

import dk.sdu.cloud.plugins.compute.SampleComputePlugin
import dk.sdu.cloud.plugins.connection.TicketBasedConnectionPlugin
import dk.sdu.cloud.plugins.identities.DirectIdentityMapperPlugin
import kotlinx.serialization.json.JsonObject

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

    fun load(): LoadedPlugins {
        val config = pluginContext.config

        val compute = config.plugins.compute?.let { loadPlugin(computePlugins, it) }
        val connection = config.plugins.connection?.let { loadPlugin(connectionPlugins, it) }
        val identityMapper = config.plugins.identityMapper?.let { loadPlugin(identityMapperPlugins, it) }

        return LoadedPlugins(compute, connection, identityMapper)
    }
}

data class LoadedPlugins(
    val compute: ComputePlugin?,
    val connection: ConnectionPlugin?,
    val identityMapper: IdentityMapperPlugin?,
)
