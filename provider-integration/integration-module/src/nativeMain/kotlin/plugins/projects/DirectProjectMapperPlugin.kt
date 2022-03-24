/*
package dk.sdu.cloud.plugins.projects

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.cli.CliHandler
import dk.sdu.cloud.ipc.*
import dk.sdu.cloud.plugins.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import platform.posix.getuid
import kotlin.system.exitProcess

@Serializable
private data class RegisterProject(val ucloud: String, val local: String)
private object DirectProjectMapperIpc : IpcContainer("directprojects") {
    val create = createHandler<RegisterProject, Unit>()
    val deleteByUCloud = updateHandler<FindByStringId, Unit>("deleteByUCloud")
    val deleteByLocal = updateHandler<FindByStringId, Unit>("deleteByLocal")
    val retrieveByUCloud = retrieveHandler<FindByStringId, FindByStringId>()
    val retrieveByLocal = updateHandler<FindByStringId, BulkResponse<FindByStringId>>("retrieveByLocal")
}

class DirectProjectMapperPlugin : ProjectMapperPlugin {
    override suspend fun PluginContext.initialize(pluginConfig: JsonObject) {
        val ipc = ipcServerOptional
        if (ipc != null) {
            ipc.addHandler(DirectProjectMapperIpc.create.handler { user, request ->
                if (user.uid != 0U) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                ProjectMapper.registerProjectMapping(request.ucloud, request.local)
            })

            ipc.addHandler(DirectProjectMapperIpc.deleteByUCloud.handler { user, request ->
                if (user.uid != 0U) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                ProjectMapper.clearMappingByUCloudProject(request.id)
            })

            ipc.addHandler(DirectProjectMapperIpc.deleteByLocal.handler { user, request ->
                if (user.uid != 0U) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                ProjectMapper.clearMappingByLocalProject(request.id)
            })

            ipc.addHandler(DirectProjectMapperIpc.retrieveByLocal.handler { user, request ->
                if (user.uid != 0U) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                BulkResponse(ProjectMapper.mapLocalToUCloud(request.id).map { FindByStringId(it) })
            })

            ipc.addHandler(DirectProjectMapperIpc.retrieveByUCloud.handler { user, request ->
                if (user.uid != 0U) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                FindByStringId(ProjectMapper.mapUCloudToLocal(request.id))
            })
        }

        commandLineInterface?.addHandler(CliHandler(PLUGIN_NAME) { args ->
            if (getuid() != 0U) {
                println("Error: \"ucloud $PLUGIN_NAME\" must be invoked as root!")
                println()
                printUsage()
            }

            if (args.isEmpty()) printUsage()

            when (args[0]) {
                ADD_COMMAND -> {
                    if (args.size != 3) printUsage()
                    ipcClient.sendRequestBlocking(DirectProjectMapperIpc.create, RegisterProject(args[1], args[2]))
                    println("OK")
                }

                RM_UCLOUD_COMMAND -> {
                    if (args.size != 2) printUsage()
                    ipcClient.sendRequestBlocking(DirectProjectMapperIpc.deleteByUCloud, FindByStringId(args[1]))
                    println("OK")
                }

                RM_LOCAL_COMMAND -> {
                    if (args.size != 2) printUsage()
                    ipcClient.sendRequestBlocking(DirectProjectMapperIpc.deleteByLocal, FindByStringId(args[1]))
                    println("OK")
                }

                GET_UCLOUD_COMMAND -> {
                    if (args.size != 2) printUsage()
                    val local = ipcClient.sendRequestBlocking(
                        DirectProjectMapperIpc.retrieveByUCloud,
                        FindByStringId(args[1])
                    )
                    println("Local project: ${local.id}")
                }

                GET_LOCAL_COMMAND -> {
                    if (args.size != 2) printUsage()
                    val ucloudProjects = ipcClient.sendRequestBlocking(
                        DirectProjectMapperIpc.retrieveByLocal,
                        FindByStringId(args[1])
                    )

                    if (ucloudProjects.responses.isEmpty()) {
                        println("No such project")
                    } else {
                        for (project in ucloudProjects.responses) {
                            println("UCloud project: ${project.id}")
                        }
                    }
                }
                else -> printUsage()
            }
        })
    }

    private fun printUsage(): Nothing {
        println("UCloud Integration Module Project Management")
        println("Usage:")
        println("  ucloud $PLUGIN_NAME $ADD_COMMAND <ucloud-id> <local-id>")
        println("  ucloud $PLUGIN_NAME $RM_UCLOUD_COMMAND <ucloud-id>")
        println("  ucloud $PLUGIN_NAME $RM_LOCAL_COMMAND <local-id>")
        println("  ucloud $PLUGIN_NAME $GET_UCLOUD_COMMAND <ucloud-id>")
        println("  ucloud $PLUGIN_NAME $GET_LOCAL_COMMAND <local-id>")
        exitProcess(1)
    }

    companion object {
        private const val PLUGIN_NAME = "projects"
        private const val ADD_COMMAND = "add"
        private const val RM_UCLOUD_COMMAND = "rm-ucloud"
        private const val RM_LOCAL_COMMAND = "rm-local"
        private const val GET_UCLOUD_COMMAND = "get-ucloud"
        private const val GET_LOCAL_COMMAND = "get-local"
    }
}
 */