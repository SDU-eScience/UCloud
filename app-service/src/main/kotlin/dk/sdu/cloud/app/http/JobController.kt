package dk.sdu.cloud.app.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.HPCJobDescriptions
import dk.sdu.cloud.app.api.JobCancelledResponse
import dk.sdu.cloud.app.services.JobService
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.*
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

class JobController(
    private val jobRequestProducer: MappedEventProducer<String, KafkaRequest<AppRequest>>
) {
    fun configure(routing: Route) = with(routing) {
        route("jobs") {
            implement(HPCJobDescriptions.findById) {
                logEntry(log, it)
                val user = call.request.validatedPrincipal
                val result = transaction { JobService.findJob(it.id, user) }
                if (result == null) {
                    error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
                } else {
                    ok(result)
                }
            }

            implement(HPCJobDescriptions.listRecent) {
                logEntry(log, it)
                val user = call.request.validatedPrincipal
                ok(transaction { JobService.recentJobs(user) })
            }

            implement(HPCJobDescriptions.start) { req ->
                logEntry(log, req)

                jobRequestProducer.emit(
                    KafkaRequest(
                        RequestHeader(
                            call.request.jobId,
                            call.request.validatedPrincipal.token
                        ),
                        req
                    )
                )
            }

            implement(HPCJobDescriptions.cancel) {
                logEntry(log, it)
                error(JobCancelledResponse(false), HttpStatusCode.NotImplemented)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(JobController::class.java)
    }
}
