package dk.sdu.cloud.plugins

import dk.sdu.cloud.project.api.v2.Project
import kotlinx.serialization.json.JsonObject

interface ProjectPlugin : Plugin<JsonObject> {
    suspend fun PluginContext.onProjectUpdated(project: Project)
}
