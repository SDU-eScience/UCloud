package dk.sdu.cloud.alerting.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class Alert(
    val message: String
)

class AlertingService(private val notifiers: List<AlertNotifier>) {
    init {
        if (notifiers.isEmpty()) {
            throw IllegalArgumentException("Need at least one notifier!")
        }
    }

    suspend fun createAlert(alert: Alert) {
        coroutineScope {
            val result = notifiers.map {
                async {
                    runCatching { it.onAlert(alert) }
                }
            }.awaitAll()

            val hasSuccess = result.any { it.isSuccess }
            if (!hasSuccess) {
                val exceptions = result.mapNotNull { it.exceptionOrNull() }
                log.warn("Caught exception for ticket: $alert")
                exceptions.forEach { log.warn(it.stackTraceToString()) }
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
