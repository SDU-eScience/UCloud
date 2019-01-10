package dk.sdu.cloud.project.auth.services

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.ListDirectoryRequest
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.auth.api.usernameForProjectInRole
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.share.api.AcceptShareRequest
import dk.sdu.cloud.share.api.CreateShareRequest
import dk.sdu.cloud.share.api.ShareDescriptions
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.slf4j.Logger

/**
 * Listens to events that indicate that a project has been fully initialized.
 *
 * Note(Dan): This could easily be a different microservice. But we currently only need to run a single script, it
 * is significantly easier to just keep this in the same microservice for now. When we reach the point of having to
 * many of these "ProjectInitializedListeners" we should start moving them into their own services.
 */
class StorageInitializer(
    private val refreshTokenCloudFactory: (String) -> AuthenticatedCloud
) : ProjectInitializedListener {
    override suspend fun onProjectCreated(projectId: String, users: List<AuthToken>) {
        log.info("Handling storage hook for $projectId")

        val projectHome = "/home/$projectId"
        val piCloud = refreshTokenCloudFactory(
            (users.find { it.role == ProjectRole.PI }
                ?: throw IllegalArgumentException("Bad input")).authRefreshToken
        )

        awaitFileSystem(projectHome, piCloud)

        users.filter { it.role != ProjectRole.PI }.map { tokens ->
            GlobalScope.launch {
                val username = usernameForProjectInRole(projectId, tokens.role)
                log.debug("Creating share for $username")
                val userCloud = refreshTokenCloudFactory(tokens.authRefreshToken)
                awaitFileSystem(projectHome, userCloud)

                val shareResponse = ShareDescriptions.create.call(
                    CreateShareRequest(
                        sharedWith = username,
                        path = projectHome,
                        rights = AccessRight.values().toSet()
                    ),
                    piCloud
                )

                if (HttpStatusCode.fromValue(shareResponse.status) == HttpStatusCode.Conflict) return@launch
                val shareId = shareResponse.orThrow()

                ShareDescriptions.accept.call(
                    AcceptShareRequest(shareId.id, createLink = false),
                    userCloud
                ).orThrow()
            }
        }.joinAll()
    }

    private suspend fun awaitFileSystem(homeFolder: String, cloud: AuthenticatedCloud) {
        for (attempt in 1..100) {
            log.debug("Awaiting ready status from project home ($homeFolder)")
            val status = FileDescriptions.listAtPath.call(
                ListDirectoryRequest(
                    path = homeFolder,
                    itemsPerPage = null,
                    page = null,
                    order = null,
                    sortBy = null
                ),
                cloud
            ).status.let { HttpStatusCode.fromValue(it) }

            // Forbidden is a good indicator that the FS is ready but we don't have permissions yet.
            if (status.isSuccess() || status == HttpStatusCode.Forbidden) {
                break
            } else if (status == HttpStatusCode.ExpectationFailed || status == HttpStatusCode.NotFound) {
                log.debug("FS is not yet ready!")
                delay(100)
            } else {
                throw RPCException.fromStatusCode(status)
            }
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
