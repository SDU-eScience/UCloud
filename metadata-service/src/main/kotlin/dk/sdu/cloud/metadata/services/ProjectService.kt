package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.metadata.util.normalize
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode

sealed class ProjectException : RuntimeException() {
    class Duplicate : ProjectException()
    class NotFound : ProjectException()
}

suspend inline fun RESTHandler<*, *, CommonErrorMessage, *>.tryWithProject(closure: () -> Unit) {
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
    private val cloud: AuthenticatedCloud
) {

    suspend fun createProject(title: String, user: String): String {
        return ProjectDescriptions.create.call(CreateProjectRequest(title, user), cloud).orThrow().id
    }

    companion object : Loggable {
        override val log = logger()
    }
}
