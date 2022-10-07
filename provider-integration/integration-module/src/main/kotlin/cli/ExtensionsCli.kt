package dk.sdu.cloud.cli

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ExtensionFailure
import dk.sdu.cloud.plugins.ExtensionFailureIpc
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.sendTerminalFrame
import dk.sdu.cloud.utils.sendTerminalMessage
import dk.sdu.cloud.utils.sendTerminalTable
import java.util.*

fun ExtensionsCli(controllerContext: ControllerContext) {
    controllerContext.pluginContext.commandLineInterface?.addHandler(CliHandler("extensions") { args ->
        val ipcClient = controllerContext.pluginContext.ipcClient

        fun sendHelp(): Nothing = sendCommandLineUsage("extension-failures", "Extension utility") {
            subcommand("read", "Retrieves a batch of extension failures")

            subcommand("delete", "Deletes one or more failures from the log") {
                arg("failureId(s)", description = "Comma separated failure IDs")
            }

            subcommand("view", "View details of a failure") {
                arg("failureId", description = "The ID of the failure (see read-failures)")
            }

            subcommand("clear", "Mark all extension failures as read")
        }

        when (args.getOrNull(0)) {
            null, "read" -> {
                val failures = ipcClient.sendRequest(
                    ExtensionFailureIpc.browse,
                    Unit
                ).items

                sendTerminalTable {
                    header("Timestamp", 30)
                    header("ID", 10)
                    header("UID", 10)
                    header("Extension", 70)

                    for (failure in failures) {
                        cell(Date(failure.timestamp))
                        cell(failure.id)
                        cell(failure.uid)
                        cell(failure.extensionPath.substringAfterLast('/'))
                        nextRow()
                    }
                }
            }

            "view" -> {
                val failureId = args.getOrNull(1) ?: sendHelp()

                val failure = ipcClient.sendRequest(
                    ExtensionFailureIpc.browse,
                    Unit
                ).items.find { it.id == failureId }

                if (failure == null) {
                    sendTerminalMessage { line("No such failure found") }
                } else {
                    sendTerminalFrame {
                        title("Extension failure", wide = true)
                        field("ID", failure.id)
                        field("Timestamp", Date(failure.timestamp))
                        field("Exit code", failure.statusCode)
                        field("stdout", failure.stdout)
                        field("stderr", failure.stderr)
                    }
                }
            }

            "delete" -> {
                val failureIds = args.getOrNull(1)?.split(",")?.map { FindByStringId(it.trim()) } ?: sendHelp()

                ipcClient.sendRequest(
                    ExtensionFailureIpc.markAsRead,
                    BulkRequest(failureIds)
                )
            }

            "clear" -> {
                ipcClient.sendRequest(ExtensionFailureIpc.clear, Unit)
            }

            else -> sendHelp()
        }
    })

    val ipcServer = controllerContext.pluginContext.ipcServerOptional
    ipcServer?.addHandler(ExtensionFailureIpc.browse.handler { user, request ->
        if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val rows = ArrayList<ExtensionFailure>()
        dbConnection.withSession { session ->
            session.prepareStatement(
                """
                    select ts, request, extension_path, stdout, stderr, status_code, uid, id
                    from extension_failure
                    order by ts desc
                    limit 100
                """
            ).useAndInvoke(
                readRow = { row ->
                    rows.add(
                        ExtensionFailure(
                            row.getLong(0)!!,
                            row.getString(1)!!,
                            row.getString(2)!!,
                            row.getString(3)!!,
                            row.getString(4)!!,
                            row.getInt(5)!!,
                            row.getInt(6)!!,
                            row.getLong(7)!!.toString(),
                        )
                    )
                }
            )
        }

        PageV2(rows.size, rows, null)
    })

    ipcServer?.addHandler(ExtensionFailureIpc.create.handler { user, request ->
        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    insert into extension_failure(ts, request, extension_path, stdout, stderr, status_code, uid)
                    values (:ts, :request, :extension_path, :stdout, :stderr, :status_code, :uid)
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindLong("ts", Time.now())
                    bindString("request", request.request)
                    bindString("extension_path", request.extensionPath)
                    bindString("stdout", request.stdout)
                    bindString("stderr", request.stderr)
                    bindInt("status_code", request.statusCode)
                    bindInt("uid", user.uid)
                }
            )
        }
    })

    ipcServer?.addHandler(ExtensionFailureIpc.clear.handler { user, request ->
        if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    delete from extension_failure where true
                """
            ).useAndInvokeAndDiscard()
        }
    })

    ipcServer?.addHandler(ExtensionFailureIpc.markAsRead.handler { user, request ->
        if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    delete from extension_failure
                    where id = some(:ids::bigint[])
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindList("ids", request.items.mapNotNull { it.id.toLongOrNull() })
                }
            )
        }
    })
}
