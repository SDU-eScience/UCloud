package dk.sdu.cloud.plugins

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class PuhuriCreateProjectRequest(
    @SerialName("backend_id")
    val backendId: String,
    val customer: String,
    val description: String,
    val name: String,

    @SerialName("oecs_fos_2007_code")
    val oecsFos2007Code: String
)


class PuhuriPlugin {
    private val apiToken = "Token "
    private val rootEndPoint = "https://puhuri-core-beta.neic.no/api/"
    private val customer = "https://puhuri-core-beta.neic.no/api/customers/579f3e4d309a4b208026e784bf0775a3/"
    private val offering = "https://puhuri-core-beta.neic.no/api/marketplace-offerings/5c93748e796b47eaaec0805153e66fb4/"
    private val plan = "https://puhuri-core-beta.neic.no/api/marketplace-plans/a274fc378464423390bf596991e10328/"
    private val httpClient = HttpClient(CIO) {
        expectSuccess = false
    }

    suspend fun approveGrant() {
        createProject()

        // TODO Add user as PI
        // TODO Create Offering
    }

    private suspend fun createOffering() {

    }

    private suspend fun createProject() {
        val payload = PuhuriCreateProjectRequest(
            "UCLOUD_TEST_API_ID",
            customer,
            "Test description",
            "Test name API",
            "1.1"
        )

        val resp = httpClient.post(apiEndPoint("projects"), apiRequestWithBody(payload))
        log.debug("Puhuri createProject response: ${resp.status} ${resp.body<String>()}")
    }


    fun apiEndPoint(path: String): String {
        return rootEndPoint + path.removePrefix("/").removeSuffix("/") + "/"
    }

    private inline fun <reified T> apiRequestWithBody(
        payload: T?
    ): HttpRequestBuilder.() -> Unit {
        return {
            if (payload != null) {
                setBody(
                    TextContent(
                        defaultMapper.encodeToString(payload),
                        ContentType.Application.Json
                    )
                )
            }

            headers {
                append("Authorization", apiToken)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}