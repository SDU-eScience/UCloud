package dk.sdu.cloud.cli

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ExtensionLog
import dk.sdu.cloud.plugins.ExtensionLogFlags
import dk.sdu.cloud.plugins.ExtensionLogIpc
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.prettyMapper
import dk.sdu.cloud.sql.TemporaryView
import dk.sdu.cloud.sql.bindBooleanNullable
import dk.sdu.cloud.sql.bindStringNullable
import dk.sdu.cloud.sql.queryJson
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.sendTerminalFrame
import dk.sdu.cloud.utils.sendTerminalMessage
import dk.sdu.cloud.utils.sendTerminalTable
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.random.Random

private fun CommandLineInterface.tryPrettyJson(maybeJson: String): String {
    if (isParsable) return maybeJson

    return try {
        prettyMapper.encodeToString(
            JsonElement.serializer(),
            defaultMapper.parseToJsonElement(maybeJson)
        )
    } catch (ex: Throwable) {
        maybeJson
    }
}

fun ExtensionsCli(controllerContext: ControllerContext) {
    controllerContext.pluginContext.commandLineInterface?.addHandler(CliHandler("extensions") { args ->
        val ipcClient = controllerContext.pluginContext.ipcClient

        fun sendHelp(): Nothing = sendCommandLineUsage("extensions", "Extension utility") {
            subcommand("read", "Retrieves a batch of extension") {
                arg("--failures", optional = true, description = "Only show entries which failed")
                arg(
                    "--extension=<path>",
                    optional = true,
                    description = "Only show entries which uses a specific extension"
                )
                arg(
                    "--before-relative=<interval>",
                    optional = true,
                    description = "Filters out entries which does not occur before the time specified"
                )
                arg(
                    "--after-relative=<interval>",
                    optional = true,
                    description = "Filters out entries which does not occur before the time specified"
                )
            }

            subcommand("delete", "Deletes one or more entries from the log") {
                arg("id(s)", description = "Comma separated IDs")
            }

            subcommand("view", "View details of an event") {
                arg("id", description = "The ID (see read)")
            }

            subcommand("clear", "Deletes all entries")
        }

        when (args.getOrNull(0)) {
            null, "read" -> {
                val events = ipcClient.sendRequest(
                    ExtensionLogIpc.browse,
                    ExtensionLogFlags(
                        if (findBooleanOption("--failures", args)) true else null,
                        findOption("--extension", args),
                        findOption("--before-relative", args),
                        findOption("--after-relative", args),
                    )
                ).items

                sendTerminalTable {
                    header("Timestamp", 30)
                    header("ID", 10)
                    header("UID", 10)
                    header("Success", 10)
                    header("Exit", 10)
                    header("Extension", 50)

                    for (event in events) {
                        cell(Date(event.timestamp))
                        cell(event.id)
                        cell(event.uid)
                        cell(event.success)
                        cell(event.statusCode)
                        cell(event.extensionPath.substringAfterLast('/'))
                        nextRow()
                    }
                }
            }

            "view" -> {
                val id = args.getOrNull(1) ?: sendHelp()

                val logEvent = runCatching {
                    ipcClient.sendRequest(
                        ExtensionLogIpc.retrieve,
                        FindByStringId(id)
                    )
                }.getOrNull()

                if (logEvent == null) {
                    sendTerminalMessage { line("No such entry found") }
                } else {
                    sendTerminalFrame {
                        title("Extension event", wide = true)
                        field("ID", logEvent.id)
                        field("Timestamp", Date(logEvent.timestamp))
                        field("Extension", logEvent.extensionPath)
                        field("UID", logEvent.uid)
                        field("Exit code", logEvent.statusCode)
                        field("Success", logEvent.success)
                        field("Request", tryPrettyJson(logEvent.request))
                        field("stdout", tryPrettyJson(logEvent.stdout))
                        field("stderr", logEvent.stderr)
                    }
                }
            }

            "delete" -> {
                val ids = args.getOrNull(1)?.split(",")?.map { FindByStringId(it.trim()) } ?: sendHelp()

                ipcClient.sendRequest(
                    ExtensionLogIpc.markAsRead,
                    BulkRequest(ids)
                )
            }

            "clear" -> {
                ipcClient.sendRequest(ExtensionLogIpc.clear, Unit)
            }

            else -> sendHelp()
        }
    })

    val logJsonView = TemporaryView.generate(ExtensionLog.serializer(), "extension_log")

    val ipcServer = controllerContext.pluginContext.ipcServerOptional
    ipcServer?.addHandler(ExtensionLogIpc.browse.handler { user, request ->
        if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val rows = dbConnection.withSession { session ->
            session.queryJson(
                logJsonView,
                """
                    select serialized
                    from extension_log_json
                    where
                        (:filter_failure::bool is null or :filter_failure::bool = not success) and
                        (:filter_extension::text is null or extension_path ilike '%' || :filter_extension::text || '%') and
                        (:filter_before_relative::text is null or timestamp < now() - :filter_before_relative::interval) and
                        (:filter_after_relative::text is null or timestamp > now() - :filter_after_relative::interval)
                    order by timestamp desc
                    limit 100
                """,
                prepare = {
                    bindBooleanNullable("filter_failure", request.filterFailure)
                    bindStringNullable("filter_extension", request.filterExtension)
                    bindStringNullable("filter_before_relative", request.filterBeforeRelative)
                    bindStringNullable("filter_after_relative", request.filterAfterRelative)
                }
            )
        }

        PageV2(rows.size, rows, null)
    })

    ipcServer?.addHandler(ExtensionLogIpc.create.handler { user, request ->
        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    insert into extension_log(timestamp, request, extension_path, stdout, stderr, status_code, uid, success)
                    values (now(), :request, :extension_path, :stdout, :stderr, :status_code, :uid, :success)
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("request", request.request)
                    bindString("extension_path", request.extensionPath)
                    bindString("stdout", request.stdout)
                    bindString("stderr", request.stderr)
                    bindInt("status_code", request.statusCode)
                    bindInt("uid", user.uid)
                    bindBoolean("success", request.success)
                }
            )

            if (Random.nextInt(0, 100) == 1) {
                session.prepareStatement(
                    """
                        delete from extension_log
                        where timestamp timestamp < now() - '7 days'::interval
                    """
                ).useAndInvokeAndDiscard()
            }
        }
    })

    ipcServer?.addHandler(ExtensionLogIpc.clear.handler { user, request ->
        if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    delete from extension_log where true
                """
            ).useAndInvokeAndDiscard()
        }
    })

    ipcServer?.addHandler(ExtensionLogIpc.markAsRead.handler { user, request ->
        if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    delete from extension_log
                    where id = some(:ids::bigint[])
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindList("ids", request.items.mapNotNull { it.id.toLongOrNull() })
                }
            )
        }
    })

    ipcServer?.addHandler(ExtensionLogIpc.retrieve.handler { user, request ->
        if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        dbConnection.withSession { session ->
            session.queryJson(
                logJsonView,
                "select serialized from extension_log_json where id = :id",
                prepare = { bindLong("id", request.id.toLongOrNull() ?: -1) }
            ).singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    })
}
