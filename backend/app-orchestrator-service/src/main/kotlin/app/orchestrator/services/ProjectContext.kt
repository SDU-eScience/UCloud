package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.project.api.ProjectRole

data class ProjectContext(val project: String, val role: ProjectRole)
