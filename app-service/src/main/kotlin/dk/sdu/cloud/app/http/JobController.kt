package dk.sdu.cloud.app.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.HPCJobDescriptions
import dk.sdu.cloud.app.services.HPCStore
import dk.sdu.cloud.app.storageConnection
import dk.sdu.cloud.service.KafkaRPCException
import dk.sdu.cloud.service.implement
import io.ktor.routing.Route
import io.ktor.routing.route

class JobController(private val store: HPCStore) {
    fun configure(routing: Route) = with(routing) {
        route("jobs") {
            implement(HPCJobDescriptions.findById) {
                try {
                    ok(store.queryJobIdToStatus(it.id, allowRetries = false))
                } catch (ex: KafkaRPCException) {
                    error(CommonErrorMessage(ex.message ?: "Error"), ex.httpStatusCode)
                }
            }

            implement(HPCJobDescriptions.listRecent) {
                val user = call.storageConnection.connectedUser.displayName
                try {
                    ok(store.queryRecentJobsByUser(user, allowRetries = false))
                } catch (ex: KafkaRPCException) {
                    error(CommonErrorMessage(ex.message ?: "Error"), ex.httpStatusCode)
                }
            }
        }
    }
}