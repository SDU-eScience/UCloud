package org.esciencecloud.abc.http

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import org.esciencecloud.abc.services.HPCStore
import org.esciencecloud.abc.api.HPCAppEvent
import org.esciencecloud.abc.api.JobStatus
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok

class JobController(private val store: HPCStore) {
    fun configure(routing: Routing) = with(routing) {
        get("job/{id}") {
            val lastEvent = store.queryJobIdToStatus(call.parameters["id"]!!)

            when (lastEvent) {
                is Ok -> {
                    val status = when (lastEvent.result) {
                        is HPCAppEvent.Pending -> JobStatus.PENDING
                        is HPCAppEvent.SuccessfullyCompleted -> JobStatus.COMPLETE
                        is HPCAppEvent.UnsuccessfullyCompleted -> JobStatus.FAILURE
                        is HPCAppEvent.Started -> JobStatus.RUNNING

                        is HPCAppEvent.Ended -> IllegalStateException() // Is abstract, all other cases should be caught
                    }

                    call.respond(mapOf("status" to status))
                }

                is Error -> {
                    // TODO Need proper error codes from the Error type!
                    call.respond(HttpStatusCode.NotFound, lastEvent.message)
                }
            }
        }
    }
}