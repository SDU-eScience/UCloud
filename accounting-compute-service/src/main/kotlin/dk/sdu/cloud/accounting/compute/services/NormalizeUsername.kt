package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.Role

fun normalizeUsername(username: String, role: Role): String {
    return when (role) {
        Role.PROJECT_PROXY -> username.substringBeforeLast('#')
        else -> username
    }
}
