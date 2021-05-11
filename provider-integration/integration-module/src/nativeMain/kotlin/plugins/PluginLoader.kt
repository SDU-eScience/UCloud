package dk.sdu.cloud.plugins

import dk.sdu.cloud.ServerMode
import kotlinx.serialization.json.JsonObject

class PluginLoader(private val pluginContext: PluginContext) {
    private val computePlugins = mapOf<String, () -> ComputePlugin>(
        "sample" to { SampleComputePlugin() }
    )

    private val connectionPlugins = mapOf<String, () -> ConnectionPlugin>(
        "ticket" to { TicketBasedConnectionPlugin() }
    )

    private fun <T : Plugin> loadPlugin(lookupTable: Map<String, () -> T>, jsonObject: JsonObject): T? {
        return jsonObject.entries.firstOrNull()?.let {
            val pluginFactory = lookupTable[it.key]
            val config = it.value
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
        val serverMode = config.serverMode

        val compute = config.plugins.compute?.let { loadPlugin(computePlugins, it) }
        val connection = config.plugins.connection?.let { loadPlugin(connectionPlugins, it) }

        return LoadedPlugins(compute, connection)
    }
}

data class LoadedPlugins(
    val compute: ComputePlugin?,
    val connection: ConnectionPlugin?,
)
