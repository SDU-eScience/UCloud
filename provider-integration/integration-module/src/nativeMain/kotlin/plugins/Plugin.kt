package dk.sdu.cloud.plugins

import kotlinx.serialization.json.JsonElement

interface Plugin {
    fun PluginContext.initialize() {}
}