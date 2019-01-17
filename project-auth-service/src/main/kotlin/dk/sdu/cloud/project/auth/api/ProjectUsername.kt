package dk.sdu.cloud.project.auth.api

import dk.sdu.cloud.project.api.ProjectRole

fun usernameForProjectInRole(project: String, role: ProjectRole): String {
    return "$project#$role"
}
