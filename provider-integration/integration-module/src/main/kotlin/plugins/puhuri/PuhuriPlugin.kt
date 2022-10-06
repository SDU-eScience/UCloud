package dk.sdu.cloud.plugins.puhuri

import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.cli.CliHandler
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.controllers.ResourceOwnerWithId
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.debug.enterContext
import dk.sdu.cloud.debugSystem
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.logThrowable
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.plugins.connection.OpenIdConnectPlugin
import dk.sdu.cloud.plugins.connection.OpenIdConnectSubject
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.provider.api.IntegrationControl
import dk.sdu.cloud.provider.api.IntegrationControlApproveConnectionRequest
import dk.sdu.cloud.sql.bindStringNullable
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.sendTerminalMessage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlin.math.ceil
import kotlin.system.exitProcess

class PuhuriPlugin : ProjectPlugin {
    override val pluginTitle: String = "Puhuri"

    private lateinit var openIdConnectPlugin: OpenIdConnectPlugin

    private lateinit var pluginConfig: ConfigSchema.Plugins.Projects.Puhuri
    private lateinit var puhuri: PuhuriClient
    private lateinit var pluginContext: PluginContext

    override fun configure(config: ConfigSchema.Plugins.Projects) {
        this.pluginConfig = config as ConfigSchema.Plugins.Projects.Puhuri
        this.puhuri = PuhuriClient(pluginConfig.endpoint, pluginConfig.customerId, pluginConfig.offeringId,
            pluginConfig.planId, pluginConfig.apiToken)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun PluginContext.initialize() {
        pluginContext = this

        addDevelopmentCommandLine()
        if (!config.shouldRunServerCode()) return

        openIdConnectPlugin = config.plugins.connection as? OpenIdConnectPlugin
            ?: error("OpenIdConnectPlugin must be registered with Puhuri plugin")

        openIdConnectPlugin.registerOnConnectionCompleteCallback(::onConnectionComplete)

        GlobalScope.launch {
            runCatching { attemptProjectSynchronize() }
        }
    }

    override fun supportsRealUserMode(): Boolean = false
    override fun supportsServiceUserMode(): Boolean = true

    // NOTE(Dan): Since this requires "service user mode" mode, these just go away.
    override suspend fun PluginContext.lookupLocalId(ucloudId: String): Int? = null
    override suspend fun PluginContext.onUserMappingInserted(ucloudId: String, localId: Int) {
        // Do nothing
    }

    override suspend fun PluginContext.onProjectUpdated(newProject: dk.sdu.cloud.project.api.v2.Project) {
        val puhuriProject = puhuri.lookupProject(newProject.id) ?: puhuri.createProject(
            newProject.id,
            newProject.specification.title,
            "Puhuri project imported from UCloud"
        )

        data class PuhuriProjectUser(
            val ucloudId: String,
            val puhuriId: String,
            val role: ProjectRole?,
            val isSynchronized: Boolean
        )

        val existingUsers = run {
            val users = ArrayList<PuhuriProjectUser>()

            dbConnection.withSession { session ->
                session.prepareStatement(
                    //language=postgresql
                    """
                        select ucloud_identity, puhuri_identity, ucloud_project_role, synchronized_to_puhuri
                        from puhuri_project_users
                        where
                            ucloud_project = :ucloud_project and
                            puhuri_identity is not null
                    """
                ).useAndInvoke(
                    prepare = {
                        bindString("ucloud_project", newProject.id)
                    },
                    readRow = { row ->
                        users.add(
                            PuhuriProjectUser(
                                row.getString(0)!!,
                                row.getString(1)!!,
                                row.getString(2)?.let { ProjectRole.valueOf(it) },
                                row.getBoolean(3)!!
                            )
                        )
                    }
                )
            }

            users
        }

        val members = newProject.status.members ?: emptyList()
        for (member in members) {
            val existingUser = existingUsers.find { it.ucloudId == member.username }
            val shouldSynchronize = when {
                existingUser == null -> true
                existingUser.role != member.role -> true
                else -> false
            }

            if (!shouldSynchronize) continue

            val puhuriId = existingUser?.puhuriId ?: run {
                var puhuriId: String? = null
                dbConnection.withSession { session ->
                    session.prepareStatement(
                        //language=postgresql
                        """
                            select puhuri_identity
                            from puhuri_connections
                            where ucloud_identity = :ucloud_username
                        """
                    ).useAndInvoke(
                        prepare = { bindString("ucloud_username", member.username) },
                        readRow = { row -> puhuriId = row.getString(0) }
                    )
                }

                puhuriId
            }

            pushUserToProject(member.username, puhuriId, newProject.id, puhuriProject.uuid, member.role)
        }

        for (existing in existingUsers) {
            if (members.none { it.username == existing.ucloudId }) {
                pushUserToProject(existing.ucloudId, existing.puhuriId, newProject.id, puhuriProject.uuid, null)
            }
        }
    }

    private suspend fun pushUserToProject(
        ucloudUser: String,
        puhuriUser: String?,
        ucloudProject: String,
        puhuriProject: String,
        newRoleOrNullIfDeleted: ProjectRole?,
    ) {
        try {
            // Immediately write down the changes and mark it as not synchronized. If we fail to push to Puhuri, then
            // we can re-attempt these changes later.
            dbConnection.withSession { session ->
                session.prepareStatement(
                    //language=postgresql
                    """
                        insert into puhuri_project_users
                            (ucloud_identity, ucloud_project, puhuri_identity, ucloud_project_role, synchronized_to_puhuri)
                        values
                            (:ucloud_user, :ucloud_project, :puhuri_identity::text, :role::text, false)
                        on conflict (ucloud_identity, ucloud_project) do update set
                            synchronized_to_puhuri = excluded.synchronized_to_puhuri,
                            ucloud_project_role = excluded.ucloud_project_role,
                            puhuri_identity = excluded.puhuri_identity 
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("ucloud_user", ucloudUser)
                        bindString("ucloud_project", ucloudProject)
                        bindStringNullable("puhuri_identity", puhuriUser)
                        bindStringNullable("role", newRoleOrNullIfDeleted?.name)
                    }
                )
            }

            val puhuriResolvedUid = if (puhuriUser != null) puhuri.lookupPuhuriUserFromCuid(puhuriUser) else return

            if (newRoleOrNullIfDeleted != null) {
                puhuri.addUserToProject(puhuriResolvedUid, puhuriProject, newRoleOrNullIfDeleted)

                dbConnection.withSession { session ->
                    session.prepareStatement(
                        //language=postgresql
                        """
                            update puhuri_project_users
                            set
                                synchronized_to_puhuri = true
                            where
                                ucloud_identity = :ucloud_user and
                                ucloud_project = :ucloud_project
                        """
                    ).useAndInvokeAndDiscard(
                        prepare = {
                            bindString("ucloud_user", ucloudUser)
                            bindString("ucloud_project", ucloudProject)
                        }
                    )
                }
            } else {
                puhuri.removeUserFromProject(puhuriResolvedUid, puhuriProject)

                dbConnection.withSession { session ->
                    session.prepareStatement(
                        //language=postgresql
                        """
                            delete from puhuri_project_users
                            where
                                ucloud_identity = :ucloud_user and
                                ucloud_project = :ucloud_project
                        """
                    ).useAndInvokeAndDiscard(
                        prepare = {
                            bindString("ucloud_user", ucloudUser)
                            bindString("ucloud_project", ucloudProject)
                        }
                    )
                }
            }
        } catch (ex: Throwable) {
            debugSystem.logThrowable(
                "Failed to push user to project: $ucloudUser, $puhuriUser, $puhuriProject, $newRoleOrNullIfDeleted.\n",
                ex
            )
        }
    }

    private suspend fun onConnectionComplete(subject: OpenIdConnectSubject) {
        val ucloudUser = subject.ucloudIdentity
        val puhuriUserId = subject.subject

        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    insert into puhuri_connections(ucloud_identity, puhuri_identity) 
                    values (:ucloud_identity, :puhuri_identity)
                    on conflict (ucloud_identity) do update set
                        puhuri_identity = excluded.puhuri_identity;
                """
            ).useAndInvokeAndDiscard {
                bindString("ucloud_identity", ucloudUser)
                bindString("puhuri_identity", puhuriUserId)
            }

            // NOTE(Dan): This doesn't actually remove the old Puhuri user from existing projects.
            //  Maybe this is something we should do also?

            session.prepareStatement(
                //language=postgresql
                """ 
                    update puhuri_project_users
                    set
                        puhuri_identity = :puhuri_identity,
                        synchronized_to_puhuri = case when (puhuri_identity = :puhuri_identity and synchronized_to_puhuri)
                            then TRUE
                            else FALSE
                        end
                    where
                        ucloud_identity = :ucloud_identity 
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("puhuri_identity", puhuriUserId)
                    bindString("ucloud_identity", ucloudUser)
                }
            )
        }

        attemptProjectSynchronize(usernameFilter = ucloudUser)

        IntegrationControl.approveConnection.call(
            IntegrationControlApproveConnectionRequest(subject.ucloudIdentity),
            pluginContext.rpcClient
        ).orThrow()
    }

    private object Ipc : IpcContainer("puhuri") {
        val connect = updateHandler("connect", ConnectRequest.serializer(), Unit.serializer())

        @Serializable
        data class ConnectRequest(val ucloudUsername: String, val puhuriCuid: String)
    }

    private suspend fun PluginContext.addDevelopmentCommandLine() {
        if (config.shouldRunAnyPluginCode()) {
            fun printUsage(): Nothing {
                sendTerminalMessage {
                    bold { inline("Usage: ") }
                    code { inline("ucloud puhuri connect <ucloudUsername> <puhuriCuid>") }
                }

                exitProcess(0)
            }

            (commandLineInterface ?: return).addHandler(CliHandler("puhuri") { args ->
                if (args.isEmpty()) printUsage()

                when(args[0]) {
                    "connect" -> {
                        if (args.size != 3) printUsage()
                        try {
                            ipcClient.sendRequest(Ipc.connect, Ipc.ConnectRequest(args[1], args[2]))
                            sendTerminalMessage { line("OK") }
                        } catch (ex: RPCException) {
                            sendTerminalMessage {
                                bold { red { inline("Error! ") } }
                                inline(ex.why)
                            }
                        } catch (ex: Throwable) {
                            ex.printStackTrace()
                        }
                    }

                    else -> printUsage()
                }

                exitProcess(0)
            })
        } else if (config.shouldRunServerCode()) {
            ipcServer.addHandler(Ipc.connect.handler { user, request ->
                if (user.uid != 0) throw RPCException("You must run this command as root", HttpStatusCode.Forbidden)

                onConnectionComplete(
                    OpenIdConnectSubject(
                        request.ucloudUsername,
                        request.puhuriCuid,
                        email = request.puhuriCuid
                    )
                )
            })
        }
    }

    private suspend fun attemptProjectSynchronize(usernameFilter: String? = null) {
        data class PuhuriProjectUser(
            val ucloudId: String,
            val ucloudProject: String,
            val puhuriId: String,
            val ucloudRole: ProjectRole?
        )

        val missingSynchronizations = ArrayList<PuhuriProjectUser>()

        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    select ucloud_identity, ucloud_project, puhuri_identity, ucloud_project_role
                    from puhuri_project_users
                    where
                        synchronized_to_puhuri = false and
                        (:username_filter::text is null or ucloud_identity = :username_filter::text) and
                        puhuri_identity is not null
                """
            ).useAndInvoke(
                prepare = { bindStringNullable("username_filter", usernameFilter) },
                readRow = {
                    missingSynchronizations.add(
                        PuhuriProjectUser(
                            it.getString(0)!!,
                            it.getString(1)!!,
                            it.getString(2)!!,
                            it.getString(3)?.let { ProjectRole.valueOf(it) }
                        )
                    )
                }
            )
        }

        val puhuriProjectCache = missingSynchronizations.map { it.ucloudProject }.toSet().mapNotNull { project ->
            project to (puhuri.lookupProject(project) ?: return@mapNotNull null)
        }.toMap()

        for (sync in missingSynchronizations) {
            val project = puhuriProjectCache[sync.ucloudProject] ?: continue
            pushUserToProject(sync.ucloudId, sync.puhuriId, sync.ucloudProject, project.uuid, sync.ucloudRole)
        }
    }

    suspend fun onAllocations(allocations: List<DetailedAllocationNotification>) {
        // NOTE(Dan): This function is invoked both when new allocations arrive and when we are synchronizing
        // allocations. This function starts by throwing away a lot of information which are not used. That is because
        // we currently support Puhuri in a very limited fashion. We assume we can only allocate one type of CPU, one
        // type of GPU and one type of storage. All other information is discarded.

        val alreadySynchronized = HashMap<String, Boolean>()
        dbConnection.withSession { session ->
            for (alloc in allocations) {
                session.prepareStatement(
                    //language=postgresql
                    """
                        insert into puhuri_allocations(allocation_id, balance, product_type, synchronized_to_puhuri)
                        values (:id, :balance, :product_type, false)
                        on conflict (allocation_id) do update set 
                            synchronized_to_puhuri = synchronized_to_puhuri
                        returning allocation_id, synchronized_to_puhuri
                    """
                ).useAndInvoke(
                    prepare = {
                        bindString("id", alloc.allocationId)
                        bindLong("balance", alloc.balance)
                        bindString("product_type", alloc.productType.name)
                    },
                    readRow = { row -> alreadySynchronized[row.getString(0)!!] = row.getBoolean(1)!! }
                )
            }
        }

        for ((owner, allocs) in allocations.groupBy { it.owner }) {
            val projectId = (owner as? ResourceOwnerWithId.Project)?.projectId ?: continue
            val relevantAllocations = allocs.filter { alreadySynchronized[it.allocationId] != true }
            if (relevantAllocations.isEmpty()) continue

            val cpuAllocation = relevantAllocations
                .find {
                    it.productType == ProductType.COMPUTE && !it.productCategory.contains("gpu", ignoreCase = true)
                }

            val gpuAllocation = relevantAllocations
                .find {
                    it.productType == ProductType.COMPUTE && it.productCategory.contains("gpu", ignoreCase = true)
                }

            val storageAllocation = relevantAllocations
                .find { it.productType == ProductType.STORAGE }

            try {
                val puhuriProjectId = puhuri.lookupProject(projectId)?.uuid ?: continue
                puhuri.createOrder(
                    puhuriProjectId,
                    PuhuriAllocation(
                        cpuKHours = ceil((cpuAllocation?.balance ?: 0) / 1000.0).toInt(),
                        gpuHours = (gpuAllocation?.balance ?: 0).toInt(),
                        gbKHours = ceil((storageAllocation?.balance ?: 0) / 1000.0).toInt(),
                    )
                )

                dbConnection.withSession { session ->
                    session.prepareStatement(
                        //language=postgresql
                        """
                            update puhuri_allocations
                            set synchronized_to_puhuri = true
                            where
                                   allocation_id = :cpu_allocation::text
                                or allocation_id = :gpu_allocation::text
                                or allocation_id = :storage_allocation::text
                        """
                    ).useAndInvokeAndDiscard(
                        prepare = {
                            bindStringNullable("cpu_allocation", cpuAllocation?.allocationId)
                            bindStringNullable("gpu_allocation", gpuAllocation?.allocationId)
                            bindStringNullable("storage_allocation", storageAllocation?.allocationId)
                        }
                    )
                }
            } catch (ex: Throwable) {
                debugSystem.logThrowable("Failed to synchronize allocation: $allocs $projectId", ex)
            }
        }
    }
}

class PuhuriAllocationPlugin : AllocationPlugin {
    override val pluginTitle: String = "Puhuri"
    private lateinit var puhuriPlugin: PuhuriPlugin

    override fun supportsRealUserMode(): Boolean = false
    override fun supportsServiceUserMode(): Boolean = true

    override suspend fun PluginContext.initialize() {
        puhuriPlugin = config.plugins.projects as? PuhuriPlugin ?: run {
            throw IllegalStateException(
                "The Puhuri allocation plugin cannot be used without the corresponding " +
                        "project plugin"
            )
        }
    }

    override suspend fun PluginContext.onResourceAllocationDetailed(
        notifications: List<DetailedAllocationNotification>
    ): List<OnResourceAllocationResult> {
        puhuriPlugin.onAllocations(notifications)
        return notifications.map { OnResourceAllocationResult.ManageThroughUCloud }
    }

    override suspend fun PluginContext.onResourceSynchronizationDetailed(notifications: List<DetailedAllocationNotification>) {
        puhuriPlugin.onAllocations(notifications)
    }
}

class PuhuriFilePlugin : EmptyFilePlugin() {
    override val pluginTitle: String = "Puhuri"
    override fun supportsRealUserMode(): Boolean = false
    override fun supportsServiceUserMode(): Boolean = true
}

class PuhuriFileCollectionPlugin : EmptyFileCollectionPlugin() {
    override val pluginTitle = "Puhuri"
    override fun supportsRealUserMode(): Boolean = false
    override fun supportsServiceUserMode(): Boolean = true
}

class PuhuriComputePlugin : EmptyComputePlugin() {
    override val pluginTitle = "Puhuri"
    override fun supportsRealUserMode(): Boolean = false
    override fun supportsServiceUserMode(): Boolean = true
}

class PuhuriClient(
    endpoint: String,
    customerId: String,
    offeringId: String,
    planId: String,
    private val apiToken: String
) {
    private val rootEndpoint = endpoint.removeSuffix("/") + "/"
    private val customer = rootEndpoint + "customers/" + customerId + "/"
    private val offering = rootEndpoint + "marketplace-offerings/" + offeringId + "/"
    private val plan = rootEndpoint + "marketplace-plans/" + planId + "/"

    private val httpClient = HttpClient(CIO) {
        expectSuccess = false
    }

    suspend fun lookupProject(ucloudProjectId: String): PuhuriProject? {
        return debugSystem.enterContext("lookupProject $ucloudProjectId") {
            val resp = httpClient.get(
                apiPath("projects") + "?backend_id=$ucloudProjectId",
                apiRequest()
            ).orThrow()

            val results = defaultMapper.decodeFromString(ListSerializer(PuhuriProject.serializer()), resp.bodyAsText())

            if (results.isNotEmpty()) {
                logExit(
                    "Response",
                    defaultMapper.encodeToJsonElement(PuhuriProject.serializer().nullable, results.getOrNull(0)) as JsonObject,
                    MessageImportance.IMPLEMENTATION_DETAIL
                )

                results[0]
            } else {
                null
            }
        }
    }

    suspend fun lookupPuhuriUserFromCuid(cuid: String): String {
        val resp = httpClient.post(
            apiPath("remote-eduteams"),
            apiRequestWithBody(PuhuriGetUserIdRequest.serializer(), PuhuriGetUserIdRequest(cuid))
        ).orThrow()
        return defaultMapper.decodeFromString(PuhuriGetUserIdResponse.serializer(), resp.bodyAsText()).uuid
    }

    suspend fun createOrder(projectId: String, allocation: PuhuriAllocation) {
        httpClient.post(
            apiPath("marketplace-orders"),
            apiRequestWithBody(
                PuhuriCreateOrderRequest.serializer(),
                PuhuriCreateOrderRequest(
                    "${rootEndpoint}projects/$projectId/",
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

    suspend fun addUserToProject(userId: String, projectId: String, role: ProjectRole) {
        val puhuriRole = PuhuriProjectRole.values().find { it.ucloudRole == role } ?: PuhuriProjectRole.USER
        val projectUrl = "${rootEndpoint}projects/$projectId/"
        val userUrl = "${rootEndpoint}users/$userId/"

        // NOTE(Brian): Requires deletion of old entry if it exists
        removeUserFromProject(userId, projectId)

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

    suspend fun listProjectMembers(projectId: String): List<PuhuriProjectPermissionEntry> {
        if (projectId.isEmpty()) return emptyList()

        val resp = httpClient.get(apiPath("project-permissions") + "?project=$projectId", apiRequest()).orThrow()
        return defaultMapper.decodeFromString(ListSerializer(PuhuriProjectPermissionEntry.serializer()), resp.bodyAsText())
    }

    suspend fun removeUserFromProject(userId: String, projectId: String) {
        val lookup = listProjectMembers(projectId).firstOrNull { it.userId == userId } ?: return
        removeUserFromProjectByPk(lookup.pk)
    }

    private suspend fun removeUserFromProjectByPk(pk: Int) {
        httpClient.delete(apiPath("project-permissions/${pk}"), apiRequest()).orThrow()
    }

    suspend fun createProject(id: String, name: String, description: String): PuhuriProject {
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

        return defaultMapper.decodeFromString(PuhuriProject.serializer(), resp.bodyAsText())
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
                append("Authorization", "Token $apiToken")
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
