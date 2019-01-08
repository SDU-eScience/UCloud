package dk.sdu.cloud.project.auth.services

import dk.sdu.cloud.auth.api.CreateSingleUserResponse
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectRole

interface ProjectInitializedListener {
    suspend fun onProjectCreated(event: ProjectEvent.Created, users: List<Pair<ProjectRole, CreateSingleUserResponse>>)
}
