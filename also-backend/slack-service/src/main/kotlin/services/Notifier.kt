package dk.sdu.cloud.slack.services

import dk.sdu.cloud.slack.api.Alert
import dk.sdu.cloud.slack.api.Ticket

interface Notifier {
    suspend fun onAlert(alert: Alert)
    suspend fun onTicket(ticket: Ticket)
}
