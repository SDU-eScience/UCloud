package dk.sdu.cloud.cli

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.ipcServer
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.project.api.v2.Project
import dk.sdu.cloud.project.api.v2.ProjectRole
import dk.sdu.cloud.project.api.v2.Projects
import dk.sdu.cloud.project.api.v2.ProjectsBrowseRequest
import dk.sdu.cloud.project.api.v2.ProjectsChangeRoleRequestItem
import dk.sdu.cloud.project.api.v2.ProjectsRetrieveRequest
import dk.sdu.cloud.utils.sendTerminalFrame
import dk.sdu.cloud.utils.sendTerminalTable
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

fun UCloudProjectCli(controllerContext: ControllerContext) {
    val pluginContext = controllerContext.pluginContext
    val config = pluginContext.config
    pluginContext.commandLineInterface?.addHandler(CliHandler("projects") { args ->
        fun sendHelp(): Nothing = sendCommandLineUsage("projects", "View information about relevant UCloud projects") {
            subcommand("list", "View a list of relevant projects")
            subcommand("retrieve", "Retrieve project information and members") {
                arg("projectId", description = "Project ID retrieved from UCloud (see projects list)")
            }

            subcommand("create", "Creates a UCloud project which will be a child of this provider's project") {
                arg("title", optional = false) {
                    description = "The title of the project, must be unique"
                }
            }
        }

        val ipcClient = pluginContext.ipcClient

        genericCommandLineHandler {
            when (args.getOrNull(0)) {
                "list" -> {
                    val projects = ipcClient.sendRequest(UCloudProjectIpc.browse, Unit)

                    sendTerminalTable {
                        header("ID", 40)
                        header("Path", 30)
                        header("Title", 30)
                        header("PI", 20)

                        for (row in projects.items) {
                            nextRow()
                            cell(row.id)
                            cell(row.path)
                            cell(row.title)
                            cell(row.pi?.email)
                        }
                    }
                }

                "retrieve" -> {
                    val id = args.getOrNull(1) ?: sendHelp()
                    val projectInfo = ipcClient.sendRequest(UCloudProjectIpc.retrieve, FindByStringId(id))
                    val members = ipcClient.sendRequest(UCloudProjectIpc.retrieveMembers, FindByStringId(id))

                    sendTerminalFrame("Project Information") {
                        field("ID", projectInfo.id)
                        field("Path", projectInfo.path)
                        field("Title", projectInfo.title)
                        field("PI", projectInfo.pi?.email)
                    }

                    sendTerminalTable {
                        header("Role", 20)
                        header("Member", 100)

                        for (member in members.items) {
                            nextRow()
                            cell(member.role)
                            cell(member.email ?: member.username)
                        }
                    }
                }

                "create" -> {
                    val title = args.getOrNull(1) ?: sendHelp()

                    ipcClient.sendRequest(
                        UCloudProjectIpc.create,
                        ProviderProject(title)
                    )
                }

                else -> sendHelp()
            }
        }
    })

    if (config.shouldRunServerCode()) {
        val rpcClient = pluginContext.rpcClient
        val ipcServer = pluginContext.ipcServer

        ipcServer.addHandler(UCloudProjectIpc.browse.handler { user, _ ->
            if (user.uid != 0) throw RPCException("Root is required for this script", HttpStatusCode.Forbidden)

            val projects = ArrayList<UCloudProject>()
            var next: String? = null
            while (true) {
                val response = Projects.browse.call(
                    ProjectsBrowseRequest(
                        itemsPerPage = 250,
                        next = next,

                        includeMembers = false,
                        includePath = true,
                        includeArchived = false,
                        includeGroups = false,
                        includeSettings = false,
                        includeFavorite = false,
                    ),
                    rpcClient
                ).orThrow()

                for (respItem in response.items) {
                    projects.add(UCloudProject(respItem.id, respItem.status.path!!, respItem.specification.title))
                }

                next = response.next ?: break
                if (response.items.size >= 10_000) break
            }

            PageV2(projects.size, projects, null)
        })

        ipcServer.addHandler(UCloudProjectIpc.retrieve.handler { user, request ->
            if (user.uid != 0) throw RPCException("Root is required for this script", HttpStatusCode.Forbidden)

            val retrievedProject = Projects.retrieve.call(
                ProjectsRetrieveRequest(
                    id = request.id,

                    includeMembers = true,
                    includePath = true,
                    includeArchived = false,
                    includeGroups = false,
                    includeSettings = false,
                    includeFavorite = false,
                ),
                rpcClient
            ).orThrow()

            val pi = retrievedProject.status.members!!.find { it.role == ProjectRole.PI }

            UCloudProject(
                retrievedProject.id,
                retrievedProject.status.path!!,
                retrievedProject.specification.title,
                pi?.let {
                    UCloudProjectUser(
                        it.username,
                        null,
                        it.role
                    )
                }
            )
        })

        ipcServer.addHandler(UCloudProjectIpc.retrieveMembers.handler { user, request ->
            if (user.uid != 0) throw RPCException("Root is required for this script", HttpStatusCode.Forbidden)

            val retrievedProject = Projects.retrieve.call(
                ProjectsRetrieveRequest(
                    id = request.id,

                    includeMembers = true,
                    includePath = true,
                    includeArchived = false,
                    includeGroups = false,
                    includeSettings = false,
                    includeFavorite = false,
                ),
                rpcClient
            ).orThrow()

            val members = retrievedProject.status.members!!.map { UCloudProjectUser(it.username, null, it.role) }
            PageV2(members.size, members, null)
        })

        ipcServer.addHandler(UCloudProjectIpc.create.handler { user, request ->
            if (user.uid != 0) throw RPCException("Root is required for this script", HttpStatusCode.Forbidden)

            val parentProject = Projects.retrieveProviderProject.call(Unit, rpcClient).orThrow().id

            val projectId = Projects.create.call(
                bulkRequestOf(
                    Project.Specification(parentProject, request.title)
                ),
                rpcClient
            ).orThrow().responses.singleOrNull()?.id ?: error("Unexpected response from UCloud. Project has no ID.")

            FindByStringId(projectId)
        })
    }
}

@Serializable
private data class UCloudProjectUser(
    val username: String,
    val email: String?,
    val role: ProjectRole
)

@Serializable
private data class UCloudProject(
    val id: String,
    val path: String,
    val title: String,
    val pi: UCloudProjectUser? = null,
)

@Serializable
private data class ProviderProject(
    val title: String,
)

private object UCloudProjectIpc : IpcContainer("projects") {
    val browse = browseHandler(Unit.serializer(), PageV2.serializer(UCloudProject.serializer()))
    val retrieve = retrieveHandler(FindByStringId.serializer(), UCloudProject.serializer())
    val retrieveMembers = updateHandler("retrieveMembers", FindByStringId.serializer(), PageV2.serializer(UCloudProjectUser.serializer()))
    val create = createHandler(ProviderProject.serializer(), FindByStringId.serializer())
}
