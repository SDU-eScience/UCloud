package dk.sdu.cloud.zenodo.services

import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.zenodo.util.HttpClient
import dk.sdu.cloud.zenodo.util.asJson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.delay
import org.asynchttpclient.request.body.multipart.FilePart
import org.asynchttpclient.request.body.multipart.StringPart
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.util.concurrent.TimeoutException

sealed class ZenodoRPCException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode)

class MissingOAuthToken : ZenodoRPCException("Not connected to Zenodo", HttpStatusCode.Unauthorized)

class TooManyRetries : ZenodoRPCException("Unable to connect to Zenodo", HttpStatusCode.BadGateway)

data class ZenodoRPCFailure(
    val zenodoMessage: String,
    val status: Int,
    val errors: Map<String, Any>?
) : ZenodoRPCException("Error while communicating with Zenodo", HttpStatusCode.BadGateway)

class ZenodoRPCService(
    private val oauthService: ZenodoOAuth<*>
) {
    fun isConnected(user: String): Boolean {
        return oauthService.isConnected(user)
    }

    suspend fun validateToken(user: String, retries: Int = 0) {
        if (retries >= 5) throw TooManyRetries()

        val token =
            oauthService.retrieveTokenOrRefresh(user) ?: throw MissingOAuthToken()

        val rawResponse = try {
            HttpClient.get("${oauthService.baseUrl}/api/deposit/depositions") {
                setHeader(HttpHeaders.Authorization, "Bearer ${token.accessToken}")
            }
        } catch (ex: TimeoutException) {
            delay(500)
            return validateToken(user, retries + 1)
        }

        try {
            when (rawResponse.statusCode) {
                in 200..299 -> {
                    // All is good
                    return
                }

                HttpStatusCode.Unauthorized.value, HttpStatusCode.Forbidden.value -> {
                    oauthService.invalidateTokenForUser(user)
                    throw MissingOAuthToken()
                }

                in 500..599 -> {
                    delay(500)
                    return validateToken(user, retries + 1)
                }

                else -> {
                    throw rawResponse.asJson<ZenodoRPCFailure>()
                }
            }
        } catch (ex: Exception) {
            log.info("Caught an exception while trying to parse Zenodo entities!")
            log.info(ex.stackTraceToString())
            return validateToken(user, retries + 1)
        }
    }

    suspend fun createDeposition(user: String, retries: Int = 0): ZenodoDepositionEntity {
        if (retries >= 5) throw TooManyRetries()

        val token =
            oauthService.retrieveTokenOrRefresh(user) ?: throw MissingOAuthToken()

        val rawResponse = try {
            HttpClient.post("${oauthService.baseUrl}/api/deposit/depositions") {
                setHeader(HttpHeaders.Authorization, "Bearer ${token.accessToken}")
                setHeader("Content-Type", "application/json")
                setBody("{}")
            }
        } catch (ex: TimeoutException) {
            delay(500)
            return createDeposition(user, retries + 1)
        }

        return try {
            when (rawResponse.statusCode) {
                in 200..299 -> {
                    rawResponse.asJson()
                }

                HttpStatusCode.Unauthorized.value, HttpStatusCode.Forbidden.value -> {
                    oauthService.invalidateTokenForUser(user)
                    throw MissingOAuthToken()
                }

                in 500..599 -> {
                    delay(500)
                    return createDeposition(user, retries + 1)
                }

                else -> {
                    throw rawResponse.asJson<ZenodoRPCFailure>()
                }
            }
        } catch (ex: Exception) {
            log.info("Caught an exception while trying to parse Zenodo entities!")
            log.info(ex.stackTraceToString())
            return createDeposition(user, retries + 1)
        }
    }

    suspend fun createUpload(
        user: String,
        depositionId: String,
        fileName: String,
        filePart: File,
        retries: Int = 0
    ) {
        if (retries >= 5) throw TooManyRetries()

        val token = oauthService.retrieveTokenOrRefresh(user) ?: throw MissingOAuthToken()

        val response = try {
            HttpClient.post("${oauthService.baseUrl}/api/deposit/depositions/$depositionId/files") {
                setHeader(HttpHeaders.Authorization, "Bearer ${token.accessToken}")
                addBodyPart(StringPart("filename", fileName))
                addBodyPart(FilePart("file", filePart, null, null, fileName))
            }
        } catch (ex: TimeoutException) {
            delay(500)
            return createUpload(user, depositionId, fileName, filePart, retries + 1)
        }

        return try {
            when (response.statusCode) {
                in 200..299 -> {
                    // Do nothing, all is good.
                }

                HttpStatusCode.Unauthorized.value, HttpStatusCode.Forbidden.value -> {
                    oauthService.invalidateTokenForUser(user)
                    throw MissingOAuthToken()
                }

                in 500..599 -> {
                    delay(500)
                    return createUpload(user, depositionId, fileName, filePart, retries + 1)
                }

                else -> {
                    throw response.asJson<ZenodoRPCFailure>()
                }
            }
        } catch (ex: Exception) {
            log.info("Caught an exception while trying to parse Zenodo entities!")
            log.info(ex.stackTraceToString())
            return createUpload(user, depositionId, fileName, filePart, retries + 1)
        }
    }

    fun createAuthorizationUrl(user: String, returnTo: String): URL {
        return oauthService.createAuthorizationUrl(user, returnTo, "deposit:write")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ZenodoRPCService::class.java)
    }
}

data class ZenodoDepositionEntity(
    val created: String,
    val files: List<Any>,
    val id: String,
    val links: ZenodoDepositionLinks,
    val metadata: ZenodoDepositionMetadata,
    val modified: String,
    val owner: Long,
    val record_id: Long,
    val state: String,
    val submitted: Boolean,
    val title: String
)

data class ZenodoDepositionLinks(
    val discard: String?,
    val edit: String?,
    val files: String?,
    val publish: String?,
    val newversion: String?,
    val self: String?
)

typealias ZenodoDepositionMetadata = Map<String, Any?>


