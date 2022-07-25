package dk.sdu.cloud.plugins

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.service.Loggable
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.*
import kotlinx.datetime.Instant

class PuhuriPlugin {
    private val rootEndPoint = "https://puhuri-core-beta.neic.no/api/"
    private val customer = "https://puhuri-core-beta.neic.no/api/customers/579f3e4d309a4b208026e784bf0775a3/"
    private val offering = "https://puhuri-core-beta.neic.no/api/marketplace-offerings/5c93748e796b47eaaec0805153e66fb4/"
    private val plan = "https://puhuri-core-beta.neic.no/api/marketplace-plans/a274fc378464423390bf596991e10328/"
    private val httpClient = HttpClient(CIO) {
        expectSuccess = false
    }

    // NOTE(Brian): For test purposes
    suspend fun approveGrant() {
        // Test values
        val id = "UCLOUD_TEST_API_ID"
        val name = "Test name API"
        val description = "Test description"
        val projectUrl = "https://puhuri-core-beta.neic.no/api/projects/3d57c5bca6ee413badbf974629872f9c/"
        val projectId = "3d57c5bca6ee413badbf974629872f9c"

        /*
        val project = createProject(id, name, description)
        val userId = getUserId("dca0a8ca-652b-4e5d-aa6b-caa60ea8fff9@myaccessid.org")

        if (userId != null) {
            setProjectPermission(userId, project.uuid, ProjectRole.PI)
        }

        createOrder(projectUrl, PuhuriAllocation(0, 0, 0))
        */
    }

    private suspend fun getUserId(username: String): String? {
        return try {
            val resp = httpClient.post(
                apiPath("remote-eduteams"),
                apiRequestWithBody(PuhuriGetUserIdRequest(username))
            )
            defaultMapper.decodeFromString(PuhuriGetUserIdResponse.serializer(), resp.body()).uuid
        } catch (e: Exception) {
            log.error("Failed to find UUID for user")
            null
        }
    }

    private suspend fun createOrder(projectUrl: String, allocation: PuhuriAllocation) {
        val payload = PuhuriCreateOrderRequest(
            projectUrl,
            listOf(
                PuhuriOrderItem(
                    offering,
                    PuhuriOrderItemAttributes("UCloud allocation"),
                    plan,
                    allocation
                )
            )
        )

        log.debug("Creating order")
        val resp = httpClient.post(apiPath("marketplace-orders"), apiRequestWithBody(payload))
        log.debug("Puhuri createOrder response: ${resp.status}")
    }

    private suspend fun setProjectPermission(userId: String, projectId: String, role: ProjectRole) {
        val puhuriRole = when (role) {
            ProjectRole.PI -> "manager"
            ProjectRole.ADMIN -> "admin"
            else -> "member"
        }

        val projectUrl = "${rootEndPoint}projects/$projectId/"
        val userUrl = "${rootEndPoint}users/$userId/"

        val payload = PuhuriSetProjectPermissionRequest(
            userUrl,
            projectUrl,
            puhuriRole
        )

        // NOTE(Brian): Requires deletion of old entry if it exists
        removeProjectPermission(userId, projectId)

        val resp = httpClient.post(apiPath("project-permissions"), apiRequestWithBody(payload))
        log.debug("Puhuri setProjectPermissions response: ${resp.status}: ${resp.body<String>()}")
    }

    private suspend fun listProjectPermissions(projectId: String): List<PuhuriProjectPermissionEntry> {
        if (projectId.isEmpty()) {
            log.error("projectId cannot be empty")
            return emptyList()
        }

        val resp = httpClient.get(apiPath("project-permissions") + "?project=$projectId", apiRequest())
        return defaultMapper.decodeFromString(resp.body())
    }
    private suspend fun removeProjectPermission(userId: String, projectId: String) {
        val lookup = listProjectPermissions(projectId).firstOrNull { it.userId == userId } ?: return
        removeProjectPermission(lookup.pk)
    }

