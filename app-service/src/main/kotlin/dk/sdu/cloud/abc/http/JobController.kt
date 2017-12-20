package dk.sdu.cloud.abc.http

import dk.sdu.cloud.abc.api.HPCJobDescriptions
import dk.sdu.cloud.abc.api.StandardError
import dk.sdu.cloud.abc.services.HPCStore
import dk.sdu.cloud.abc.storageConnection
import io.ktor.routing.Route
import io.ktor.routing.route
import org.esciencecloud.service.KafkaRPCException
import org.esciencecloud.service.implement

class JobController(private val store: HPCStore) {
    fun configure(routing: Route) = with(routing) {
        route("jobs") {
            implement(HPCJobDescriptions.findById) {
                try {
                    ok(store.queryJobIdToStatus(it.id, allowRetries = false))
                } catch (ex: KafkaRPCException) {
                    error(StandardError(ex.message ?: "Error"), ex.httpStatusCode)
                }
            }

            implement(HPCJobDescriptions.listRecent) {
                val user = call.storageConnection.connectedUser.displayName
                try {
                    ok(store.queryRecentJobsByUser(user, allowRetries = false))
                } catch (ex: KafkaRPCException) {
                    error(StandardError(ex.message ?: "Error"), ex.httpStatusCode)
                }
            }
        }
    }
}