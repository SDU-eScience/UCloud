package dk.sdu.cloud.slack.services

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.slack.api.Alert
import dk.sdu.cloud.slack.api.Ticket
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.cio.FailToConnectException
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.response
import io.ktor.content.TextContent
import io.ktor.http.*
import io.ktor.util.KtorExperimentalAPI
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
private data class SlackMessage(val text: String)

class SlackNotifier(
    val hook: String
) : Notifier {
    private val httpClient = HttpClient() {
        expectSuccess = false
    }

    @OptIn(KtorExperimentalAPI::class)
    override suspend fun onAlert(alert: Alert) {
        val message = alert.message.lines().joinToString("\n") { "> $it" }

        attemptSend(message)
    }

    @OptIn(KtorExperimentalAPI::class)
    override suspend fun onTicket(ticket: Ticket) {
        val message = """
            New ticket via UCloud:

            *User information:*
              - *Username:* ${ticket.principal.username}
              - *Role:* ${ticket.principal.role}
              - *Real name:* ${ticket.principal.firstName} ${ticket.principal.lastName}
              - *Email:* ${ticket.principal.email}

            *Technical info:*
              - *Request ID (Audit):* ${ticket.requestId}
              - *User agent:* ${ticket.userAgent}

            Subject: ${ticket.subject}

            The following message was attached:

        """.trimIndent() + ticket.message.lines().joinToString("\n") { "> $it" }

        attemptSend(message)
    }

    @OptIn(KtorExperimentalAPI::class)
    private suspend fun attemptSend(message: String) {
        var retries = 0
        while (true) {
            retries++
            if (retries == 3) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
            }
            val postResult = try {
                httpClient.request<HttpResponse>(hook) {
                    method = HttpMethod.Post
                    body = TextContent(
                        defaultMapper.encodeToString(SlackMessage(message)),
                        ContentType.Application.Json
                    )
                }
            } catch (ex: Exception) {
                when (ex) {
                    is java.net.ConnectException -> {
                        log.debug("Java.net.Connect Exception caught : ${ex.message}")

                    }
                    is FailToConnectException -> {
                        log.debug("Cio ConnectException caught : ${ex.message}")
                    }
                }
                continue
            }
            val status = postResult.response.status
            if (!status.isSuccess()) {
                log.warn("unsuccessful message from slack ($status)")
                runCatching { log.warn(postResult.receive()) }
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }
            return
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
