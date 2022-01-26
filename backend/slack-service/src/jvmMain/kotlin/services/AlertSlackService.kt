package dk.sdu.cloud.slack.services

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.slack.api.Alert
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class AlertSlackService(private val notifiers: List<Notifier>) {
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
                log.warn("Caught exception for alert: $alert")
                exceptions.forEach { log.warn(it.stackTraceToString()) }
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
