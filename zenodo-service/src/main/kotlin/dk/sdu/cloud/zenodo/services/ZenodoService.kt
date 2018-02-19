package dk.sdu.cloud.zenodo.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.client.HttpClient
import dk.sdu.cloud.client.asJson
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.experimental.delay
import org.asynchttpclient.request.body.multipart.FilePart
import org.asynchttpclient.request.body.multipart.StringPart
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL

object MissingOAuthToken : RuntimeException("Missing OAuth Token!")
object TooManyRetries : RuntimeException("Too many retries")

class ZenodoService(
    private val oauthService: ZenodoOAuth
) {
    suspend fun createDeposition(jwt: DecodedJWT, retries: Int = 0): ZenodoResponse<ZenodoDepositionEntity> {
        if (retries == 5) return ZenodoResponse.Failure("Internal Server Error", 500, null)

        val token =
            oauthService.retrieveTokenOrRefresh(jwt.subject) ?: return ZenodoResponse.Failure("Unauthorized", 401, null)

        val rawResponse = HttpClient.post("${oauthService.baseUrl}/api/deposit/depositions") {
            setHeader(HttpHeaders.Authorization, "Bearer ${token.accessToken}")
            setHeader("Content-Type", "application/json")
            setBody("{}")
        }

        return try {
            when (rawResponse.statusCode) {
                in 200..299 -> {
                    ZenodoResponse.Success(rawResponse.asJson())
                }

                in 500..599 -> {
                    delay(500)
                    return createDeposition(jwt, retries + 1)
                }

                else -> {
                    rawResponse.asJson<ZenodoResponse.Failure<ZenodoDepositionEntity>>()
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
        filePart: File,
        retries: Int = 0
    ): ZenodoResponse<Unit> {
        if (retries == 5) throw TooManyRetries

        val token = oauthService.retrieveTokenOrRefresh(jwt.subject) ?: throw MissingOAuthToken

        val response = HttpClient.post("${oauthService.baseUrl}/api/deposit/depositions/$depositionId/files") {
            setHeader(HttpHeaders.Authorization, "Bearer ${token.accessToken}")
            addBodyPart(StringPart("filename", filePart.name))
            addBodyPart(FilePart("file", filePart))
        }

        return try {
            when (response.statusCode) {
                in 200..299 -> {
                    ZenodoResponse.Success(Unit)
                }

                in 500..599 -> {
                    delay(500)
                    return createUpload(jwt, depositionId, filePart, retries + 1)
                }

                else -> {
                    response.asJson<ZenodoResponse.Failure<Unit>>()
                }
            }
        } catch (ex: Exception) {
            log.info("Caught an exception while trying to parse Zenodo entities!")
            log.info(ex.stackTraceToString())
            return createUpload(jwt, depositionId, filePart, retries + 1)
        }
    }

    fun createAuthorizationUrl(jwt: DecodedJWT, returnTo: String): URL {
        return oauthService.createAuthorizationUrl(jwt.subject, returnTo, "deposit:write")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ZenodoService::class.java)
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


sealed class ZenodoResponse<T> {
    data class Success<T>(val result: T) : ZenodoResponse<T>()

    data class Failure<T>(
        val message: String,
        val status: Int,
        val errors: Map<String, Any>?
    ) : ZenodoResponse<T>()
}

/*
                        {
             files: [],
             community: null,
             uploadType: {
                 type: "Publication",
                 subtype: "Book",
             }, // Required
             basicInformation: { // Required
                 digitalObjectIdentifier: null,
                 publicationDate: null,
                 title: null,
                 authors: [{name: "", affiliation: "", orcid: ""}],
                 description: null,
                 version: null,
                 language: null,
                 keywords: null,
                 additionalNotes: null,
             },
             license: { // Required
                 accessRight: null, // Radio buttons
                 license: null // Dropdown
             },
             funding: { // Recommended
                 funder: null,
                 numberNameAbbr: null,
             },
             relatedAndAlternativeIdentifiers: { // Recommended
                 identifiers: [],
             }
                         */
