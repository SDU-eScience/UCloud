package mail.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.mail.services.Alert
import dk.sdu.cloud.mail.services.AlertNotifier
import dk.sdu.cloud.service.Loggable
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.cio.FailToConnectException
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.response
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.util.KtorExperimentalAPI
import org.slf4j.Logger

private data class SlackMessage(val text: String)

class SlackNotifier(
    val hook: String
) : AlertNotifier {
    private val httpClient = HttpClient()

    @OptIn(KtorExperimentalAPI::class)
    override suspend fun onAlert(alert: Alert) {
        val message = alert.message.lines().joinToString("\n") { "> $it" }

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
                        defaultMapper.writeValueAsString(SlackMessage(message)),
                        ContentType.Application.Json
                    )
                }
            } catch (ex: FailToConnectException) {
                log.debug("Connect Exception caught : ${ex.message}")
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
        override val log: Logger = logger()
    }
}
