package dk.sdu.cloud.cli

import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.base64Decode
import dk.sdu.cloud.base64Encode
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withHttpBody
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.ipcServer
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.utils.sendTerminalMessage
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import libc.clib
import java.io.File

fun ApplicationCli(controllerContext: ControllerContext) {
    val pluginCtx = controllerContext.pluginContext
    val config = pluginCtx.config

    pluginCtx.commandLineInterface?.addHandler(CliHandler("application") { args ->
        val ipcClient = pluginCtx.ipcClient

        fun sendHelp() {
            sendTerminalMessage {
                bold { line("Unknown command: ${args.firstOrNull() ?: "-"}") }
                line()
                line("Available commands:")

                inline("- ")
                code { line("ucloud application upload <file.yaml>") }

                inline("- ")
                code { line("ucloud application authorize <appName> <appVersion> <public|\$username>") }

                inline("- ")
                code { line("ucloud application unauthorize <appName> <appVersion> <public|\$username>") }
            }
        }

        try {
            when (args.firstOrNull()) {
                "upload" -> {
                    if (args.size != 2) {
                        sendHelp()
                        return@CliHandler
                    }

                    val yamlFile = File(args[1])
                    if (!yamlFile.exists()) {
                        sendTerminalMessage {
                            red { bold { inline("File not found: ")  } }
                            code { line(yamlFile.absolutePath) }
                        }
                        return@CliHandler
                    }

                    val yamlText = yamlFile.readText()
                    if (yamlText.length >= 500_000) {
                        sendTerminalMessage {
                            red { bold { line("File size is too big. Try a different file.") } }
                        }
                        return@CliHandler
                    }

                    ipcClient.sendRequest(
                        ApplicationIpc.uploadApplication,
                        YamlWrapper(yamlText)
                    )

                    sendTerminalMessage {
                        bold { green { line("Application has been uploaded successfully!") } }
                    }
                }

                "unauthorize", "authorize" -> {
                    val revoke = args[0] == "unauthorize"

                    if (args.size < 4) {
                        sendHelp()
                        return@CliHandler
                    }

                    val appName = args[1]
                    val appVersion = args[2]

                    val entity = if (args[3] == "public") {
                        null
                    } else if (args.size == 4) {
                        AccessEntity(args[3])
                    } else {
                        if (args.size != 5) {
                            sendHelp()
                            return@CliHandler
                        }

                        AccessEntity(args[3], args[4])
                    }

                    ipcClient.sendRequest(
                        ApplicationIpc.authorizeApplication,
                        AuthorizeRequest(appName, appVersion, entity, revoke)
                    )

                    sendTerminalMessage {
                        green { bold { line("Authorization successful") } }
                    }
                }

                else -> {
                    sendHelp()
                }
            }
        } catch (ex: RPCException) {
            if (ex.httpStatusCode == HttpStatusCode.Forbidden && clib.getuid() != 0) {
                sendTerminalMessage {
                    red { bold { line("You must run this script as root!") } }
                }
            } else {
                sendTerminalMessage {
                    red { bold { line("An error has occured. We received the following message:") } }
                    line(ex.why)
                }
            }
        }
    })

    pluginCtx.commandLineInterface?.addHandler(CliHandler("tool") { args ->
        val ipcClient = pluginCtx.ipcClient

        fun sendHelp() {
            sendTerminalMessage {
                bold { line("Unknown command: ${args.firstOrNull() ?: "-"}") }
                line()
                line("Available commands:")

                inline("- ")
                code { line("ucloud tool upload <file.yaml>") }

                inline("- ")
                code { line("ucloud tool logo <tool> <file.png>") }
            }
        }

        try {
            when (args.firstOrNull()) {
                "upload" -> {
                    if (args.size != 2) {
                        sendHelp()
                        return@CliHandler
                    }

                    val yamlFile = File(args[1])
                    if (!yamlFile.exists()) {
                        sendTerminalMessage {
                            red { bold { inline("File not found: ")  } }
                            code { line(yamlFile.absolutePath) }
                        }
                        return@CliHandler
                    }

                    val yamlText = yamlFile.readText()
                    if (yamlText.length >= 500_000) {
                        sendTerminalMessage {
                            red { bold { line("File size is too big. Try a different file.") } }
                        }
                        return@CliHandler
                    }

                    ipcClient.sendRequest(
                        ApplicationIpc.uploadTool,
                        YamlWrapper(yamlText)
                    )

                    sendTerminalMessage {
                        bold { green { line("Application has been uploaded successfully!") } }
                    }
                }

                "logo" -> {
                    if (args.size != 3) {
                        sendHelp()
                        return@CliHandler
                    }

                    val toolName = args[1]
                    val file = File(args[2])
                    if (!file.exists()) {
                        sendTerminalMessage {
                            red { bold { inline("File not found: ")  } }
                            code { line(file.absolutePath) }
                        }
                        return@CliHandler
                    }

                    val logoEncoded = base64Encode(file.readBytes())
                    if (logoEncoded.length >= 500_000) {
                        sendTerminalMessage {
                            red { bold { line("File size is too big. Try a different file.") } }
                        }
                        return@CliHandler
                    }

                    ipcClient.sendRequest(
                        ApplicationIpc.uploadLogo,
                        UploadLogoRequest(toolName, logoEncoded)
                    )

                    sendTerminalMessage {
                        bold { green { line("Logo has been uploaded successfully!") } }
                    }
                }

                else -> {
                    sendHelp()
                }
            }
        } catch (ex: RPCException) {
            if (ex.httpStatusCode == HttpStatusCode.Forbidden && clib.getuid() != 0) {
                sendTerminalMessage {
                    red { bold { line("You must run this script as root!") } }
                }
            } else {
                sendTerminalMessage {
                    red { bold { line("An error has occured. We received the following message:") } }
                    line(ex.why)
                }
            }
        }
    })

    if (config.shouldRunServerCode()) {
        val rpcClient = pluginCtx.rpcClient
        pluginCtx.ipcServer.addHandler(ApplicationIpc.uploadTool.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

            ToolStore.create.call(
                Unit,
                rpcClient.withHttpBody(request.content)
            ).orThrow()
        })

        pluginCtx.ipcServer.addHandler(ApplicationIpc.uploadApplication.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

            AppStore.create.call(
                Unit,
                rpcClient.withHttpBody(request.content)
            ).orThrow()
        })

        pluginCtx.ipcServer.addHandler(ApplicationIpc.authorizeApplication.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

            if (request.entity == null) {
                AppStore.setPublic.call(
                    SetPublicRequest(request.application, request.version, !request.revoke),
                    rpcClient
                ).orThrow()
            } else {
                AppStore.updateAcl.call(
                    UpdateAclRequest(
                        request.application,
                        listOf(
                            ACLEntryRequest(
                                request.entity,
                                ApplicationAccessRight.LAUNCH,
                                request.revoke
                            )
                        )
                    ),
                    rpcClient
                ).orThrow()
            }
        })

        pluginCtx.ipcServer.addHandler(ApplicationIpc.uploadLogo.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

            val logoBytes = base64Decode(request.contentBase64)

            ToolStore.uploadLogo.call(
                UploadApplicationLogoRequest(request.tool),
                rpcClient.withHttpBody(ContentType.Image.Any, logoBytes.size.toLong(), ByteReadChannel(logoBytes))
            ).orThrow()
        })
    }
}

@Serializable
private data class YamlWrapper(val content: String)

@Serializable
private data class UploadLogoRequest(val tool: String, val contentBase64: String)

@Serializable
private data class AuthorizeRequest(
    val application: String,
    val version: String,
    val entity: AccessEntity?,
    val revoke: Boolean,
)

private object ApplicationIpc : IpcContainer("application") {
    val uploadTool = updateHandler("uploadTool", YamlWrapper.serializer(), Unit.serializer())
    val uploadApplication = updateHandler("uploadApplication", YamlWrapper.serializer(), Unit.serializer())
    val authorizeApplication = updateHandler("authorizeApplication", AuthorizeRequest.serializer(), Unit.serializer())
    val uploadLogo = updateHandler("uploadLogo", UploadLogoRequest.serializer(), Unit.serializer())
}
