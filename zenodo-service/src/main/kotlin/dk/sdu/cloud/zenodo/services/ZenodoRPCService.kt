package dk.sdu.cloud.zenodo.services

import com.auth0.jwt.interfaces.DecodedJWT
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

sealed class ZenodoRPCException : RuntimeException() {
    abstract override val message: String
}

class MissingOAuthToken : ZenodoRPCException() {
    override val message = "Missing OAuth Token!"
}

class TooManyRetries : ZenodoRPCException() {
    override val message = "Too many retries!"
}

data class ZenodoRPCFailure(
    override val message: String,
    val status: Int,
    val errors: Map<String, Any>?
) : ZenodoRPCException()

class ZenodoRPCService(
    private val oauthService: ZenodoOAuth
) {
    fun isConnected(jwt: DecodedJWT): Boolean {
        return oauthService.isConnected(jwt.subject)
    }

    suspend fun validateToken(jwt: DecodedJWT, retries: Int = 0) {
        if (retries >= 5) throw TooManyRetries()

        val token =
            oauthService.retrieveTokenOrRefresh(jwt.subject) ?: throw MissingOAuthToken()

        val rawResponse = try {
            HttpClient.get("${oauthService.baseUrl}/api/deposit/depositions") {
                setHeader(HttpHeaders.Authorization, "Bearer ${token.accessToken}")
            }
        } catch (ex: TimeoutException) {
            delay(500)
            return validateToken(jwt, retries + 1)
        }

        try {
            when (rawResponse.statusCode) {
                in 200..299 -> {
                    // All is good
                    return
                }

                HttpStatusCode.Unauthorized.value, HttpStatusCode.Forbidden.value -> {
                    oauthService.invalidateTokenForUser(jwt.subject)
                    throw MissingOAuthToken()
                }

                in 500..599 -> {
                    delay(500)
                    return validateToken(jwt, retries + 1)
                }

                else -> {
                    throw rawResponse.asJson<ZenodoRPCFailure>()
                }
            }
        } catch (ex: Exception) {
            log.info("Caught an exception while trying to parse Zenodo entities!")
            log.info(ex.stackTraceToString())
            return validateToken(jwt, retries + 1)
        }
    }

    suspend fun createDeposition(jwt: DecodedJWT, retries: Int = 0): ZenodoDepositionEntity {
        if (retries >= 5) throw TooManyRetries()

        val token =
            oauthService.retrieveTokenOrRefresh(jwt.subject) ?: throw MissingOAuthToken()

        val rawResponse = try {
            HttpClient.post("${oauthService.baseUrl}/api/deposit/depositions") {
                setHeader(HttpHeaders.Authorization, "Bearer ${token.accessToken}")
                setHeader("Content-Type", "application/json")
                setBody("{}")
            }
        } catch (ex: TimeoutException) {
            delay(500)
            return createDeposition(jwt, retries + 1)
        }

        return try {
            when (rawResponse.statusCode) {
                in 200..299 -> {
                    rawResponse.asJson()
                }

                HttpStatusCode.Unauthorized.value, HttpStatusCode.Forbidden.value -> {
                    oauthService.invalidateTokenForUser(jwt.subject)
                    throw MissingOAuthToken()
                }

                in 500..599 -> {
                    delay(500)
                    return createDeposition(jwt, retries + 1)
                }

                else -> {
                    throw rawResponse.asJson<ZenodoRPCFailure>()
                }
            }
        } catch (ex: Exception) {
            log.info("Caught an exception while trying to parse Zenodo entities!")
            log.info(ex.stackTraceToString())
            return createDeposition(jwt, retries + 1)
        }
    }

    suspend fun createUpload(
        jwt: DecodedJWT,
        depositionId: String,
        fileName: String,
        filePart: File,
        retries: Int = 0
    ) {
        if (retries >= 5) throw TooManyRetries()

        val token = oauthService.retrieveTokenOrRefresh(jwt.subject) ?: throw MissingOAuthToken()

        val response = try {
            HttpClient.post("${oauthService.baseUrl}/api/deposit/depositions/$depositionId/files") {
                setHeader(HttpHeaders.Authorization, "Bearer ${token.accessToken}")
                addBodyPart(StringPart("filename", fileName))
                addBodyPart(FilePart("file", filePart, null, null, fileName))
            }
        } catch (ex: TimeoutException) {
            delay(500)
            return createUpload(jwt, depositionId, fileName, filePart, retries + 1)
        }

        return try {
            when (response.statusCode) {
                in 200..299 -> {
                    // Do nothing, all is good.
                }

                HttpStatusCode.Unauthorized.value, HttpStatusCode.Forbidden.value -> {
                    oauthService.invalidateTokenForUser(jwt.subject)
                    throw MissingOAuthToken()
                }

                in 500..599 -> {
                    delay(500)
                    return createUpload(jwt, depositionId, fileName, filePart, retries + 1)
                }

                else -> {
                    throw response.asJson<ZenodoRPCFailure>()
                }
            }
        } catch (ex: Exception) {
            log.info("Caught an exception while trying to parse Zenodo entities!")
            log.info(ex.stackTraceToString())
            return createUpload(jwt, depositionId, fileName, filePart, retries + 1)
        }
    }

    fun createAuthorizationUrl(jwt: DecodedJWT, returnTo: String): URL {
        return oauthService.createAuthorizationUrl(jwt.subject, returnTo, "deposit:write")
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


