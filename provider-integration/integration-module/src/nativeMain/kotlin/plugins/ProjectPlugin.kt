package dk.sdu.cloud.plugins

import dk.sdu.cloud.config.*
import dk.sdu.cloud.project.api.v2.Project

interface ProjectPlugin : Plugin<ConfigSchema.Plugins.Projects> {
    suspend fun PluginContext.onProjectUpdated(newProject: Project)
    suspend fun PluginContext.onUserMappingInserted(ucloudId: String, localId: Int)
}
