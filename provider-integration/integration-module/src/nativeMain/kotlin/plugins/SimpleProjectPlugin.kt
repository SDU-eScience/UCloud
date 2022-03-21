package dk.sdu.cloud.plugins

import dk.sdu.cloud.project.api.v2.Project

class SimpleProjectPlugin : ProjectPlugin {
    override suspend fun PluginContext.onProjectUpdated(project: Project) {
        println("Project updated ${project}")
    }
}
