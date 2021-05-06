package dk.sdu.cloud.plugins

import dk.sdu.cloud.IMConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class PluginLoader(private val config: IMConfiguration) {
    private val computePlugins = mapOf<String, () -> ComputePlugin>(
        "sample" to { SampleComputePlugin() }
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
        return LoadedPlugins(
            loadPlugin(computePlugins, config.plugins.compute)
        )
    }
}

data class LoadedPlugins(
    val compute: ComputePlugin?
)
