package dk.sdu.cloud.app.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.HPCJobDescriptions
import dk.sdu.cloud.app.api.JobStartedResponse
import dk.sdu.cloud.app.services.JobException
import dk.sdu.cloud.app.services.JobService
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.*
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory
import java.util.*

class JobController(
    private val jobService: JobService
) {
    fun configure(routing: Route) = with(routing) {
        route("jobs") {
            implement(HPCJobDescriptions.findById) {
                logEntry(log, it)
                val user = call.request.validatedPrincipal
                TODO()
                /*
                val result = jobService.findJob(it.id, user)
                if (result == null) {
                    error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
                } else {
                    ok(result)
                }
                */
            }

            implement(HPCJobDescriptions.listRecent) {
                logEntry(log, it)
                val user = call.request.validatedPrincipal
                ok(jobService.recentJobs(user))
            }

            implement(HPCJobDescriptions.start) { req ->
                logEntry(log, req)
                try {
                    val uuid = jobService.startJob(req, call.request.validatedPrincipal)
                    ok(JobStartedResponse(uuid))
                } catch (ex: JobException) {
                    if (ex.statusCode.value in 500..599) log.warn(ex.stackTraceToString())
                    error(CommonErrorMessage(ex.message ?: "An error has occurred"), ex.statusCode)
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(JobController::class.java)
    }
}
