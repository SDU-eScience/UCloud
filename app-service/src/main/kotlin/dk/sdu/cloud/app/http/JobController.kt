package dk.sdu.cloud.app.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.HPCJobDescriptions
import dk.sdu.cloud.app.api.JobStartedResponse
import dk.sdu.cloud.app.services.JobException
import dk.sdu.cloud.app.services.JobService
import dk.sdu.cloud.app.services.JobServiceException
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.service.*
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class JobController(
    private val jobService: JobService
) {
    fun configure(routing: Route) = with(routing) {
        route("jobs") {
            implement(HPCJobDescriptions.findById) {
                logEntry(log, it)
                val user = call.request.validatedPrincipal
                val result = jobService.findJobById(user, it.id)
                if (result == null) {
                    error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
                } else {
                    ok(result)
                }
            }

            implement(HPCJobDescriptions.listRecent) {
                logEntry(log, it)
                val user = call.request.validatedPrincipal
                ok(jobService.recentJobs(user, it))
            }

            implement(HPCJobDescriptions.start) { req ->
                logEntry(log, req)
                try {
                    val userCloud = JWTAuthenticatedCloud(
                        call.cloudClient.parent,
                        call.request.validatedPrincipal.token
                    ).withCausedBy(call.request.jobId)

                    val uuid = jobService.startJob(call.request.validatedPrincipal, req, userCloud)
                    ok(JobStartedResponse(uuid))
                } catch (ex: JobException) {
                    if (ex.statusCode.value in 500..599) log.warn(ex.stackTraceToString())
                    error(CommonErrorMessage(ex.message ?: "An error has occurred"), ex.statusCode)
                }
            }

            implement(HPCJobDescriptions.follow) { req ->
                logEntry(log, req)

                val user = call.request.validatedPrincipal
                val job = jobService.findJobForInternalUseById(user, req.jobId) ?: return@implement run {
                    log.debug("Could not find job id: ${req.jobId}")
                    error(CommonErrorMessage("Job not found"), HttpStatusCode.NotFound)
                }

                val result = try {
                    jobService.followStdStreams(req, job)
                } catch (ex: JobServiceException) {
                    error(CommonErrorMessage(ex.message ?: "Unknown error"), ex.statusCode)
                    return@implement
                }

                ok(result)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(JobController::class.java)
    }
}
