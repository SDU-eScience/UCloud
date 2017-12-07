package org.esciencecloud.abc.http

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import org.esciencecloud.abc.services.HPCStore
import org.esciencecloud.abc.storageConnection
import org.esciencecloud.service.KafkaRPCException

class JobController(private val store: HPCStore) {
    fun configure(routing: Route) = with(routing) {
        get("jobs/{id}") {
            try {
                call.respond(store.queryJobIdToStatus(call.parameters["id"]!!, allowRetries = false))
            } catch (ex: KafkaRPCException) {
                call.respond(ex.httpStatusCode)
            }
        }

        get("jobs") {
            val user = call.storageConnection.connectedUser.displayName
            try {
                call.respond(store.queryRecentJobsByUser(user, allowRetries = false))
            } catch (ex: KafkaRPCException) {
                call.respond(ex.httpStatusCode)
            }
        }
    }
}