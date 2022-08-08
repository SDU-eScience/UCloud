package dk.sdu.cloud.plugins

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.service.Loggable
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class PuhuriPlugin : ProjectPlugin {
    override val pluginTitle: String = "Puhuri"

    private lateinit var pluginConfig: ConfigSchema.Plugins.Projects.Puhuri
    private lateinit var rootEndpoint: String
    private lateinit var customer: String
    private lateinit var offering: String
    private lateinit var plan: String

    private val httpClient = HttpClient(CIO) {
        expectSuccess = false
    }

    override fun configure(config: ConfigSchema.Plugins.Projects) {
        this.pluginConfig = config as ConfigSchema.Plugins.Projects.Puhuri

        this.rootEndpoint = pluginConfig.endpoint.removeSuffix("/") + "/"
        this.customer = rootEndpoint + "customers/" + pluginConfig.customerId + "/"
        this.offering = rootEndpoint + "marketplace-offerings/" + pluginConfig.offeringId + "/"
        this.plan = rootEndpoint + "marketplace-plans/" + pluginConfig.planId + "/"
    }

    // NOTE(Dan): Since this requires "no user instance" mode, these just go away.
    override suspend fun PluginContext.lookupLocalId(ucloudId: String): Int? = null
    override suspend fun PluginContext.onUserMappingInserted(ucloudId: String, localId: Int) {
        // Do nothing
    }

    override suspend fun PluginContext.onProjectUpdated(newProject: dk.sdu.cloud.project.api.v2.Project) {
        // TODO(Dan): Not yet implemented
    }

    /*
        puhuri.approveGrant(
            Project(
                title = "TestProjectPleaseIgnore2",
                id = "ID124",
                fullPath = "Full/path/TestProjectPleaseIgnore2",
                archived = false
            ),
            PuhuriAllocation(0, 0, 0),
            "dca0a8ca-652b-4e5d-aa6b-caa60ea8fff9@myaccessid.org"
        )
     */

    suspend fun approveGrant(project: Project, allocation: PuhuriAllocation, username: String) {
        val existingProject = projectLookup(project.id)

        val resolvedProject = if (existingProject == null) {
            val puhuriProject = createProject(project.id, project.title, project.fullPath ?: project.title)
            val userId = getUserId(username)
            setProjectPermission(userId, puhuriProject.uuid, ProjectRole.PI)

            puhuriProject
        } else {
            existingProject
        }

        createOrder(resolvedProject.url, allocation)
    }

    private suspend fun projectLookup(ucloudProjectId: String) : PuhuriProject? {
        val resp = httpClient.get(
            apiPath("projects") + "?backend_id=$ucloudProjectId",
            apiRequest()
        ).orThrow()

        val results = defaultMapper.decodeFromString(ListSerializer(PuhuriProject.serializer()), resp.body())

        return if (results.isNotEmpty()) {
            results[0]
        } else {
            null
        }
    }

    private suspend fun getUserId(username: String): String {
        log.debug("Getting user id")

        val resp = httpClient.post(
            apiPath("remote-eduteams"),
            apiRequestWithBody(PuhuriGetUserIdRequest.serializer(), PuhuriGetUserIdRequest(username))
        ).orThrow()
        return defaultMapper.decodeFromString(PuhuriGetUserIdResponse.serializer(), resp.body()).uuid
    }

    private suspend fun createOrder(projectUrl: String, allocation: PuhuriAllocation) {
        log.debug("Creating order")

        httpClient.post(
            apiPath("marketplace-orders"),
            apiRequestWithBody(
                PuhuriCreateOrderRequest.serializer(),
                PuhuriCreateOrderRequest(
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
            )
        ).orThrow()
    }

    private suspend fun setProjectPermission(userId: String, projectId: String, role: ProjectRole) {
        log.debug("Set project permission for user")

        val puhuriRole = when (role) {
            ProjectRole.PI -> PuhuriProjectRole.MANAGER
            ProjectRole.ADMIN -> PuhuriProjectRole.ADMIN
            else -> PuhuriProjectRole.USER
        }

        val projectUrl = "${rootEndpoint}projects/$projectId/"
        val userUrl = "${rootEndpoint}users/$userId/"

        // NOTE(Brian): Requires deletion of old entry if it exists
        removeProjectPermission(userId, projectId)

        httpClient.post(
            apiPath("project-permissions"),
            apiRequestWithBody(
                PuhuriSetProjectPermissionRequest.serializer(),
                PuhuriSetProjectPermissionRequest(
                    userUrl,
                    projectUrl,
                    puhuriRole
                )
            )
        ).orThrow()
    }

    private suspend fun listProjectPermissions(projectId: String): List<PuhuriProjectPermissionEntry> {
        if (projectId.isEmpty()) {
            log.error("projectId cannot be empty")
            return emptyList()
        }

        val resp = httpClient.get(apiPath("project-permissions") + "?project=$projectId", apiRequest()).orThrow()
        return defaultMapper.decodeFromString(resp.body())
    }

    private suspend fun removeProjectPermission(userId: String, projectId: String) {
        val lookup = listProjectPermissions(projectId).firstOrNull { it.userId == userId } ?: return
        removeProjectPermission(lookup.pk)
    }

    private suspend fun removeProjectPermission(pk: Int) {
        httpClient.delete(apiPath("project-permissions/${pk}"), apiRequest()).orThrow()
    }

    private suspend fun createProject(id: String, name: String, description: String): PuhuriProject {
        val resp = httpClient.post(
            apiPath("projects"),
            apiRequestWithBody(
                PuhuriCreateProjectRequest.serializer(),
                PuhuriCreateProjectRequest(
                    id,
                    customer,
                    description,
                    name,
                    null // TODO How should we set this?
                )
            )
        ).orThrow()

        return defaultMapper.decodeFromString(PuhuriProject.serializer(), resp.body())
    }

    private fun apiPath(path: String): String {
        return rootEndpoint + path.removePrefix("/").removeSuffix("/") + "/"
    }

    private fun apiRequest(): HttpRequestBuilder.() -> Unit {
        return apiRequestWithBody(Unit.serializer(), null)
    }

    private fun <T> apiRequestWithBody(
        serializer: KSerializer<T>,
        payload: T?
    ): HttpRequestBuilder.() -> Unit {
        return {
            if (payload != null) {
                setBody(
                    TextContent(
                        defaultMapper.encodeToString(serializer, payload),
                        ContentType.Application.Json
                    )
                )
            }

            headers {
                append("Authorization", "Token ${pluginConfig.apiToken}")
            }
        }
    }

    @OptIn(InternalAPI::class)
    private suspend fun HttpResponse.orThrow(): HttpResponse {
        if (!status.isSuccess()) {
            throw RPCException(
                content.toByteArray().toString(Charsets.UTF_8),
                HttpStatusCode.parse(status.value)
            )
        }
        return this
    }

    companion object : Loggable {
        override val log = logger()
    }
}

class PuhuriAllocationPlugin : AllocationPlugin {
    override val pluginTitle: String = "Puhuri (Allocations)"
    private lateinit var puhuriPlugin: PuhuriPlugin

    override suspend fun PluginContext.initialize() {
        puhuriPlugin = config.plugins.projects as? PuhuriPlugin ?: run {
            throw IllegalStateException("The Puhuri allocation plugin cannot be used without the corresponding " +
                    "project plugin")
        }
    }

    override suspend fun PluginContext.onResourceAllocation(
        notifications: List<AllocationNotification>
    ): List<OnResourceAllocationResult> {
        // TODO(Dan): Not yet implemented. Should dispatch to puhuriPlugin.
        return notifications.map { OnResourceAllocationResult.ManageThroughUCloud }
    }

    override suspend fun PluginContext.onResourceSynchronization(notifications: List<AllocationNotification>) {
        // TODO(Dan): Not yet implemented. Should dispatch to puhuriPlugin.
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
    val oecsFos2007Code: String?
)

@Serializable
data class PuhuriProject(
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

    val image: String?,

    @SerialName("marketplace_resource_count")
    val marketplaceResourceCount: Map<String, Int>,

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
    val role: PuhuriProjectRole
)

@Serializable
data class PuhuriProjectPermissionEntry(
    val url: String,
    val pk: Int,

    val role: PuhuriProjectRole,

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


@Serializable
enum class PuhuriProjectRole(val ucloudRole: ProjectRole) {
    @SerialName("manager")
    MANAGER(ProjectRole.PI),

    @SerialName("admin")
    ADMIN(ProjectRole.ADMIN),

    @SerialName("member")
    USER(ProjectRole.USER);
}
