package dk.sdu.cloud.app.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.HPCJobDescriptions
import dk.sdu.cloud.app.services.JobService
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.implement
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.jetbrains.exposed.sql.transactions.transaction

class JobController {
    fun configure(routing: Route) = with(routing) {
        route("jobs") {
            implement(HPCJobDescriptions.findById) {
                val user = call.request.validatedPrincipal
                val result = transaction { JobService.findJob(it.id, user) }
                if (result == null) {
                    error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
                } else {
                    ok(result)
                }
            }

            implement(HPCJobDescriptions.listRecent) {
                val user = call.request.validatedPrincipal
                ok(transaction { JobService.recentJobs(user) })
            }
        }
    }
}