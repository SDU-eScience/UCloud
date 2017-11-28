package org.esciencecloud.abc.http

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import org.esciencecloud.abc.services.HPCStore
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Result

class JobController(private val store: HPCStore) {
    fun configure(routing: Route) = with(routing) {
        // TODO Authentication should go on all API routes
        get("jobs/{id}") {
            val lastEvent = store.queryJobIdToStatus(call.parameters["id"]!!, allowRetries = false)

            when (lastEvent) {
                is Ok -> {
                    val status = lastEvent.result.toJobStatus()
                    call.respond(mapOf("status" to status))
                }

                is Error -> {
                    // TODO Need proper error codes from the Error type!
                    call.respond(HttpStatusCode.NotFound, lastEvent.message)
                }
            }
        }

        // TODO JUST FOR TESTING
        // TODO JUST FOR TESTING
        // TODO JUST FOR TESTING
        get("myjobs/{user}") {
            val user = call.parameters["user"]!!
            val recent = store.queryRecentJobsByUser(user, allowRetries = false).capture() ?: return@get run {
                val error = Result.lastError<Unit>()
                call.respond(HttpStatusCode.fromValue(error.errorCode), error.message)
            }
            call.respond(recent)
        }
    }
}