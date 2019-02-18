package dk.sdu.cloud.file.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.homeDirectory
import dk.sdu.cloud.file.util.FSException

class HomeFolderService(
    private val serviceCloud: AuthenticatedClient
) {
    suspend fun findHomeFolder(username: String): String {
        val user =
            UserDescriptions.lookupUsers
                .call(
                    LookupUsersRequest(listOf(username)),
                    serviceCloud
                )
                .orThrow()
                .results
                .values
                .singleOrNull() ?: throw FSException.PermissionException()

        return if (user.role == Role.PROJECT_PROXY) {
            homeDirectory(username.substringBeforeLast('#'))
        } else {
            homeDirectory(username)
        }
    }
}
