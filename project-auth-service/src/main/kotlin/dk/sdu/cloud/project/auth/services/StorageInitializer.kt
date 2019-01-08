package dk.sdu.cloud.project.auth.services

import dk.sdu.cloud.auth.api.CreateSingleUserResponse
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.ListDirectoryRequest
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.auth.api.usernameForProjectInRole
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.share.api.CreateShareRequest
import dk.sdu.cloud.share.api.ShareDescriptions
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import org.slf4j.Logger

/**
 * Listens to events that indicate that a project has been fully initialized.
 *
 * Note(Dan): This could easily be a different microservice. But we currently only need to run a single script, it
 * is significantly easier to just keep this in the same microservice for now. When we reach the point of having to
 * many of these "ProjectInitializedListeners" we should start moving them into their own services.
 */
class StorageInitializer(
    private val cloudContext: CloudContext
) : ProjectInitializedListener {
    override suspend fun onProjectCreated(
        event: ProjectEvent.Created,
        users: List<Pair<ProjectRole, CreateSingleUserResponse>>
    ) {
        log.info("Handling storage hook for $event")

        val projectHome = "/home/${event.project.id}"
        val piCloud = cloudContext.jwtAuth(
            (users.find { it.first == ProjectRole.PI }
                ?: throw IllegalArgumentException("Bad input")).second.accessToken
        )

        awaitFileSystem(projectHome, piCloud)

        users.asSequence().filter { it.first != ProjectRole.PI }.forEach { (role, tokens) ->
            val username = usernameForProjectInRole(event.project.id, role)
            val userCloud = cloudContext.jwtAuth(tokens.accessToken)

            val shareId = ShareDescriptions.create.call(
                CreateShareRequest(
                    sharedWith = username,
                    path = projectHome,
                    rights = AccessRight.values().toSet()
                ),
                piCloud
            ).orThrow()

            ShareDescriptions.accept.call(
                shareId,
                userCloud
            ).orThrow()
        }
    }

    private suspend fun awaitFileSystem(projectHome: String, piCloud: JWTAuthenticatedCloud) {
        for (attempt in 1..30) {
            log.debug("Awaiting ready status from project home ($projectHome)")
            val status = FileDescriptions.listAtPath.call(
                ListDirectoryRequest(
                    path = projectHome,
                    itemsPerPage = null,
                    page = null,
                    order = null,
                    sortBy = null
                ),
                piCloud
            ).status.let { HttpStatusCode.fromValue(it) }

            if (status.isSuccess()) {
                break
            } else if (status == HttpStatusCode.ExpectationFailed || status == HttpStatusCode.NotFound) {
                log.debug("FS is not yet ready!")
                delay(1000)
            } else {
                throw RPCException.fromStatusCode(status)
            }
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
