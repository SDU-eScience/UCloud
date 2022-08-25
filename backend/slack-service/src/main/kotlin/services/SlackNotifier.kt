package dk.sdu.cloud.slack.services

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.slack.api.Alert
import dk.sdu.cloud.slack.api.Ticket
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.cio.FailToConnectException
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.content.TextContent
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.net.ConnectException

@Serializable
private data class SlackMessage(val text: String)

class SlackNotifier(
    private val hook: String,
    private val db: DBContext,
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
        data class ProjectIdAndTitle(val id: String, val title: String)

        val projects = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", ticket.principal.username)
                },
                """
                    select p.id, p.title
                    from
                        project.projects p join
                        project.project_members pm on p.id = pm.project_id
                    where
                        pm.username = :username
                """
            ).rows.map { row ->
                ProjectIdAndTitle(row.getString(0)!!, row.getString(1)!!)
            }
        }

        val projectString = if (projects.isEmpty()) {
            "None"
        } else {
            buildString {
                appendLine()
                for (project in projects) {
                    val isCurrentProject = ticket.project == project.id
                    append("    - ")
                    append(project.title)
                    append(" (`")
                    append(project.id)
                    append("`)")

                    if (isCurrentProject) {
                        appendLine(" [Current]")
                    } else {
                        appendLine()
                    }
                }
            }
        }

        val message = """
New ticket via UCloud:

*User information:*
  - *Username:* ${ticket.principal.username}
  - *Real name:* ${ticket.principal.firstName} ${ticket.principal.lastName}
  - *Email:* ${ticket.principal.email}
  - *Projects:* ${projectString}

*Technical info:*
  - *Request ID (Audit):* `${ticket.requestId}`
  - *User agent:* ${ticket.userAgent}

Subject: ${ticket.subject}

The following message was attached:

""".trimIndent() + ticket.message.lines().joinToString("\n") { "> $it" }

        attemptSend(message)
    }

    private suspend fun attemptSend(message: String) {
        log.debug("Attempting to send notification:\n${message}")

        var retries = 0
        while (true) {
            retries++
            if (retries == 3) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
            }
            val postResult = try {
                httpClient.request(hook) {
                    method = HttpMethod.Post
                    setBody(TextContent(
                        defaultMapper.encodeToString(SlackMessage(message)),
                        ContentType.Application.Json
                    ))
                }
            } catch (ex: Exception) {
                when (ex) {
                    is ConnectException -> {
                        log.debug("Java.net.Connect Exception caught : ${ex.message}")

                    }
                    is FailToConnectException -> {
                        log.debug("Cio ConnectException caught : ${ex.message}")
                    }
                }
                continue
            }
            val status = postResult.status
            if (!status.isSuccess()) {
                log.warn("unsuccessful message from slack ($status)")
                runCatching { log.warn(postResult.bodyAsText()) }
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }
            return
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
