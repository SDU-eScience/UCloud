package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode

sealed class ProjectException : RuntimeException() {
    class Duplicate : ProjectException()
    class NotFound : ProjectException()
}

inline fun CallHandler<*, *, CommonErrorMessage>.tryWithProject(closure: () -> Unit) {
    try {
        closure()
    } catch (ex: ProjectException) {
        when (ex) {
            is ProjectException.Duplicate -> error(CommonErrorMessage("Already exists"), HttpStatusCode.Conflict)
            is ProjectException.NotFound -> error(CommonErrorMessage("Not Found"), HttpStatusCode.NotFound)
        }
    } catch (ex: Exception) {
        error(CommonErrorMessage("Internal Server Error"), HttpStatusCode.InternalServerError)
        ProjectService.log.warn(ex.stackTraceToString())
    }
}

class ProjectService(
    private val cloud: AuthenticatedClient
) {

    suspend fun createProject(title: String, user: String): String {
        return ProjectDescriptions.create.call(CreateProjectRequest(title, user), cloud).orThrow().id
    }

    companion object : Loggable {
        override val log = logger()
    }
}
