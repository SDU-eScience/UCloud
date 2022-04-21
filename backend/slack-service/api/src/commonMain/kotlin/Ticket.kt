package dk.sdu.cloud.slack.api

import dk.sdu.cloud.SecurityPrincipal
import kotlinx.serialization.Serializable

@Serializable
data class Ticket(
    val requestId: String,
    val principal: SecurityPrincipal,
    val userAgent: String,
    val subject: String,
    val message: String,
    val project: String? = null,
)
