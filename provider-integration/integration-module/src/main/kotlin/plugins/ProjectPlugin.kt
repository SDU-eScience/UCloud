package dk.sdu.cloud.plugins

import dk.sdu.cloud.PageV2
import dk.sdu.cloud.config.*
import dk.sdu.cloud.project.api.v2.Project
import kotlinx.serialization.Serializable

@Serializable
data class ProjectWithLocalId(
    val localId: Int,
    val project: Project
)

interface ProjectPlugin : Plugin<ConfigSchema.Plugins.Projects> {
    suspend fun PluginContext.onProjectUpdated(newProject: Project)
    suspend fun PluginContext.onUserMappingInserted(ucloudId: String, localId: Int)
    suspend fun PluginContext.lookupLocalId(ucloudId: String): Int?
    suspend fun PluginContext.browseProjects(): PageV2<ProjectWithLocalId>
}

