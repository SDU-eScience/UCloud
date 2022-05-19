package dk.sdu.cloud.cli

import dk.sdu.cloud.PaginationRequestV2
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.controllers.ConnectionEntry
import dk.sdu.cloud.controllers.ConnectionIpc
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.controllers.FindByUsername
import dk.sdu.cloud.ipc.sendRequestBlocking
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.utils.sendTerminalMessage

fun ConnectionCommandLine(controllerContext: ControllerContext) {
    controllerContext.pluginContext.commandLineInterface?.addHandler(CliHandler("connection") { args ->
        val ipcClient = controllerContext.pluginContext.ipcClient

        try {
            when (args.firstOrNull()) {
                "list", "ls" -> {
                    val items = ipcClient.sendRequestBlocking(
                        ConnectionIpc.browse,
                        PaginationRequestV2(250)
                    ).items

                    if (items.isEmpty()) {
                        sendTerminalMessage {
                            bold { line("No connections found.") }
                            inline("You can create a new connection with ")
                            code { inline("ucloud connection add <username> <uid>") }
                        }
                    } else {
                        sendTerminalMessage {
                            bold {
                                inline("Header".padEnd(60, ' '))
                                inline("UID".padEnd(10, ' '))
                                line()
                                line("".padEnd(70, '-'))
                            }

                            for (item in items) {
                                inline(item.username.padEnd(60, ' '))
                                inline(item.uid.toString().padEnd(10, ' '))
                                line()
                            }
                        }
                    }

                }

                "view" -> {
                    val username = args.getOrNull(1)
                    if (username == null) {
                        sendTerminalMessage {
                            red { bold { line("Incorrect usage!") } }

                            inline("Usage: ")
                            code { inline("ucloud connection view <username/uid>") }
                        }
                        return@CliHandler
                    }
                    val connection = ipcClient.sendRequestBlocking(
                        ConnectionIpc.browse,
                        PaginationRequestV2(250)
                    ).items.find { it.username == username || it.uid == username.toIntOrNull() }

                    if (connection == null) {
                        sendTerminalMessage {
                            bold { line("No connections found.") }
                        }
                    } else {
                        sendTerminalMessage {
                            bold {
                                inline("Header".padEnd(60, ' '))
                                inline("UID".padEnd(10, ' '))
                                line()
                                line("".padEnd(70, '-'))
                            }

                            inline(connection.username.padEnd(60, ' '))
                            inline(connection.uid.toString().padEnd(10, ' '))
                            line()
                        }
                    }
                }

                "add", "update" -> {
                    val username = args.getOrNull(1)
                    val uid = args.getOrNull(2)?.toIntOrNull()

                    if (username == null || uid == null) {
                        sendTerminalMessage {
                            red { bold { line("Incorrect usage!") } }

                            inline("Usage: ")
                            code { inline("ucloud connection add <username> <uid>") }
                        }
                        return@CliHandler
                    }

                    ipcClient.sendRequestBlocking(
                        ConnectionIpc.registerConnection,
                        ConnectionEntry(username, uid)
                    )
                }

                "delete", "del", "remove", "rm" -> {
                    val username = args.getOrNull(1)

                    if (username == null) {
                        sendTerminalMessage {
                            red { bold { line("Incorrect usage!") } }

                            inline("Usage: ")
                            code { inline("ucloud connection rm <username>") }
                        }
                        return@CliHandler
                    }

                    ipcClient.sendRequestBlocking(
                        ConnectionIpc.removeConnection,
                        FindByUsername(username)
                    )
                }

                else -> {
                    sendTerminalMessage {
                        bold { code { line("ucloud connection <command> <args...>") } }
                        line()

                        bold { code { inline("list") } }
                        line(" - Lists the connections registered with the UCloud/IM")

                        code {
                            bold { inline("view") }
                            inline(" <username>")
                        }
                        line(" - Views details about a specific connection")

                        code {
                            bold { inline("add") }
                            inline(" <username> <uid>")
                        }
                        line(" - Establishes a new connection between a UCloud user and a local identity")

                        code {
                            bold { inline("delete") }
                            inline(" <username>")
                        }
                        line(" - Removes a connection by UCloud username")
                    }
                }
            }
        } catch (ex: RPCException) {
            if (ex.httpStatusCode == HttpStatusCode.Forbidden) {
                sendTerminalMessage {
                    red { bold { line("You must run this script as root!") } }
                }
            } else {
                ex.printStackTrace()
            }
        }
    })
}