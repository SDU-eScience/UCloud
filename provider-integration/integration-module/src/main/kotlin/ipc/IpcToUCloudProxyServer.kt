package dk.sdu.cloud.ipc

import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobIncludeFlags
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.controllers.UserMapping
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsControl
import dk.sdu.cloud.project.api.v2.Project
import dk.sdu.cloud.project.api.v2.Projects
import dk.sdu.cloud.project.api.v2.ProjectsRetrieveRequest
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.provider.api.Resource
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.SimpleCache
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

// NOTE(Dan): The IpcToUCloudProxyServer allows plugins and user-instances semi-direct access to RPC with UCloud/Core.
// User-instances are even given a "normal" RPC client which will have an identical API to the normal RPC client.
// However, this proxy server _must_ verify that the end-user making the IPC call is actually authorized to do such an
// action in UCloud. This proxy server will use the _PROVIDER CREDENTIALS_ for all its requests. Which grants
// _VERY BROAD POWERS_. Whenever you want to add a call here, please make sure that the end-user making the IPC is
// actually allowed to do such an action. See below for some examples of how to do it before you try to add your own.
class IpcToUCloudProxyServer(private val client: AuthenticatedClient) {
    fun init(server: IpcServer, client: AuthenticatedClient) {
        server.addHandler(IpcHandler(IpcProxyRequestInterceptor.IPC_PROXY_METHOD) { user, req ->
            val proxyRequest = defaultMapper.decodeFromJsonElement(IpcProxyRequest.serializer(), req.params)
            val ucloudId = UserMapping.localIdToUCloudId(user.uid)
            var requireFurtherVerification = false

            val call: CallDescription<*, *, *> = when (proxyRequest.call) {
                // NOTE(DAN): PLEASE MAKE SURE YOU UNDERSTAND WHAT YOU ARE DOING BEFORE ADDING A NEW ENTRY
                JobsControl.update.fullName -> {
                    val request = defaultMapper.decodeFromJsonElement(
                        JobsControl.update.requestType,
                        proxyRequest.request
                    )

                    for (reqItem in request.items) {
                        verifyAccessToResource(
                            ucloudId,
                            jobCache.get(reqItem.id) ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                        )
                    }

                    JobsControl.update
                }

                FileCollectionsControl.register.fullName -> {
                    val request = defaultMapper.decodeFromJsonElement(
                        FileCollectionsControl.register.requestType,
                        proxyRequest.request
                    )

                    for (reqItem in request.items) {
                        if (reqItem.createdBy == null && reqItem.project == null) {
                            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                        }

                        verifyAccessToResource(
                            ucloudId,
                            reqItem.createdBy ?: "_ucloud",
                            reqItem.project,
                            emptyList()
                        )
                    }
                    FileCollectionsControl.register
                }

                FileCollectionsControl.retrieve.fullName -> {
                    requireFurtherVerification = true
                    FileCollectionsControl.retrieve
                }

                FileCollectionsControl.browse.fullName -> {
                    requireFurtherVerification = true
                    FileCollectionsControl.browse
                }

                Products.retrieve.fullName -> {
                    // NOTE(Dan): This is public
                    Products.retrieve
                }

                // NOTE(DAN): PLEASE MAKE SURE YOU UNDERSTAND WHAT YOU ARE DOING BEFORE ADDING A NEW ENTRY

                else -> null
            } ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            runBlocking {
                @Suppress("UNCHECKED_CAST")
                call as CallDescription<Any, Any, Any>

                val modifiedResponse: Any = run {
                    val response = client.client.call(
                        call,
                        defaultMapper.decodeFromJsonElement(call.requestType, proxyRequest.request),
                        client.backend,
                        client.authenticator,
                        afterHook = client.afterHook
                    ).orThrow()

                    if (requireFurtherVerification) {
                        when (call.fullName) {
                            FileCollectionsControl.retrieve.fullName -> {
                                val fc = response as FileCollection
                                verifyAccessToResource(ucloudId, fc)

                                fc
                            }

                            FileCollectionsControl.browse.fullName -> {
                                @Suppress("UNCHECKED_CAST")
                                val page = response as PageV2<FileCollection>

                                val newItems = page.items.filter {
                                    runCatching { verifyAccessToResource(ucloudId, it) }.isSuccess
                                }

                                page.copy(items = newItems)
                            }

                            else -> error("Unhandled verification case for ${call.fullName}")
                        }
                    } else {
                        response
                    }
                }

                defaultMapper.encodeToJsonElement(call.successType, modifiedResponse) as JsonObject
            }
        })
    }

    private suspend fun verifyAccessToResource(
        ucloudUsername: String?,
        resource: Resource<*, *>,
        allowRetry: Boolean = true
    ) {
        verifyAccessToResource(
            ucloudUsername,
            resource.owner.createdBy,
            resource.owner.project,
            resource.permissions?.others ?: emptyList(),
            projectMembershipIsSufficient = false,
            allowRetry
        )
    }

    private suspend fun verifyAccessToResource(
        ucloudUsername: String?,
        createdBy: String,
        project: String?,
        acl: List<ResourceAclEntry>,
        projectMembershipIsSufficient: Boolean = true,
        allowRetry: Boolean = true
    ) {
        if (ucloudUsername == null) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        if (project != null) {
            try {
                val resolvedProject = projectCache.get(project)
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                val members = (resolvedProject.status.members ?: emptyList())
                val groups = (resolvedProject.status.groups ?: emptyList())

                val myself = members.find { it.username == ucloudUsername }
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                if (!myself.role.isAdmin() && !projectMembershipIsSufficient) {
                    var foundAclEntry = false
                    for (entry in acl) {
                        if (entry.permissions.isEmpty()) continue
                        val hasPermissions = when (val entity = entry.entity) {
                            is AclEntity.ProjectGroup -> {
                                groups.any { group ->
                                    group.id == entity.group &&
                                            group.specification.project == entity.projectId &&
                                            (group.status.members ?: emptyList()).contains(ucloudUsername)
                                }
                            }

                            is AclEntity.User -> ucloudUsername == entity.username
                        }

                        if (hasPermissions) {
                            foundAclEntry = true
                            break
                        }
                    }

                    if (!foundAclEntry) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                }
            } catch (ex: RPCException) {
                if (allowRetry) {
                    projectCache.remove(project)
                    return verifyAccessToResource(ucloudUsername, createdBy, project, acl, allowRetry = false)
                } else {
                    throw ex
                }
            }
        } else {
            if (createdBy != ucloudUsername) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }
        }
    }

    private val projectCache = SimpleCache<String, Project>(
        lookup = { projectId ->
            Projects.retrieve.call(
                ProjectsRetrieveRequest(
                    projectId,
                    includeGroups = true,
                    includeMembers = true,
                ),
                client
            ).orNull()
        }
    )

    private val jobCache = SimpleCache<String, Job>(
        maxAge = 60_000 * 30,
        lookup = { jobId ->
            JobsControl.retrieve.call(
                ResourceRetrieveRequest(
                    JobIncludeFlags(
                        includeApplication = true,
                        includeOthers = true,
                        includeParameters = true,
                    ),
                    jobId
                ),
                client
            ).orNull()
        }
    )

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
