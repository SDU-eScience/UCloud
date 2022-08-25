package dk.sdu.cloud.cli

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.plugins.ipcServer
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.project.api.v2.ProjectRole
import dk.sdu.cloud.project.api.v2.Projects
import dk.sdu.cloud.project.api.v2.ProjectsBrowseRequest
import dk.sdu.cloud.project.api.v2.ProjectsRetrieveRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

fun UCloudProjectCli(controllerContext: ControllerContext) {
    val pluginContext = controllerContext.pluginContext
    val config = pluginContext.config
    pluginContext.commandLineInterface?.addHandler(CliHandler("projects") { args ->
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
    }
}

@Serializable
data class UCloudProjectUser(
    val username: String,
    val email: String?,
    val role: ProjectRole
)

@Serializable
data class UCloudProject(
    val id: String,
    val path: String,
    val title: String,
    val pi: UCloudProjectUser? = null,
)

object UCloudProjectIpc : IpcContainer("projects") {
    val browse = browseHandler(Unit.serializer(), PageV2.serializer(UCloudProject.serializer()))
    val retrieve = retrieveHandler(FindByStringId.serializer(), UCloudProject.serializer())
    val retrieveMembers = updateHandler("retrieveMembers", FindByStringId.serializer(), PageV2.serializer(UCloudProjectUser.serializer()))
}
