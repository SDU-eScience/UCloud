package dk.sdu.cloud.app.api

data class ApplicationBackend(
    val name: String,
    val title: String = name,
    val useWorkspaces: Boolean = false
)
