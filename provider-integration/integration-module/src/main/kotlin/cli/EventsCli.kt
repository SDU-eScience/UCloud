package dk.sdu.cloud.cli

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.controllers.EventIpc
import dk.sdu.cloud.controllers.EventPauseState
import dk.sdu.cloud.controllers.UCloudCoreEvent
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.utils.sendTerminalMessage
import dk.sdu.cloud.utils.sendTerminalTable

fun EventsCli(controllerContext: ControllerContext) {
    controllerContext.pluginContext.commandLineInterface?.addHandler(CliHandler("events") { args ->
        fun sendHelp(): Nothing = sendCommandLineUsage("events", "Manage events from UCloud/Core") {
            subcommand("ls", "Reads a list of unhandled events from UCloud/Core")

            subcommand(
                "rm",
                "Manually remove an unhandled event from the queue. This means that no plugin will handle the event."
            ) {
                arg("id", description = "The event ID, see the read command for more details")
            }

            subcommand("replay", "Trigger a replay of relevant events. This will usually cause extension to run again.")

            subcommand("pause", "Stops the automatic processing of UCloud/Core events")
            subcommand("unpause", "Resumes the automatic processing of UCloud/Core events")
            subcommand(
                "is-paused",
                "Queries the system if automatic processing of UCloud/Core events is currently paused"
            )
        }

        val ipcClient = controllerContext.pluginContext.ipcClient

        when (args.getOrNull(0)) {
            "ls" -> {
                val notifications = ipcClient.sendRequest(EventIpc.browse, Unit).items

                sendTerminalTable {
                    header("ID", 40)
                    header("Type", 20)
                    header("Description", 60)

                    for (notification in notifications) {
                        cell(notification.id)
                        cell(
                            when (notification) {
                                is UCloudCoreEvent.Allocation -> "Allocation"
                                is UCloudCoreEvent.Project -> "Project update"
                            }
                        )

                        cell(
                            when (notification) {
                                is UCloudCoreEvent.Allocation -> {
                                    buildString {
                                        append(
                                            when (val o = notification.event.owner) {
                                                is WalletOwner.Project -> o.projectId
                                                is WalletOwner.User -> o.username
                                            }
                                        )
                                        append(" ")
                                        append(notification.event.category)
                                    }
                                }

                                is UCloudCoreEvent.Project -> {
                                    val project = notification.event.project
                                    "${project.specification.title} (${project.id})"
                                }
                            }
                        )
                        nextRow()
                    }
                }
            }

            "rm" -> {
                val id = args.getOrNull(1) ?: sendHelp()
                ipcClient.sendRequest(EventIpc.delete, FindByStringId(id))
                sendTerminalMessage { green { line("OK") } }
            }

            "replay" -> {
                ipcClient.sendRequest(EventIpc.replay, Unit)
                sendTerminalMessage { green { line("OK") } }
            }

            "pause" -> {
                ipcClient.sendRequest(EventIpc.updatePauseState, EventPauseState(true))
                sendTerminalMessage { green { line("OK") } }
            }

            "unpause" -> {
                ipcClient.sendRequest(EventIpc.updatePauseState, EventPauseState(false))
                sendTerminalMessage { green { line("OK") } }
            }

            "is-paused" -> {
                val isPaused = ipcClient.sendRequest(EventIpc.retrievePauseState, Unit).isPaused
                sendTerminalMessage {
                    if (isPaused) line("yes")
                    else line("no")
                }
            }

            else -> sendHelp()
        }
    })
}
