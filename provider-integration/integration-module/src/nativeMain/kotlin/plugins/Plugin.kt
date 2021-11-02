package dk.sdu.cloud.plugins

import kotlinx.serialization.json.JsonElement

interface Plugin {
    suspend fun PluginContext.initialize() {}
}