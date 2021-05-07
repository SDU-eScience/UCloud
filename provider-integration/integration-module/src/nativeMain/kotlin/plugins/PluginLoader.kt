package dk.sdu.cloud.plugins

import dk.sdu.cloud.IMConfiguration
import dk.sdu.cloud.ServerMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class PluginLoader(private val config: IMConfiguration) {
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
                plugin.initialize(config)
                return plugin
            } else {
                null
            }
        }
    }

    fun load(): LoadedPlugins {
        val serverMode = config.serverMode

        val compute = config.plugins.compute?.let { loadPlugin(computePlugins, it) }
        val connection = if (serverMode == ServerMode.SERVER) {
            config.plugins.connection?.let { loadPlugin(connectionPlugins, it) }
        } else {
            null
        }

        return LoadedPlugins(compute, connection)
    }
}

data class LoadedPlugins(
    val compute: ComputePlugin?,
    val connection: ConnectionPlugin?,
)