    private suspend fun removeProjectPermission(pk: Int) {
        httpClient.delete(apiPath("project-permissions/${pk}"), apiRequest())
    }

    private suspend fun createProject(id: String, name: String, description: String): PuhuriCreateProjectResponse {
        val payload = PuhuriCreateProjectRequest(
            id,
            customer,
            description,
            name,
            "1.1"
        )

        val resp = httpClient.post(apiPath("projects"), apiRequestWithBody(payload))
        log.debug("Puhuri createProject response: ${resp.status}: ${resp.body<String>()}")
        return defaultMapper.decodeFromJsonElement(PuhuriCreateProjectResponse.serializer(), resp.body())
    }


    private fun apiPath(path: String): String {
        return rootEndPoint + path.removePrefix("/").removeSuffix("/") + "/"
    }

    private fun apiRequest(): HttpRequestBuilder.() -> Unit {
        return apiRequestWithBody<Unit>(null)
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

@Serializable
data class PuhuriCreateProjectResponse(
    val url: String,
    val uuid: String,
    val name: String,
    val customer: String,

    @SerialName("customer_uuid")
    val customerId: String,

    @SerialName("customer_name")
    val customerName: String,

    @SerialName("customer_native_name")
    val customerNativeName: String,

    @SerialName("customer_abbreviation")
    val customerAbbreviation: String,

    val description: String,
    val created: Instant,
    val type: String?,

    @SerialName("backend_id")
    val backendId: String,

    @SerialName("end_date")
    val endDate: Instant?,

    @SerialName("oecd_fos_2007_code")
    val oecsFos2007Code: String?,

    @SerialName("is_industry")
    val isIndustry: Boolean,

    val image: String,

    //@SerialName("marketplace_resource_count")
    //val marketplaceResourceCount: MarketplaceResourceCount,

    @SerialName("billing_price_estimate")
    val billingPriceEstimate: PuhuriBillingPriceEstimate,
)

@Serializable
data class PuhuriBillingPriceEstimate(
    val total: Float,
    val current: Float,
    val tax: Float,

    @SerialName("tax_current")
    val taxCurrent: Float
)

@Serializable
data class PuhuriCreateOrderRequest(
    val project: String,
    val items: List<PuhuriOrderItem>
)

@Serializable
data class PuhuriOrderItem(
    val offering: String,
    val attributes: PuhuriOrderItemAttributes,
    val plan: String,
    val limits: PuhuriAllocation,
)

@Serializable
data class PuhuriOrderItemAttributes(
    val name: String
)

@Serializable
data class PuhuriAllocation(
    @SerialName("gb_k_hours")
    val gbKHours: Int,
    @SerialName("gpu_hours")
    val gpuHours: Int,
    @SerialName("cpu_k_hours")
    val cpuKHours: Int
)

@Serializable
data class PuhuriGetUserIdRequest(
    val cuid: String,
)

@Serializable
data class PuhuriGetUserIdResponse(
    val uuid: String
)

@Serializable
data class PuhuriSetProjectPermissionRequest(
    val user: String,
    val project: String,
    val role: String
)

@Serializable
data class PuhuriProjectPermissionEntry(
    val url: String,
    val pk: Int,
    val role: String, // TODO

    val created: Instant,

    @SerialName("expiration_time")
    val expiration: Instant?,

    @SerialName("created_by")
    val createdBy: String,

    @SerialName("project")
    val projectUrl: String,

    @SerialName("project_uuid")
    val projectId: String,

    @SerialName("project_name")
    val projectName: String,

    @SerialName("customer_name")
    val customerName: String,

    @SerialName("user")
    val userUrl: String,

    @SerialName("user_full_name")
    val userFullName: String,

    @SerialName("user_native_name")
    val userNativeName: String,

    @SerialName("user_username")
    val userUsername: String,

    @SerialName("user_uuid")
    val userId: String,

    @SerialName("user_email")
    val userEmail: String,
)