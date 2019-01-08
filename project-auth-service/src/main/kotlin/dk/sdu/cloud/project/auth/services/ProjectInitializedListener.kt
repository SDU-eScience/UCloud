package dk.sdu.cloud.project.auth.services

interface ProjectInitializedListener {
    suspend fun onProjectCreated(projectId: String, users: List<AuthToken>)
}
