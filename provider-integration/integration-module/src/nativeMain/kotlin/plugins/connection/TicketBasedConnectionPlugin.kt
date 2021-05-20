package dk.sdu.cloud.plugins.connection

import dk.sdu.cloud.callBlocking
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.cli.CliHandler
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.ipc.*
import dk.sdu.cloud.plugins.ConnectionPlugin
import dk.sdu.cloud.plugins.ConnectionResponse
import dk.sdu.cloud.plugins.HTML
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.provider.api.IntegrationControl
import dk.sdu.cloud.provider.api.IntegrationControlApproveConnectionRequest
import dk.sdu.cloud.service.Log
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.utils.secureToken
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class TicketApprovalRequest(
    val ticket: String,
    val localIdentity: String,
)

class TicketBasedConnectionPlugin : ConnectionPlugin {
    override fun PluginContext.initialize() {
        val log = Log("TicketBasedConnectionPlugin")
        ipcServer?.addHandler(
            IpcHandler("connect.approve") { user, jsonRequest ->
                log.debug("Asked to approve connection!")
                val rpcClient = rpcClient ?: error("No RPC client")
                if (user.uid != 0u || user.gid != 0u) {
                    throw RPCException("Only root can call these endpoints", HttpStatusCode.Unauthorized)
                }

                val req = runCatching {
                    defaultMapper.decodeFromJsonElement<TicketApprovalRequest>(jsonRequest.params)
                }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

                var ucloudId: String? = null
                (dbConnection ?: error("No DB connection available")).withTransaction { connection ->
                    connection.prepareStatement(
                        //language=SQLite
                        """
                            update ticket_connections
                            set completed_at = datetime()
                            where ticket = :ticket and completed_at is null
                            returning ucloud_id
                        """
                    ).useAndInvoke({ bindString("ticket", req.ticket) }) {
                        ucloudId = it.getString(0)
                    }

                    val capturedId =
                        ucloudId ?: throw RPCException("Invalid ticket supplied", HttpStatusCode.BadRequest)

                    connection.prepareStatement(
                        //language=SQLite
                        """
                            insert or replace into user_mapping (ucloud_id, local_identity)
                            values (:ucloud_id, :local_identity)
                        """
                    ).useAndInvokeAndDiscard {
                        bindString("ucloud_id", capturedId)
                        bindString("local_identity", req.localIdentity)
                    }

                    IntegrationControl.approveConnection.callBlocking(
                        IntegrationControlApproveConnectionRequest(capturedId),
                        rpcClient
                    ).orThrow()
                }

                JsonObject(emptyMap())
            }
        )

        commandLineInterface?.addHandler(
            CliHandler("connect") { args ->
                val usageMessage = "Usage: ucloud connect approve <ticket> <localIdentity>"
                val ipcClient = ipcClient ?: error("No ipc client")

                when (args.getOrNull(0)) {
                    "approve" -> {
                        val ticket = args.getOrNull(1)
                            ?: throw RPCException(usageMessage, HttpStatusCode.BadRequest)
                        val localId = args.getOrNull(2)
                            ?: throw RPCException(usageMessage, HttpStatusCode.BadRequest)

                        ipcClient.sendRequestBlocking(
                            JsonRpcRequest(
                                "connect.approve",
                                defaultMapper.encodeToJsonElement(TicketApprovalRequest(ticket, localId)) as JsonObject
                            )
                        ).orThrow<Unit>()

                        println("Success!")
                    }

                    else -> {
                        throw RPCException("Unknown command!\n$usageMessage", HttpStatusCode.BadRequest)
                    }
                }
            }
        )
    }

    override fun PluginContext.initiateConnection(username: String): ConnectionResponse {
        val connection = dbConnection ?: error("Server mode required for TicketBasedConnectionPlugin")
        val ticket = secureToken(64)
        connection.withTransaction {
            connection.prepareStatement(
                //language=SQLite
                """
                    insert into ticket_connections (ticket, ucloud_id, created_at, completed_at)
                    values (:ticket, :ucloud_id, datetime(), null)
                """
            ).useAndInvokeAndDiscard {
                bindString("ticket", ticket)
                bindString("ucloud_id", username)
            }
        }
        return ConnectionResponse.ShowInstructions(mapOf("ticket" to listOf(ticket)))
    }

    override fun PluginContext.showInstructions(query: Map<String, List<String>>): HTML {
        val connection = dbConnection ?: error("Server mode required for TicketBasedConnectionPlugin")
        val ticket = query["ticket"]?.firstOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        var ucloudUsername: String? = null
        connection.withTransaction {
            connection.prepareStatement(
                //language=SQLite
                """
                    select ucloud_id
                    from ticket_connections
                    where ticket = :ticket and completed_at is null
                """
            ).useAndInvoke(
                prepare = { bindString("ticket", ticket) },
                readRow = { ucloudUsername = it.getString(0)!! }
            )
        }

        val username = ucloudUsername ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        return HTML(
            //language=HTML
            """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                     <meta charset="UTF-8">
                     <meta name="viewport" content="width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0">
                     <meta http-equiv="X-UA-Compatible" content="ie=edge">
                     <title>Connect ${HTML.escape(config.core.providerId)} to UCloud</title>
                     <link rel="preconnect" href="https://fonts.gstatic.com">
                     <link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;700&family=JetBrains+Mono&display=swap" rel="stylesheet"> 
                     <style>
                        body {
                            font-family: 'IBM Plex Sans', sans-serif;
                            font-size: 16px;
                            margin: 2em auto;
                            padding: 1em;
                            max-width: 1000px;
                            line-height: 1.5;
                            color: #454545;
                        }
                        
                        a, a:visited {
                            color: #0077aa;
                        }
                        
                        .box {
                            border: 2px rgb(102, 102, 102) dashed;
                            padding: 16px;
                            border-radius: 5px;
                            font-family: 'JetBrains Mono', monospace;
                        }
                     </style>
                </head>
                <body>
                    <h1>Connect your UCloud user to ${HTML.escape(config.core.providerId)}</h1>
                    
                    <p>
                        Hi <b>${HTML.escape(username)}</b>!
                        
                        You are currently connecting or re-authenticating your UCloud user with
                        <b>${HTML.escape(config.core.providerId)}</b>. Please follow the instructions below to continue.
                    </p>
                    
                    <h2>1. New User</h2>
                    <p>
                        If you have never used <b>${HTML.escape(config.core.providerId)}</b> before then we kindly ask
                        you to send an email to <b>foo@bar</b> and include the following snippet in your email:
                    </p>
                    
                    <div class='box'>
                        ${HTML.escape(ticket)}
                    </div>
                    
                    <h2>2. Existing user</h2>
                    <p>
                        If you have completed this step before for <b>${HTML.escape(config.core.providerId)}</b> then
                        simply connect to the system using SSH and run the following command:
                    </p>
                    
                    <div class='box'>
                        ucloud connect approve ${HTML.escape(ticket)}
                    </div>
                </body>
                </html>
            """.trimIndent()
        )
    }
}
