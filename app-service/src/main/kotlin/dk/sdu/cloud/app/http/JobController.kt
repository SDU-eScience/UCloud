package dk.sdu.cloud.app.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.HPCJobDescriptions
import dk.sdu.cloud.app.services.HPCStore
import dk.sdu.cloud.app.services.JobService
import dk.sdu.cloud.app.validatedJwt
import dk.sdu.cloud.service.KafkaRPCException
import dk.sdu.cloud.service.implement
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.jetbrains.exposed.sql.transactions.transaction

class JobController(private val store: HPCStore) {
    fun configure(routing: Route) = with(routing) {
        route("jobs") {
            implement(HPCJobDescriptions.findById) {
                val user = call.validatedJwt
                val result = transaction { JobService.findJob(it.id, user) }
                if (result == null) {
                    error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
                } else {
                    ok(result)
                }
            }

            implement(HPCJobDescriptions.listRecent) {
                val user = call.validatedJwt
                ok(transaction { JobService.recentJobs(user) })
            }
        }
    }
}