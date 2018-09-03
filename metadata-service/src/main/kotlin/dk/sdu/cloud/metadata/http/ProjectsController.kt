package dk.sdu.cloud.metadata.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.metadata.api.CreateProjectResponse
import dk.sdu.cloud.metadata.api.ProjectDescriptions
import dk.sdu.cloud.metadata.api.ProjectEvent
import dk.sdu.cloud.metadata.api.ProjectEventProducer
import dk.sdu.cloud.metadata.services.Project
import dk.sdu.cloud.metadata.services.ProjectService
import dk.sdu.cloud.metadata.services.tryWithProject
import dk.sdu.cloud.service.*
import dk.sdu.cloud.file.api.AnnotateFileRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindByPath
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.LoggerFactory

class ProjectsController(
    private val projectEventProducer: ProjectEventProducer,
    private val projectService: ProjectService<*>
) : Controller {
    override val baseContext = ProjectDescriptions.baseContext

    override fun configure(routing: Route) = with(routing) {
        implement(ProjectDescriptions.create) { request ->
            logEntry(log, request)

            tryWithProject {
                val cloudCtx = call.cloudClient.parent
                val cloud = cloudCtx.jwtAuth(call.request.validatedPrincipal.token).withCausedBy(call.request.jobId)

                val rootStat = FileDescriptions.stat.call(FindByPath(request.fsRoot), cloud)
                if (rootStat !is RESTResponse.Ok) {
                    if (rootStat.status == 404) {
                        error(CommonErrorMessage("Could not find project root"), HttpStatusCode.NotFound)
                        return@implement
                    } else {
                        log.info("Could not find project root (${request.fsRoot}). Caused by response:")
                        log.info(rootStat.toString())
                        error(CommonErrorMessage("Internal Server Error"), HttpStatusCode.InternalServerError)
                        return@implement
                    }
                }

                val currentUser = call.request.validatedPrincipal.subject
                if (rootStat.result.ownerName != currentUser) {
                    log.debug("User is not owner of folder")
                    error(CommonErrorMessage("Not allowed"), HttpStatusCode.Forbidden)
                    return@implement
                }

                val annotateResult = FileDescriptions.annotate.call(
                    AnnotateFileRequest(request.fsRoot, PROJECT_ANNOTATION, currentUser),
                    call.cloudClient // Must be performed as the service
                )

                if (annotateResult is RESTResponse.Err) {
                    log.warn("Unable to annotate file! Status = ${annotateResult.status}")
                    log.warn(annotateResult.rawResponseBody)
                    error(CommonErrorMessage("Internal Server Error"), HttpStatusCode.InternalServerError)
                    return@implement
                }

                try {
                    log.debug("Creating a project! $currentUser")
                    val project = Project(null, request.fsRoot, rootStat.result.fileId, currentUser, "")
                    val id = projectService.createProject(project)
                    val projectWithId = project.copy(id = id)
                    projectEventProducer.emit(ProjectEvent.Created(projectWithId))
                    ok(CreateProjectResponse(id))
                } catch (ex: Exception) {
                    // TODO Remove annotation (missing endpoint currently)
                    log.info("Caught exception while creating project!")
                    log.info(ex.stackTraceToString())
                    throw ex
                }
            }
        }

        implement(ProjectDescriptions.findProjectByPath) {
            logEntry(log, it)

            tryWithProject {
                ok(projectService.findByFSRoot(it.path))
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProjectsController::class.java)
        const val PROJECT_ANNOTATION = "P"
    }
}

